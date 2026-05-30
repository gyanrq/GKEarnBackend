package com.myworld.modules.referral.application;

import com.myworld.modules.notification.application.NotificationService;
import com.myworld.modules.referral.api.ReferralSuccessEvent;
import com.myworld.modules.referral.domain.*;
import com.myworld.modules.referral.infrastructure.*;
import com.myworld.modules.rewards.application.RewardService;
import com.myworld.modules.rewards.domain.RewardSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MilestoneRewardEngine {

    private final MilestoneRepository milestoneRepo;
    private final MilestoneStatusRepository milestoneStatusRepo;
    private final ReferralRepository referralRepo;
    private final RewardService rewardService;
    private final NotificationService notificationService;

    // Use property instead of hardcoded BigDecimal.TEN
    @Value("${app.reward.conversion-rate:10}")
    private BigDecimal conversionRate;

    @EventListener
    @Transactional
    public void onReferralSuccess(ReferralSuccessEvent event) {
        Long referrerId = event.getReferral().getReferrer().getId();
        long successCount = referralRepo.countByReferrerIdAndStatus(referrerId, ReferralStatus.SUCCESS);

        notificationService.sendNotification(referrerId, "SUCCESS",
            "Referral Successful! 🎉",
            "Congratulations! One of your referrals completed KYC. " +
            "You now have " + successCount + " successful referral(s).");

        List<Milestone> activeMilestones = milestoneRepo.findByIsActiveTrue();
        for (Milestone milestone : activeMilestones) {
            if (milestone.getMilestoneType() != MilestoneType.REFERRAL_COUNT) continue;

            milestoneStatusRepo.findByUserIdAndMilestoneId(referrerId, milestone.getId())
                    .ifPresentOrElse(ms -> {
                        ms.setCurrentProgress((int) successCount);

                        if (successCount >= milestone.getTargetValue()
                                && ms.getState() == MilestoneState.LOCKED) {

                            ms.setState(MilestoneState.UNLOCKED);
                            try {
                                milestoneStatusRepo.saveAndFlush(ms);
                            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                                log.warn("[REFERRAL] Duplicate milestone claim prevented: userId={} milestoneId={}", referrerId, milestone.getId());
                                return;
                            }

                            // Modified to use the configured conversion rate 
                            long credits = milestone.getRewardAmount()
                                    .multiply(conversionRate).longValue();
                            
                            rewardService.earnCredits(referrerId, credits,
                                    "Milestone Reward: " + milestone.getName(), RewardSource.MILESTONE);

                            notificationService.sendNotification(referrerId, "REWARD",
                                "Milestone Unlocked! 🏆",
                                "You unlocked \"" + milestone.getName() + "\" and earned " +
                                credits + " credits!");

                            log.info("Milestone unlocked: userId={} milestone={}", referrerId, milestone.getName());
                        } else {
                            milestoneStatusRepo.save(ms);
                        }
                    }, () -> {
                        MilestoneStatus ms = MilestoneStatus.builder()
                                .user(event.getReferral().getReferrer())
                                .milestone(milestone)
                                .state(MilestoneState.LOCKED)
                                .currentProgress((int) successCount)
                                .build();
                        milestoneStatusRepo.save(ms);
                    });
        }
    }
}