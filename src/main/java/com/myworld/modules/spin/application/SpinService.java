package com.myworld.modules.spin.application;

import com.myworld.core.exception.BadRequestException;
import com.myworld.core.exception.ResourceNotFoundException;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.rewards.application.RewardService;
import com.myworld.modules.rewards.domain.RewardSource;
import com.myworld.modules.spin.domain.SpinHistory;
import com.myworld.modules.spin.domain.SpinPrize;
import com.myworld.modules.spin.infrastructure.SpinHistoryRepository;
import com.myworld.modules.spin.infrastructure.SpinPrizeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpinService {

    private final SpinHistoryRepository spinRepo;
    private final SpinPrizeRepository spinPrizeRepo;
    private final RewardService rewardService;
    private final UserRepository userRepo;

    // FIX: timezone from config instead of hardcoded IST
    @Value("${app.timezone:+05:30}")
    private String timezoneOffset = "+05:30";

    // FIX: java.util.Random replaced with SecureRandom.
    // While spin outcomes are not security-critical, SecureRandom is also stateless
    // and safe for concurrent access — java.util.Random can produce correlated outputs
    // under high concurrency (shared seed state). SecureRandom has no such issue.
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final List<long[]> FALLBACK_PRIZES = List.of(
        new long[]{10,  30},
        new long[]{20,  25},
        new long[]{50,  20},
        new long[]{100, 15},
        new long[]{200,  8},
        new long[]{500,  2}
    );

    @Transactional
    public long dailySpin(Long userId) {
        // FIX: was hardcoded ZoneOffset.ofHoursMinutes(5,30) — now reads from app.timezone config
        String offset = (timezoneOffset == null || timezoneOffset.isBlank()) ? "+05:30" : timezoneOffset;
        ZoneOffset zone = ZoneOffset.of(offset);
        OffsetDateTime todayIST = OffsetDateTime.now(zone)
                .toLocalDate().atStartOfDay().atOffset(zone);

        // Primary guard — fast path for the normal case
        if (spinRepo.existsByUserIdAndCreatedAtAfter(userId, todayIST))
            throw new BadRequestException("You have already used your free spin today. Come back tomorrow!");

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        long credits = weightedRandom();

        try {
            // FIX for concurrent spin race: if two requests pass the existsByUserId check
            // before either saves, the DB unique index on (user_id, date) catches the second
            // insert and throws DataIntegrityViolationException — mapped to a clean 400 here.
            // Add a unique index to spin_history: CREATE UNIQUE INDEX uq_spin_user_day
            //   ON spin_history (user_id, DATE(created_at AT TIME ZONE 'Asia/Kolkata'));
            spinRepo.save(SpinHistory.builder().user(user).creditsWon(credits).build());
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestException("You have already used your free spin today. Come back tomorrow!");
        }

        rewardService.earnCredits(userId, credits, "Daily Spin Reward", RewardSource.SPIN);
        log.info("Spin: userId={} won={} credits", userId, credits);
        return credits;
    }

    private long weightedRandom() {
        List<SpinPrize> dbPrizes = spinPrizeRepo.findByIsActiveTrueOrderBySortOrderAsc();

        if (!dbPrizes.isEmpty()) {
            int total = dbPrizes.stream().mapToInt(SpinPrize::getWeight).sum();
            if (total > 0) {
                int roll = SECURE_RANDOM.nextInt(total);
                int cumulative = 0;
                for (SpinPrize prize : dbPrizes) {
                    cumulative += prize.getWeight();
                    if (roll < cumulative) return prize.getCredits();
                }
                return dbPrizes.get(0).getCredits();
            }
        }

        int total = FALLBACK_PRIZES.stream().mapToInt(p -> (int) p[1]).sum();
        int roll = SECURE_RANDOM.nextInt(total);
        int cumulative = 0;
        for (long[] prize : FALLBACK_PRIZES) {
            cumulative += (int) prize[1];
            if (roll < cumulative) return prize[0];
        }
        return FALLBACK_PRIZES.get(0)[0];
    }

    @Transactional(readOnly = true)
    public List<SpinPrize> getActivePrizes() {
        return spinPrizeRepo.findByIsActiveTrueOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<SpinPrize> getAllPrizes() {
        return spinPrizeRepo.findAllByOrderBySortOrderAsc();
    }
}
