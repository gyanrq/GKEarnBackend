package com.myworld.modules.referral.api;

import com.myworld.modules.referral.domain.Referral;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ReferralSuccessEvent extends ApplicationEvent {
    private final Referral referral;
    public ReferralSuccessEvent(Object source, Referral referral) {
        super(source);
        this.referral = referral;
    }
}
