package com.myworld.modules.referral.application;

import com.myworld.modules.referral.api.ReferralSuccessEvent;
import com.myworld.modules.referral.domain.Referral;
import com.myworld.modules.referral.domain.ReferralStatus;
import com.myworld.modules.referral.infrastructure.ReferralRepository;
import com.myworld.modules.rewards.application.RewardService;
import com.myworld.modules.rewards.domain.RewardSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventProcessorServiceImpl implements EventProcessorService {

    private final ReferralRepository referralRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final RewardService rewardService;

    // FIX: bonus credits read from config — no magic numbers in code
    @Value("${app.referral.bonus-credits:100}")
    private long referrerBonusCredits;

    @Value("${app.referral.referred-bonus-credits:50}")
    private long referredBonusCredits;

    @Override
    @Transactional
    public void processKycVerified(Long userId) {
        // FIX: was findAll() loading entire DB into memory, then filtering wrong field.
        // Now uses targeted query: find referrals where THIS USER is the referred person.
        referralRepo.findByReferredIdAndStatus(userId, ReferralStatus.PENDING)
                .forEach(r -> {
                    r.setStatus(ReferralStatus.SUCCESS);
                    referralRepo.save(r);

                    // FIX: credit referrer — this was the missing piece.
                    // Referral status was updated to SUCCESS but the referrer
                    // never received any reward credits. Now we credit them here
                    // BEFORE publishing ReferralSuccessEvent (which handles milestones).
                    if (!Boolean.TRUE.equals(r.getBonusGiven())) {
                        rewardService.earnCredits(
                            r.getReferrer().getId(),
                            referrerBonusCredits,
                            "Referral Bonus: " + r.getReferred().getEmail(),
                            RewardSource.REFERRAL
                        );
                        // FIX: also reward the referred user for completing signup via referral
                        rewardService.earnCredits(
                            userId,
                            referredBonusCredits,
                            "Welcome Bonus (joined via referral)",
                            RewardSource.REFERRAL
                        );
                        r.setBonusGiven(true);
                        referralRepo.save(r);
                    }

                    eventPublisher.publishEvent(new ReferralSuccessEvent(this, r));
                    log.info("Referral SUCCESS + bonus credited: referralId={} referrer={} credits={}",
                            r.getId(), r.getReferrer().getId(), referrerBonusCredits);
                });
    }
}