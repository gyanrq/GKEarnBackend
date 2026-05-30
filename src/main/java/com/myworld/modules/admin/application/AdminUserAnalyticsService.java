package com.myworld.modules.admin.application;

import com.myworld.modules.admin.api.UserAnalyticsDTO;
import com.myworld.modules.admin.api.UserAnalyticsDTO.*;
import com.myworld.modules.identity.domain.DeviceFingerprint;
import com.myworld.modules.identity.domain.LoginAttempt;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.DeviceFingerprintRepository;
import com.myworld.modules.identity.infrastructure.LoginAttemptRepository;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.referral.domain.Referral;
import com.myworld.modules.referral.domain.ReferralStatus;
import com.myworld.modules.referral.infrastructure.ReferralRepository;
import com.myworld.modules.rewards.domain.RewardTransaction;
import com.myworld.modules.rewards.domain.RewardTxType;
import com.myworld.modules.rewards.infrastructure.RewardTransactionRepository;
import com.myworld.modules.rewards.infrastructure.UserRewardRepository;
import com.myworld.modules.tasks.domain.DailyTaskCompletion;
import com.myworld.modules.tasks.infrastructure.DailyTaskRepository;
import com.myworld.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserAnalyticsService {

    private static final int RECENT_TASKS_LIMIT   = 30;
    private static final int MAX_TIMELINE_EVENTS  = 50;

    private final UserRepository              userRepo;
    private final RewardTransactionRepository rewardTxRepo;
    private final UserRewardRepository        userRewardRepo;
    private final DailyTaskRepository         taskRepo;
    private final ReferralRepository          referralRepo;
    private final DeviceFingerprintRepository deviceRepo;
    private final LoginAttemptRepository      loginRepo;

    @Transactional(readOnly = true)
    public UserAnalyticsDTO getUserDeepAnalytics(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // ── Credit summary ────────────────────────────────────────────────────
        var reward        = userRewardRepo.findByUserId(userId).orElse(null);
        long totalEarned   = reward != null ? reward.getTotalCredits()    : 0L;
        long totalRedeemed = reward != null ? reward.getRedeemedCredits() : 0L;
        long netBalance    = totalEarned - totalRedeemed;
        double redeemRate  = totalEarned > 0
                ? Math.round((double) totalRedeemed / totalEarned * 10_000.0) / 100.0
                : 0.0;

        // ── All transactions for this user ────────────────────────────────────
        var txPage = rewardTxRepo.findByUser_Id(userId,
                PageRequest.of(0, 1000, Sort.by("createdAt").descending()));
        List<RewardTransaction> allTx = txPage.getContent();
        long totalTx = txPage.getTotalElements();

        // Earnings by source
        Map<String, long[]> srcMap = new LinkedHashMap<>();  // [credits, count]
        for (RewardTransaction tx : allTx) {
            if (tx.getType() == RewardTxType.EARN) {
                String src = tx.getSource().name();
                srcMap.computeIfAbsent(src, k -> new long[]{0L, 0L});
                srcMap.get(src)[0] += tx.getCredits();
                srcMap.get(src)[1]++;
            }
        }
        List<SourceBreakdownItem> earningsBySource = srcMap.entrySet().stream()
                .map(e -> SourceBreakdownItem.builder()
                        .source(e.getKey())
                        .credits(e.getValue()[0])
                        .count(e.getValue()[1])
                        .percentage(totalEarned > 0
                                ? Math.round((double) e.getValue()[0] / totalEarned * 10_000.0) / 100.0
                                : 0.0)
                        .build())
                .sorted(Comparator.comparingLong(SourceBreakdownItem::getCredits).reversed())
                .collect(Collectors.toList());

        // ── Tasks ─────────────────────────────────────────────────────────────
        List<DailyTaskCompletion> tasks = taskRepo.findTodayCompletions(
                userId, OffsetDateTime.now().minusDays(365));
        long totalTasksCompleted = tasks.size();
        List<TaskHistoryItem> recentTasks = tasks.stream()
                .sorted(Comparator.comparing(DailyTaskCompletion::getCreatedAt).reversed())
                .limit(RECENT_TASKS_LIMIT)
                .map(t -> TaskHistoryItem.builder()
                        .taskType(t.getTaskType().name())
                        .creditsEarned(t.getCreditsEarned())
                        .completedAt(t.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        // ── Referrals ─────────────────────────────────────────────────────────
        List<Referral> referralsMade = referralRepo.findByReferrerId(userId);
        long successfulReferrals = referralRepo.countByReferrerIdAndStatus(userId, ReferralStatus.SUCCESS);

        // ── Devices ───────────────────────────────────────────────────────────
        List<DeviceFingerprint> devices = deviceRepo.findAll().stream()
                .filter(d -> d.getUser() != null && d.getUser().getId().equals(userId))
                .collect(Collectors.toList());
        long uniqueDevices = devices.stream()
                .map(DeviceFingerprint::getDeviceId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        List<DeviceItem> deviceHistory = devices.stream()
                .sorted(Comparator.comparing(DeviceFingerprint::getLastSeenAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(d -> DeviceItem.builder()
                        .deviceId(d.getDeviceId())
                        .userAgent(d.getUserAgent())
                        .ipAddress(d.getIpAddress())
                        .lastSeenAt(d.getLastSeenAt())
                        .trusted(d.getTrusted())
                        .build())
                .collect(Collectors.toList());

        // ── Login attempts ────────────────────────────────────────────────────
        List<LoginAttempt> loginAttempts = loginRepo.findAll().stream()
                .filter(la -> la.getIdentifier() != null &&
                        (la.getIdentifier().equals(user.getEmail()) ||
                         la.getIdentifier().equals(user.getPhone())))
                .collect(Collectors.toList());
        long totalLoginAttempts  = loginAttempts.size();
        long failedLoginAttempts = loginAttempts.stream()
                .filter(la -> Boolean.FALSE.equals(la.getSuccess()))
                .count();

        // ── Account age / churn ───────────────────────────────────────────────
        OffsetDateTime joinedAt        = user.getCreatedAt();
        long accountAgeDays            = joinedAt != null
                ? ChronoUnit.DAYS.between(joinedAt, OffsetDateTime.now()) : 0;
        long daysSinceLastLogin        = user.getLastLoginAt() != null
                ? ChronoUnit.DAYS.between(user.getLastLoginAt(), OffsetDateTime.now()) : accountAgeDays;

        // ── Risk / Fraud Score ────────────────────────────────────────────────
        RiskResult risk = computeRisk(user, netBalance, uniqueDevices, failedLoginAttempts,
                totalEarned, totalRedeemed, daysSinceLastLogin);

        // ── Timeline ─────────────────────────────────────────────────────────
        List<TimelineEvent> timeline = buildTimeline(user, allTx, tasks, referralsMade,
                loginAttempts, devices);

        return UserAnalyticsDTO.builder()
                // identity
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .referralCode(user.getReferralCode())
                .isBlocked(user.getIsBlocked())
                .isEmailVerified(user.getIsEmailVerified())
                .isPhoneVerified(user.getIsPhoneVerified())
                .profilePictureUrl(user.getProfilePictureUrl())
                // lifecycle
                .joinedAt(joinedAt)
                .accountAgeDays(accountAgeDays)
                .lastLoginAt(user.getLastLoginAt())
                .lastLoginIp(user.getLastLoginIp())
                .daysSinceLastLogin(daysSinceLastLogin)
                // credits
                .totalEarned(totalEarned)
                .totalRedeemed(totalRedeemed)
                .netBalance(netBalance)
                .redemptionRate(redeemRate)
                // activity
                .totalTransactions(totalTx)
                .earningsBySource(earningsBySource)
                // tasks
                .totalTasksCompleted(totalTasksCompleted)
                .recentTasks(recentTasks)
                // referrals
                .referredByCode(user.getReferredByCode())
                .referralsMade((long) referralsMade.size())
                .referralsSuccessful(successfulReferrals)
                // security
                .uniqueDevices(uniqueDevices)
                .deviceHistory(deviceHistory)
                .totalLoginAttempts(totalLoginAttempts)
                .failedLoginAttempts(failedLoginAttempts)
                // risk
                .riskScore(risk.score)
                .riskLevel(risk.level)
                .riskFlags(risk.flags)
                // timeline
                .timeline(timeline)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Risk scoring engine
    // ─────────────────────────────────────────────────────────────────────────

    private record RiskResult(int score, String level, List<String> flags) {}

    private RiskResult computeRisk(User user, long netBalance, long uniqueDevices,
                                   long failedLogins, long totalEarned, long totalRedeemed,
                                   long daysSinceLastLogin) {
        int score = 0;
        List<String> flags = new ArrayList<>();

        if (netBalance > 10_000) { score += 25; flags.add("High unredeemed balance (>" + netBalance + " cr)"); }
        else if (netBalance > 5_000) { score += 15; flags.add("Elevated balance (>" + netBalance + " cr)"); }

        if (uniqueDevices > 5) { score += 30; flags.add("Unusual device count: " + uniqueDevices + " devices"); }
        else if (uniqueDevices > 3) { score += 15; flags.add("Multiple devices: " + uniqueDevices); }

        if (failedLogins > 10) { score += 20; flags.add("High failed login count: " + failedLogins); }
        else if (failedLogins > 5) { score += 10; flags.add("Multiple failed logins: " + failedLogins); }

        if (Boolean.TRUE.equals(user.getIsBlocked())) { score += 20; flags.add("Account is currently blocked"); }

        double redeemRate = totalEarned > 0 ? (double) totalRedeemed / totalEarned : 0;
        if (totalEarned > 5000 && redeemRate < 0.05) {
            score += 15; flags.add("Very low redemption rate — possible bot accumulation");
        }

        if (daysSinceLastLogin > 30) { flags.add("Inactive for " + daysSinceLastLogin + " days (churn risk)"); }

        String level = score >= 60 ? "CRITICAL" : score >= 40 ? "HIGH" : score >= 20 ? "MEDIUM" : "LOW";
        return new RiskResult(Math.min(score, 100), level, flags);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Timeline builder
    // ─────────────────────────────────────────────────────────────────────────

    private List<TimelineEvent> buildTimeline(
            User user,
            List<RewardTransaction> txList,
            List<DailyTaskCompletion> tasks,
            List<Referral> referrals,
            List<LoginAttempt> logins,
            List<DeviceFingerprint> devices) {

        List<TimelineEvent> events = new ArrayList<>();

        // JOIN
        if (user.getCreatedAt() != null) {
            events.add(TimelineEvent.builder()
                    .occurredAt(user.getCreatedAt())
                    .type("JOIN")
                    .title("Account Created")
                    .detail("User registered with email " + user.getEmail())
                    .severity("INFO")
                    .build());
        }

        // BLOCK
        if (Boolean.TRUE.equals(user.getIsBlocked()) && user.getBlockedAt() != null) {
            events.add(TimelineEvent.builder()
                    .occurredAt(user.getBlockedAt())
                    .type("BLOCK")
                    .title("Account Blocked")
                    .detail("Reason: " + (user.getBlockReason() != null ? user.getBlockReason() : "N/A"))
                    .severity("DANGER")
                    .build());
        }

        // Reward transactions (EARN)
        for (RewardTransaction tx : txList) {
            if (tx.getType() == RewardTxType.EARN && tx.getCreatedAt() != null) {
                events.add(TimelineEvent.builder()
                        .occurredAt(tx.getCreatedAt())
                        .type("EARN")
                        .title("Credits Earned")
                        .detail("+" + tx.getCredits() + " cr via " + tx.getSource().name()
                                + (tx.getDescription() != null ? " — " + tx.getDescription() : ""))
                        .severity("SUCCESS")
                        .build());
            } else if (tx.getType() == RewardTxType.REDEEM && tx.getCreatedAt() != null) {
                events.add(TimelineEvent.builder()
                        .occurredAt(tx.getCreatedAt())
                        .type("REDEEM")
                        .title("Redemption Request")
                        .detail("-" + tx.getCredits() + " cr redeemed")
                        .severity("WARNING")
                        .build());
            }
        }

        // Referrals
        for (Referral r : referrals) {
            if (r.getCreatedAt() != null) {
                events.add(TimelineEvent.builder()
                        .occurredAt(r.getCreatedAt())
                        .type("REFERRAL")
                        .title("Referral Made")
                        .detail("Referred user ID " + r.getReferred().getId()
                                + " — status: " + r.getStatus().name())
                        .severity("INFO")
                        .build());
            }
        }

        // New device seen
        for (DeviceFingerprint d : devices) {
            if (d.getLastSeenAt() != null) {
                events.add(TimelineEvent.builder()
                        .occurredAt(d.getLastSeenAt())
                        .type("DEVICE")
                        .title("Device Activity")
                        .detail("Device " + truncate(d.getDeviceId(), 12)
                                + " from IP " + d.getIpAddress()
                                + (Boolean.TRUE.equals(d.getTrusted()) ? " ✓ trusted" : " ⚠ untrusted"))
                        .severity(Boolean.TRUE.equals(d.getTrusted()) ? "INFO" : "WARNING")
                        .build());
            }
        }

        return events.stream()
                .sorted(Comparator.comparing(TimelineEvent::getOccurredAt).reversed())
                .limit(MAX_TIMELINE_EVENTS)
                .collect(Collectors.toList());
    }

    private String truncate(String s, int max) {
        if (s == null) return "unknown";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}