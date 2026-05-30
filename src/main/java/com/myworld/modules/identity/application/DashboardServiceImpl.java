// backend/identity/application/DashboardServiceImpl.java
// FIX: Added emailVerified to UserDashboardDTO builder
package com.myworld.modules.identity.application;

import com.myworld.core.config.CacheConfig;
import com.myworld.core.exception.ResourceNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import com.myworld.modules.campaign.domain.LeadStatus;
import com.myworld.modules.campaign.infrastructure.LeadRepository;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.identity.web.UserDashboardDTO;
import com.myworld.modules.referral.domain.ReferralStatus;
import com.myworld.modules.referral.infrastructure.ReferralRepository;
import com.myworld.modules.rewards.application.RewardService;
import com.myworld.modules.rewards.domain.UserReward;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final UserRepository userRepo;
    private final RewardService rewardService;
    private final ReferralRepository referralRepo;
    private final LeadRepository leadRepo;

    @Override
    // FIX: Cache user dashboard — called on every app open. 2-min TTL keeps it fresh enough.
    // Cache key is the user's email so different users never see each other's data.
    @Cacheable(value = CacheConfig.CACHE_USER_DASHBOARD, key = "#email")
    @Transactional(readOnly = true)
    public UserDashboardDTO getDashboardData(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserReward reward = rewardService.getBalance(user.getId());
        // Available credits = total earned - confirmed redeems - pending redeems
        long availableCredits = reward.getTotalCredits()
                - reward.getRedeemedCredits()
                - reward.getPendingCredits();

        long totalReferrals = referralRepo.countByReferrerIdAndStatus(user.getId(), ReferralStatus.PENDING)
                            + referralRepo.countByReferrerIdAndStatus(user.getId(), ReferralStatus.SUCCESS);
        long successfulReferrals = referralRepo.countByReferrerIdAndStatus(user.getId(), ReferralStatus.SUCCESS);

        long pendingLeads  = leadRepo.countByUserIdAndStatus(user.getId(), LeadStatus.PENDING);
        long rewardedLeads = leadRepo.countByUserIdAndStatus(user.getId(), LeadStatus.REWARDED);

        return UserDashboardDTO.builder()
                .name(user.getName())
                .email(user.getEmail())
                .referralCode(user.getReferralCode())
                .totalCredits(availableCredits)
                .totalReferrals(totalReferrals)
                .successfulReferrals(successfulReferrals)
                .pendingLeads(pendingLeads)
                .rewardedLeads(rewardedLeads)
                // FIX: send emailVerified so Android can show/hide verify banner
                .emailVerified(Boolean.TRUE.equals(user.getIsEmailVerified()))
                .build();
    }
}