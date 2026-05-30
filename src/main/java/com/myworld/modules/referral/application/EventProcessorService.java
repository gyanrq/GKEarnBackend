package com.myworld.modules.referral.application;

public interface EventProcessorService {
    void processKycVerified(Long userId);
}
