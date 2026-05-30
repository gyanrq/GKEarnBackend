package com.myworld.core.config;

import com.myworld.core.constant.Role;
import com.myworld.modules.campaign.domain.Campaign;
import com.myworld.modules.campaign.domain.CampaignType;
import com.myworld.modules.campaign.domain.Lead;
import com.myworld.modules.campaign.domain.LeadStatus;
import com.myworld.modules.campaign.infrastructure.CampaignRepository;
import com.myworld.modules.campaign.infrastructure.LeadRepository;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.referral.domain.Referral;
import com.myworld.modules.referral.domain.ReferralStatus;
import com.myworld.modules.referral.infrastructure.ReferralRepository;
import com.myworld.modules.rewards.domain.RewardConfig;
import com.myworld.modules.rewards.infrastructure.RewardConfigRepository;
import com.myworld.modules.spin.domain.SpinPrize;
import com.myworld.modules.spin.infrastructure.SpinPrizeRepository;
import com.myworld.modules.tasks.domain.TaskConfig;
import com.myworld.modules.tasks.domain.TaskType;
import com.myworld.modules.tasks.infrastructure.TaskConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Profile("dev")
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CampaignRepository campaignRepo;
    private final LeadRepository leadRepo;
    private final ReferralRepository referralRepo;
    private final RewardConfigRepository rewardConfigRepository;
    private final TaskConfigRepository taskConfigRepository;
    private final SpinPrizeRepository spinPrizeRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("🚀 Starting Data Seeding...");

        // 1. Core accounts
        User admin     = createAccountIfMissing("admin@earnx.com",     "Super Admin",      "8888888888", Role.ADMIN);
        User moderator = createAccountIfMissing("moderator@earnx.com", "System Moderator", "7777777777", Role.MODERATOR);
                         createAccountIfMissing("user@earnx.com",      "Test User",        "6666666666", Role.USER);

        // 2. Configs
        seedRewardConfig();
        seedTaskConfigs();
        seedSpinPrizes();

        // 3. Mock campaigns + users + leads + referrals (only once)
        if (campaignRepo.count() == 0) {
            seedMockData(admin);
        } else {
            log.info("Mock campaign data already exists. Skipping.");
        }

        log.info("🎉 Data Seeding Complete!");
    }

    // ─── Mock Data ────────────────────────────────────────────────────────────

    private void seedMockData(User admin) {
        Faker faker = new Faker(new Locale("en-IN"));
        String securePassword = passwordEncoder.encode("Test@123");

        // Campaigns
        List<Campaign> campaigns = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            campaigns.add(campaignRepo.save(Campaign.builder()
                    .name("Test Campaign " + i)
                    .description("Sample campaign")
                    .campaignType(CampaignType.BRAND)
                    .rewardAmount(BigDecimal.valueOf(100))
                    .trackingLink("https://track.earnx.com/" + i)
                    .isActive(true)
                    .build()));
        }

        // Fake users with duplicate-safe loop
        List<User> fakeUsers = new ArrayList<>();
        int created = 0, attempts = 0;

        while (created < 9 && attempts < 100) {
            attempts++;
            String phone        = String.valueOf(faker.number().numberBetween(6000000000L, 9999999999L));
            String email        = faker.internet().emailAddress();
            String referralCode = "REF" + faker.number().digits(6);

            if (userRepository.existsByPhone(phone)
                    || userRepository.existsByEmail(email)
                    || userRepository.existsByReferralCode(referralCode)) {
                continue;
            }

            fakeUsers.add(userRepository.save(User.builder()
                    .name(faker.name().fullName())
                    .email(email)
                    .phone(phone)
                    .password(securePassword)
                    .role(Role.USER)
                    .isEmailVerified(true)
                    .isPhoneVerified(true)
                    .referralCode(referralCode)
                    .build()));
            created++;
        }

        // Referrals + Leads
        for (User user : fakeUsers) {
            referralRepo.save(Referral.builder()
                    .referrer(admin)
                    .referred(user)
                    .status(ReferralStatus.SUCCESS)
                    .notes("Initial test referral")
                    .build());

            leadRepo.save(Lead.builder()
                    .user(user)
                    .campaign(campaigns.get(0))
                    .status(LeadStatus.PENDING)
                    .registeredEmail(user.getEmail())
                    .registeredMobile(user.getPhone())
                    .adminNotes("Test lead")
                    .build());
        }

        log.info("✅ Mock data seeded: {} campaigns, {} users, {} leads", campaigns.size(), fakeUsers.size(), fakeUsers.size());
    }

    // ─── Core Accounts ────────────────────────────────────────────────────────

    private User createAccountIfMissing(String email, String name, String phone, Role role) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            log.info("Creating {} account: {}", role, email);
            User user = userRepository.save(User.builder()
                    .name(name)
                    .email(email)
                    .phone(phone)
                    .password(passwordEncoder.encode("Password@123"))
                    .role(role)
                    .isEmailVerified(true)
                    .isPhoneVerified(true)
                    .referralCode(name.toUpperCase().replace(" ", "_") + "_REF")
                    .isBlocked(false)
                    .isDeleted(false)
                    .build());
            log.info("✅ {} created.", name);
            return user;
        });
    }

    // ─── Reward Config ────────────────────────────────────────────────────────

    private void seedRewardConfig() {
        rewardConfigRepository.findFirstByIsActiveTrue().ifPresentOrElse(config -> {
            boolean changed = false;
            if (config.getMaxDailyEarn() == null || config.getMaxDailyEarn() < 4000L) {
                config.setMaxDailyEarn(4000L); changed = true;
            }
            if (config.getRedemptionWaitSeconds() == null) {
                config.setRedemptionWaitSeconds(0L); changed = true;
            }
            if (config.getPayoutProcessingSeconds() == null) {
                config.setPayoutProcessingSeconds(604800L); changed = true;
            }
            if (changed) {
                rewardConfigRepository.save(config);
                log.info("✅ RewardConfig updated.");
            } else {
                log.info("RewardConfig already up-to-date.");
            }
        }, () -> {
            rewardConfigRepository.save(RewardConfig.builder()
                    .creditsPerRupee(10)
                    .minRedeemCredits(1000L)
                    .maxDailyEarn(4000L)
                    .redemptionWaitSeconds(0L)
                    .payoutProcessingSeconds(604800L)
                    .isActive(true)
                    .build());
            log.info("✅ RewardConfig created.");
        });
    }

    // ─── Task Configs ─────────────────────────────────────────────────────────

    private void seedTaskConfigs() {
        record Seed(TaskType type, long credits, String label) {}
        List<Seed> seeds = List.of(
            new Seed(TaskType.LOGIN,          10L, "Daily Login"),
            new Seed(TaskType.LEAD_SUBMIT,    50L, "Submit a Lead"),
            new Seed(TaskType.SHARE_REFERRAL, 20L, "Share Referral Link"),
            new Seed(TaskType.SPIN_WHEEL,      5L, "Spin the Wheel")
        );
        for (Seed s : seeds) {
            if (taskConfigRepository.findByTaskType(s.type()).isEmpty()) {
                taskConfigRepository.save(TaskConfig.builder()
                        .taskType(s.type()).credits(s.credits())
                        .label(s.label()).isActive(true).build());
                log.info("✅ TaskConfig seeded: {} = {} credits", s.type(), s.credits());
            }
        }
    }

    // ─── Spin Prizes ──────────────────────────────────────────────────────────

    private void seedSpinPrizes() {
        if (spinPrizeRepository.count() > 0) {
            log.info("SpinPrizes already seeded. Skipping.");
            return;
        }
        record Seed(long credits, int weight, String label, String color, int order) {}
        List<Seed> seeds = List.of(
            new Seed(10,  30, "10 Credits",  "#10b981", 0),
            new Seed(20,  25, "20 Credits",  "#3b82f6", 1),
            new Seed(50,  20, "50 Credits",  "#f59e0b", 2),
            new Seed(100, 15, "100 Credits", "#ef4444", 3),
            new Seed(200,  8, "200 Credits", "#8b5cf6", 4),
            new Seed(500,  2, "500 Credits", "#ec4899", 5)
        );
        for (Seed s : seeds) {
            spinPrizeRepository.save(SpinPrize.builder()
                    .credits(s.credits()).weight(s.weight()).label(s.label())
                    .color(s.color()).sortOrder(s.order()).isActive(true).build());
            log.info("✅ SpinPrize seeded: {} credits", s.credits());
        }
    }
}