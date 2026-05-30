package com.myworld.modules.fraud.application;

import com.myworld.core.exception.BadRequestException;
import com.myworld.modules.rewards.infrastructure.RedeemLogRepository;
import com.myworld.modules.rewards.infrastructure.RewardTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final RedeemLogRepository redeemLogRepo;
    private final RewardTransactionRepository txRepo;

    // FIX: Configurable Timezone
    @Value("${app.timezone:+05:30}")
    private String timezoneOffset;

    public void checkSameUPI(Long userId, String upiId) {
        if (upiId == null || upiId.isBlank()) return;
        long count = redeemLogRepo.countOtherUsersWithSamePaymentDetails(upiId.toLowerCase().trim(), userId);
        if (count > 0) {
            log.warn("[FRAUD] SHARED_UPI: userId={} upiId={} count={}", userId, upiId, count);
            throw new BadRequestException("This UPI ID is already associated with another account.");
        }
    }

    public void checkDailyEarnCap(Long userId, Long newCredits, Long maxDailyEarn) {
        // FIX: Extract offset dynamically
        ZoneOffset zone = ZoneOffset.of(timezoneOffset);
        OffsetDateTime midnight = OffsetDateTime.now(zone).toLocalDate().atStartOfDay().atOffset(zone);
        
        Long earnedToday = txRepo.sumEarnedSince(userId, midnight);
        if (earnedToday == null) earnedToday = 0L;
        if (earnedToday + newCredits > maxDailyEarn)
            throw new BadRequestException("Daily earn limit reached (" + maxDailyEarn + " credits).");
    }
}