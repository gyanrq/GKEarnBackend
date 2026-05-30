package com.myworld.modules.tasks.application;

import com.myworld.core.exception.BadRequestException;
import com.myworld.core.exception.ResourceNotFoundException;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.rewards.application.RewardService;
import com.myworld.modules.rewards.domain.RewardSource;
import com.myworld.modules.tasks.domain.*;
import com.myworld.modules.tasks.dto.TaskDTO;
import com.myworld.modules.tasks.dto.TaskLeadSubmitDTO;
import com.myworld.modules.tasks.infrastructure.DailyTaskRepository;
import com.myworld.modules.tasks.infrastructure.TaskConfigRepository;
import com.myworld.modules.tasks.infrastructure.TaskLeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyTaskService {

    private final DailyTaskRepository   taskRepo;
    private final TaskConfigRepository  configRepo;
    private final TaskLeadRepository    leadRepo;
    private final RewardService         rewardService;
    private final UserRepository        userRepo;

    // FIX: was hardcoded ZoneOffset.UTC — daily task reset was at 05:30 IST not midnight.
    // Now reads from same config property as SpinService and RewardServiceImpl.
    @Value("${app.timezone:+05:30}")
    private String timezoneOffset;

    // ── Fallback credits if DB config missing ─────────────────────────────────
    private static final Map<TaskType, Long> FALLBACK_CREDITS;
    static {
        Map<TaskType, Long> m = new EnumMap<>(TaskType.class);
        m.put(TaskType.LOGIN,            10L);
        m.put(TaskType.SPIN_WHEEL,        5L);
        m.put(TaskType.SHARE_REFERRAL,   20L);
        m.put(TaskType.LEAD_SUBMIT,      50L);
        // Crypto
        m.put(TaskType.LINK_BINANCE,    200L);
        m.put(TaskType.LINK_BYBIT,      150L);
        m.put(TaskType.LINK_BITGET,     150L);
        m.put(TaskType.LINK_BITMART,    100L);
        m.put(TaskType.LINK_COINSWITCH, 100L);
        m.put(TaskType.LINK_WAZIRX,      80L);
        m.put(TaskType.LINK_COINDCX,     80L);
        // Demat
        m.put(TaskType.LINK_SBI_SECURITIES, 300L);
        m.put(TaskType.LINK_HDFC_SKY,       300L);
        m.put(TaskType.LINK_ZERODHA,        250L);
        m.put(TaskType.LINK_GROWW,          200L);
        m.put(TaskType.LINK_ANGELONE,       200L);
        m.put(TaskType.LINK_UPSTOX,         180L);
        m.put(TaskType.LINK_IIFL,           150L);
        // App / Earning
        m.put(TaskType.LINK_MEESHO,      80L);
        m.put(TaskType.LINK_CASHKARO,    60L);
        m.put(TaskType.LINK_TASKBUCKS,   50L);
        m.put(TaskType.LINK_ROZDHAN,     50L);
        m.put(TaskType.LINK_MILESTONEIT, 70L);
        // KYC
        m.put(TaskType.KYC_VERIFY,      500L);
        m.put(TaskType.SURVEY_COMPLETE, 100L);
        m.put(TaskType.PROFILE_COMPLETE, 50L);
        FALLBACK_CREDITS = Collections.unmodifiableMap(m);
    }

    // ── Fallback labels ───────────────────────────────────────────────────────
    private static final Map<TaskType, String> FALLBACK_LABELS = Map.ofEntries(
        Map.entry(TaskType.LOGIN,               "Daily Login"),
        Map.entry(TaskType.SPIN_WHEEL,          "Spin the Wheel"),
        Map.entry(TaskType.SHARE_REFERRAL,      "Share Referral Code"),
        Map.entry(TaskType.LEAD_SUBMIT,         "Submit a Lead"),
        Map.entry(TaskType.LINK_BINANCE,        "Open Binance Account"),
        Map.entry(TaskType.LINK_BYBIT,          "Open Bybit Account"),
        Map.entry(TaskType.LINK_BITGET,         "Open Bitget Account"),
        Map.entry(TaskType.LINK_BITMART,        "Open BitMart Account"),
        Map.entry(TaskType.LINK_COINSWITCH,     "Join CoinSwitch"),
        Map.entry(TaskType.LINK_WAZIRX,         "Join WazirX"),
        Map.entry(TaskType.LINK_COINDCX,        "Join CoinDCX"),
        Map.entry(TaskType.LINK_SBI_SECURITIES, "Open SBI Securities Demat"),
        Map.entry(TaskType.LINK_HDFC_SKY,       "Open HDFC Sky Demat"),
        Map.entry(TaskType.LINK_ZERODHA,        "Open Zerodha Account"),
        Map.entry(TaskType.LINK_GROWW,          "Open Groww Account"),
        Map.entry(TaskType.LINK_ANGELONE,       "Open AngelOne Account"),
        Map.entry(TaskType.LINK_UPSTOX,         "Open Upstox Account"),
        Map.entry(TaskType.LINK_IIFL,           "Open IIFL Account"),
        Map.entry(TaskType.LINK_MEESHO,         "Install Meesho & Earn"),
        Map.entry(TaskType.LINK_CASHKARO,       "Join CashKaro"),
        Map.entry(TaskType.LINK_TASKBUCKS,      "Install TaskBucks"),
        Map.entry(TaskType.LINK_ROZDHAN,        "Install RozDhan"),
        Map.entry(TaskType.LINK_MILESTONEIT,    "Install MilestoneIT"),
        Map.entry(TaskType.KYC_VERIFY,          "Complete KYC Verification"),
        Map.entry(TaskType.SURVEY_COMPLETE,     "Complete Daily Survey"),
        Map.entry(TaskType.PROFILE_COMPLETE,    "Complete Your Profile")
    );

    private static final Map<TaskType, String> FALLBACK_CATEGORY = Map.ofEntries(
        Map.entry(TaskType.LOGIN,               "DAILY"),
        Map.entry(TaskType.SPIN_WHEEL,          "DAILY"),
        Map.entry(TaskType.SHARE_REFERRAL,      "DAILY"),
        Map.entry(TaskType.LEAD_SUBMIT,         "DAILY"),
        Map.entry(TaskType.LINK_BINANCE,        "CRYPTO"),
        Map.entry(TaskType.LINK_BYBIT,          "CRYPTO"),
        Map.entry(TaskType.LINK_BITGET,         "CRYPTO"),
        Map.entry(TaskType.LINK_BITMART,        "CRYPTO"),
        Map.entry(TaskType.LINK_COINSWITCH,     "CRYPTO"),
        Map.entry(TaskType.LINK_WAZIRX,         "CRYPTO"),
        Map.entry(TaskType.LINK_COINDCX,        "CRYPTO"),
        Map.entry(TaskType.LINK_SBI_SECURITIES, "DEMAT"),
        Map.entry(TaskType.LINK_HDFC_SKY,       "DEMAT"),
        Map.entry(TaskType.LINK_ZERODHA,        "DEMAT"),
        Map.entry(TaskType.LINK_GROWW,          "DEMAT"),
        Map.entry(TaskType.LINK_ANGELONE,       "DEMAT"),
        Map.entry(TaskType.LINK_UPSTOX,         "DEMAT"),
        Map.entry(TaskType.LINK_IIFL,           "DEMAT"),
        Map.entry(TaskType.LINK_MEESHO,         "APP"),
        Map.entry(TaskType.LINK_CASHKARO,       "APP"),
        Map.entry(TaskType.LINK_TASKBUCKS,      "APP"),
        Map.entry(TaskType.LINK_ROZDHAN,        "APP"),
        Map.entry(TaskType.LINK_MILESTONEIT,    "APP"),
        Map.entry(TaskType.KYC_VERIFY,          "KYC"),
        Map.entry(TaskType.SURVEY_COMPLETE,     "KYC"),
        Map.entry(TaskType.PROFILE_COMPLETE,    "KYC")
    );

    private static final Map<TaskType, String> FALLBACK_ICON = Map.ofEntries(
        Map.entry(TaskType.LOGIN,               "🌅"),
        Map.entry(TaskType.SPIN_WHEEL,          "🎡"),
        Map.entry(TaskType.SHARE_REFERRAL,      "🔗"),
        Map.entry(TaskType.LEAD_SUBMIT,         "📝"),
        Map.entry(TaskType.LINK_BINANCE,        "₿"),
        Map.entry(TaskType.LINK_BYBIT,          "🔶"),
        Map.entry(TaskType.LINK_BITGET,         "💎"),
        Map.entry(TaskType.LINK_BITMART,        "🌐"),
        Map.entry(TaskType.LINK_COINSWITCH,     "🪙"),
        Map.entry(TaskType.LINK_WAZIRX,         "⚡"),
        Map.entry(TaskType.LINK_COINDCX,        "📈"),
        Map.entry(TaskType.LINK_SBI_SECURITIES, "🏦"),
        Map.entry(TaskType.LINK_HDFC_SKY,       "🔵"),
        Map.entry(TaskType.LINK_ZERODHA,        "📊"),
        Map.entry(TaskType.LINK_GROWW,          "🌱"),
        Map.entry(TaskType.LINK_ANGELONE,       "😇"),
        Map.entry(TaskType.LINK_UPSTOX,         "🚀"),
        Map.entry(TaskType.LINK_IIFL,           "💼"),
        Map.entry(TaskType.LINK_MEESHO,         "🛍️"),
        Map.entry(TaskType.LINK_CASHKARO,       "💰"),
        Map.entry(TaskType.LINK_TASKBUCKS,      "✅"),
        Map.entry(TaskType.LINK_ROZDHAN,        "🤑"),
        Map.entry(TaskType.LINK_MILESTONEIT,    "🏆"),
        Map.entry(TaskType.KYC_VERIFY,          "🪪"),
        Map.entry(TaskType.SURVEY_COMPLETE,     "📋"),
        Map.entry(TaskType.PROFILE_COMPLETE,    "👤")
    );

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the full task list for today, with completion and lead status
     * pre-populated for the given user.
     */
    @Transactional(readOnly = true)
    public List<TaskDTO> getTodayTasks(Long userId) {
        OffsetDateTime todayIST = todayStartIST();

        // Which tasks did this user complete today?
        Set<String> completedTypes = taskRepo.findTodayCompletions(userId, todayIST)
                .stream().map(t -> t.getTaskType().name()).collect(Collectors.toSet());

        // Which tasks has the user ever submitted a lead for?
        Set<String> leadSubmittedTypes = leadRepo.findAll().stream()
                .filter(l -> l.getUser().getId().equals(userId))
                .map(l -> l.getTaskType().name())
                .collect(Collectors.toSet());

        // Build a map of DB configs
        Map<String, TaskConfig> configMap = configRepo.findAll().stream()
                .collect(Collectors.toMap(c -> c.getTaskType().name(), c -> c));

        // Build DTO list — one entry per TaskType value
        List<TaskDTO> result = new ArrayList<>();
        for (TaskType type : TaskType.values()) {
            TaskConfig cfg = configMap.get(type.name());

            // Survey rewards need a real survey provider/API. Keep the enum for
            // compatibility, but hide it unless an active DB config enables it.
            if (type == TaskType.SURVEY_COMPLETE && cfg == null) continue;

            // Skip explicitly disabled tasks
            if (cfg != null && Boolean.FALSE.equals(cfg.getIsActive())) continue;

            boolean completed     = completedTypes.contains(type.name());
            boolean leadSubmitted = leadSubmittedTypes.contains(type.name());

            // Resolve partnerUrl only when lead already submitted
            String partnerUrl = null;
            if (leadSubmitted) {
                partnerUrl = cfg != null ? cfg.getPartnerUrl() : defaultPartnerUrl(type);
            }

            result.add(TaskDTO.builder()
                    .taskType(type.name())
                    .label(cfg != null && cfg.getLabel() != null ? cfg.getLabel() : FALLBACK_LABELS.getOrDefault(type, type.name()))
                    .description(cfg != null ? cfg.getDescription() : defaultDescription(type))
                    .credits(resolveCredits(type, cfg))
                    .category(cfg != null && cfg.getCategory() != null ? cfg.getCategory() : FALLBACK_CATEGORY.getOrDefault(type, "DAILY"))
                    .icon(cfg != null && cfg.getIcon() != null ? cfg.getIcon() : FALLBACK_ICON.getOrDefault(type, "⚡"))
                    .completed(completed)
                    .requiresLead(cfg != null ? Boolean.TRUE.equals(cfg.getRequiresLead()) : isLinkTask(type))
                    .partnerUrl(partnerUrl)
                    .leadSubmitted(leadSubmitted)
                    .build());
        }

        // Sort: daily first, then by category, then by credits desc
        result.sort(Comparator
                .comparingInt((TaskDTO t) -> categoryOrder(t.getCategory()))
                .thenComparingLong(t -> -t.getCredits()));

        return result;
    }

    /**
     * Captures user's email + phone for a link task and returns the
     * partner URL.  Credits are NOT awarded here — they are awarded when
     * the user comes back and calls completeTask().
     */
    @Transactional
    public String submitLead(Long userId, TaskLeadSubmitDTO dto) {
        TaskType taskType = parseType(dto.getTaskType());
        if (!isLinkTask(taskType)) {
            throw new BadRequestException("Lead submission is only available for partner link tasks.");
        }

        // Idempotent — if already submitted, just return the URL again
        Optional<TaskLead> existing = leadRepo.findByUserIdAndTaskType(userId, taskType);
        if (existing.isPresent()) {
            return existing.get().getPartnerUrl();
        }

        TaskConfig cfg = configRepo.findByTaskType(taskType).orElse(null);
        String url = cfg != null && cfg.getPartnerUrl() != null
                ? cfg.getPartnerUrl()
                : defaultPartnerUrl(taskType);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        leadRepo.save(TaskLead.builder()
                .user(user)
                .taskType(taskType)
                .email(dto.getEmail())
                .mobile(dto.getMobile())
                .partnerUrl(url)
                .build());

        log.info("Task lead saved: userId={} taskType={} email={}", userId, taskType, dto.getEmail());
        return url;
    }

    /**
     * Marks a task as complete and awards credits.
     * Returns true if completed now, false if already done today.
     */
    @Transactional
    public boolean completeTask(Long userId, TaskType taskType) {
        TaskConfig cfg = configRepo.findByTaskType(taskType).orElse(null);

        if (taskType == TaskType.SURVEY_COMPLETE && cfg == null) {
            throw new BadRequestException("Survey tasks are not available right now.");
        }

        if (cfg != null && Boolean.FALSE.equals(cfg.getIsActive())) {
            throw new BadRequestException("This task is currently disabled.");
        }

        // For lead tasks: ensure the lead was submitted first
        if (isLinkTask(taskType) && !leadRepo.existsByUserIdAndTaskType(userId, taskType)) {
            throw new BadRequestException("Please submit your details first to unlock this task.");
        }

        OffsetDateTime todayIST = todayStartIST();
        if (taskRepo.existsByUserIdAndTaskTypeAndCreatedAtAfter(userId, taskType, todayIST)) {
            log.info("Task already completed: userId={} task={}", userId, taskType);
            return false;
        }

        long credits = resolveCredits(taskType, cfg);
        if (credits <= 0) return false;

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        try {
            // FIX: DataIntegrityViolationException is the database-level guard against
            // race conditions. Two concurrent requests can both pass the
            // existsByUserIdAndTaskTypeAndCreatedAtAfter check before either saves.
            // The unique index on (user_id, task_type, DATE(created_at)) — added in
            // V3 migration — catches the second insert and throws this exception,
            // which we map to a clean "already completed" result instead of a 500.
            taskRepo.save(DailyTaskCompletion.builder()
                    .user(user)
                    .taskType(taskType)
                    .creditsEarned(credits)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            log.warn("Race condition on task completion: userId={} task={} — treated as duplicate",
                    userId, taskType);
            return false;
        }

        rewardService.earnCredits(userId, credits,
                "Task: " + FALLBACK_LABELS.getOrDefault(taskType, taskType.name()),
                RewardSource.DAILY_TASK);

        log.info("Task completed: userId={} task={} credits={}", userId, taskType, credits);
        return true;
    }

    // legacy helper used by admin
    @Transactional(readOnly = true)
    public Map<String, Long> getTaskCreditMap() {
        Map<String, Long> map = Arrays.stream(TaskType.values())
                .collect(Collectors.toMap(TaskType::name, t -> FALLBACK_CREDITS.getOrDefault(t, 0L)));
        configRepo.findAll().forEach(c -> map.put(c.getTaskType().name(), c.getCredits()));
        return map;
    }

    @Transactional(readOnly = true)
    public List<TaskConfig> getAllTaskConfigs() {
        return configRepo.findAllByOrderByTaskTypeAsc();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private OffsetDateTime todayStartIST() {
        // FIX: was ZoneOffset.ofHoursMinutes(5, 30) hardcoded — now uses app.timezone config.
        // This matches SpinService and RewardServiceImpl which already use this pattern.
        ZoneOffset zone = ZoneOffset.of(timezoneOffset);
        return OffsetDateTime.now(zone).toLocalDate().atStartOfDay().atOffset(zone);
    }

    private long resolveCredits(TaskType type, TaskConfig cfg) {
        if (cfg != null && cfg.getCredits() != null && cfg.getCredits() > 0) return cfg.getCredits();
        return FALLBACK_CREDITS.getOrDefault(type, 0L);
    }

    private boolean isLinkTask(TaskType type) {
        return type.name().startsWith("LINK_");
    }

    private TaskType parseType(String s) {
        try {
            return TaskType.valueOf(s.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid taskType: " + s);
        }
    }

    private int categoryOrder(String cat) {
        return switch (cat) {
            case "DAILY"  -> 0;
            case "KYC"    -> 1;
            case "CRYPTO" -> 2;
            case "DEMAT"  -> 3;
            case "APP"    -> 4;
            default       -> 5;
        };
    }

    private String defaultPartnerUrl(TaskType type) {
        return switch (type) {
            case LINK_BINANCE        -> "https://accounts.binance.com/register";
            case LINK_BYBIT          -> "https://www.bybit.com/en/register";
            case LINK_BITGET         -> "https://www.bitget.com/register";
            case LINK_BITMART        -> "https://www.bitmart.com/register";
            case LINK_COINSWITCH     -> "https://coinswitch.co/in/signup";
            case LINK_WAZIRX         -> "https://wazirx.com/signup";
            case LINK_COINDCX        -> "https://coindcx.com/signup";
            case LINK_SBI_SECURITIES -> "https://www.sbisecurities.in/open-account";
            case LINK_HDFC_SKY       -> "https://hdfcsky.com/open-account";
            case LINK_ZERODHA        -> "https://zerodha.com/open-account";
            case LINK_GROWW          -> "https://groww.in/open-demat-account";
            case LINK_ANGELONE       -> "https://angelone.in/open-demat-account";
            case LINK_UPSTOX         -> "https://upstox.com/open-account";
            case LINK_IIFL           -> "https://www.iiflsecurities.com/open-account";
            case LINK_MEESHO         -> "https://meesho.com/install";
            case LINK_CASHKARO       -> "https://cashkaro.com/register";
            case LINK_TASKBUCKS      -> "https://play.google.com/store/apps/details?id=com.taskbucks";
            case LINK_ROZDHAN        -> "https://play.google.com/store/apps/details?id=com.rozdhan";
            case LINK_MILESTONEIT    -> "https://milestoneit.app/install";
            default                  -> "https://earnx3.com";
        };
    }

    private String defaultDescription(TaskType type) {
        return switch (type) {
            case LOGIN               -> "Log in daily to earn bonus credits!";
            case SPIN_WHEEL          -> "Spin the lucky wheel once every day.";
            case SHARE_REFERRAL      -> "Share your referral code with a friend.";
            case LEAD_SUBMIT         -> "Submit a lead for an active campaign.";
            case LINK_BINANCE        -> "Create a free Binance account & start trading crypto.";
            case LINK_BYBIT          -> "Open a Bybit account — one of the fastest exchanges.";
            case LINK_BITGET         -> "Register on Bitget & explore copy trading.";
            case LINK_BITMART        -> "Join BitMart & get access to 1000+ tokens.";
            case LINK_COINSWITCH     -> "India's easiest crypto app — join CoinSwitch.";
            case LINK_WAZIRX         -> "Buy & sell crypto instantly on WazirX.";
            case LINK_COINDCX        -> "Start your crypto journey with CoinDCX.";
            case LINK_SBI_SECURITIES -> "Open a free SBI Securities demat account.";
            case LINK_HDFC_SKY       -> "Open HDFC Sky demat & invest in stocks.";
            case LINK_ZERODHA        -> "India's #1 broker — open your Zerodha account.";
            case LINK_GROWW          -> "Open a Groww demat & start investing today.";
            case LINK_ANGELONE       -> "Join AngelOne for stocks, F&O & mutual funds.";
            case LINK_UPSTOX         -> "Open Upstox — India's fastest trading platform.";
            case LINK_IIFL           -> "Open IIFL Securities account & invest smartly.";
            case LINK_MEESHO         -> "Install Meesho & earn by reselling products.";
            case LINK_CASHKARO       -> "Join CashKaro & earn cashback on shopping.";
            case LINK_TASKBUCKS      -> "Install TaskBucks & complete tasks for cash.";
            case LINK_ROZDHAN        -> "Install RozDhan & earn by reading news.";
            case LINK_MILESTONEIT    -> "Install MilestoneIT & earn on every task.";
            case KYC_VERIFY          -> "Verify your identity to unlock higher withdrawals.";
            case SURVEY_COMPLETE     -> "Take a 2-minute survey and earn credits.";
            case PROFILE_COMPLETE    -> "Fill your profile to unlock all features.";
        };
    }
}
