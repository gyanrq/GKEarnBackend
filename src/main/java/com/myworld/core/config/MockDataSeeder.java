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

@Slf4j
@Profile("dev")
@Component
@RequiredArgsConstructor
public class MockDataSeeder implements CommandLineRunner {

    private final UserRepository userRepo;
    private final CampaignRepository campaignRepo;
    private final LeadRepository leadRepo;
    private final ReferralRepository referralRepo;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (userRepo.count() > 0) {
            log.info("Database already populated. MockDataSeeder skipped.");
            return;
        }

        log.info("🚀 Seeding Production-Safe Mock Data...");
        Faker faker = new Faker(new Locale("en-IN"));
        String securePassword = passwordEncoder.encode("Test@123");

        // 1. Create Admin User
        User admin = User.builder()
                .name("Admin User")
                .email("admin@gkearn.com")
                .phone("9999999999")
                .password(securePassword)
                .role(Role.ADMIN)
                .isEmailVerified(true)
                .isPhoneVerified(true)
                .referralCode("ADMIN001")
                .build();
        userRepo.save(admin);

        // 2. Create Campaigns
        List<Campaign> campaigns = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Campaign cam = Campaign.builder()
                    .name("Test Campaign " + i)
                    .description("Sample campaign")
                    .campaignType(CampaignType.BRAND)
                    .rewardAmount(BigDecimal.valueOf(100))
                    .trackingLink("https://track.gkearn.com/" + i)
                    .isActive(true)
                    .build();
            campaigns.add(campaignRepo.save(cam));
        }

        // 3. Generate 9 Normal Users
        List<User> users = new ArrayList<>();
        users.add(admin);

        for (int i = 0; i < 9; i++) {
            String phone = String.valueOf(faker.number().numberBetween(6000000000L, 9999999999L));
            
            User user = User.builder()
                    .name(faker.name().fullName())
                    .email(faker.internet().emailAddress())
                    .phone(phone)
                    .password(securePassword)
                    .role(Role.USER)
                    .isEmailVerified(true)
                    .isPhoneVerified(true)
                    .referralCode("REF" + faker.number().digits(6))
                    .build();
            users.add(userRepo.save(user));
        }

        // 4. Generate Referrals & Leads
        for (int i = 1; i < users.size(); i++) {
            User user = users.get(i);
            
            Referral referral = Referral.builder()
                    .referrer(admin)
                    .referred(user)
                    .status(ReferralStatus.SUCCESS)
                    .notes("Initial test referral")
                    .build();
            referralRepo.save(referral);

            Lead lead = Lead.builder()
                    .user(user)
                    .campaign(campaigns.get(0))
                    .status(LeadStatus.PENDING)
                    .registeredEmail(user.getEmail())
                    .registeredMobile(user.getPhone())
                    .adminNotes("Test lead")
                    .build();
            leadRepo.save(lead);
        }

        log.info("🎉 Mock Data Seeding Complete!");
    }
}