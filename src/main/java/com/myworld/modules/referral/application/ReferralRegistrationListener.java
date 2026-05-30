package com.myworld.modules.referral.application;

import com.myworld.modules.identity.api.UserRegisteredEvent;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.referral.domain.Referral;
import com.myworld.modules.referral.domain.ReferralStatus;
import com.myworld.modules.referral.infrastructure.ReferralRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReferralRegistrationListener {

    private final ReferralRepository referralRepo;
    private final UserRepository userRepo;

    @EventListener
    @Transactional
    public void onUserRegistered(UserRegisteredEvent event) {
        String code = event.getReferredByCode();
        if (code == null || code.isBlank()) return;

        userRepo.findByReferralCodeIgnoreCase(code.trim()).ifPresentOrElse(referrer -> {
            if (referrer.getId().equals(event.getUser().getId())) {
                log.warn("Self-referral blocked: userId={}", referrer.getId());
                return;
            }
            if (referralRepo.existsByReferrerIdAndReferredId(referrer.getId(), event.getUser().getId())) {
                log.warn("Duplicate referral blocked: referrer={} referred={}", referrer.getId(), event.getUser().getId());
                return;
            }
            Referral referral = Referral.builder()
                    .referrer(referrer)
                    .referred(event.getUser())
                    .referralCode(code)
                    .status(ReferralStatus.PENDING)
                    .build();
            referralRepo.save(referral);
            log.info("Referral created: referrer={} referred={}", referrer.getId(), event.getUser().getId());
        }, () -> log.warn("Referral code not found: {}", code));
    }
}
