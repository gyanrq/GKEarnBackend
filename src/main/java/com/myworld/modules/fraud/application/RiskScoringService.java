package com.myworld.modules.fraud.application;

import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.DeviceFingerprintRepository;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.referral.infrastructure.ReferralRepository;
import com.myworld.modules.rewards.infrastructure.RedeemLogRepository;
import com.myworld.modules.rewards.infrastructure.RewardTransactionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskScoringService {

    private final UserRepository                userRepo;
    private final DeviceFingerprintRepository   fingerprintRepo;
    private final RedeemLogRepository           redeemLogRepo;
    private final RewardTransactionRepository   txRepo;
    private final ReferralRepository            referralRepo;
    private final IpIntelligenceService         ipIntelligenceService;

    // ── Injected Config Properties ─────────────────────────────────────────────
    @Value("${app.timezone:+05:30}")
    private String timezoneOffset;

    @Value("${app.fraud.scoring.hold-threshold:200}")
    private int holdThreshold;

    @Value("${app.fraud.scoring.block-threshold:500}")
    private int blockThreshold;

    @Value("${app.fraud.scoring.max-shared-ip:3}")
    private int maxSharedIpUsers;

    @Value("${app.fraud.scoring.max-shared-device:2}")
    private int maxSharedDeviceUsers;

    @Value("${app.fraud.scoring.fast-signup-seconds:60}")
    private int fastSignupSeconds;

    @Value("${app.fraud.scoring.multi-withdraw-24h:2}")
    private int multiWithdraw24h;

    // ── Weights (kept static as they are logic constants, can be moved later) ──
    private static final int W_SAME_IP          = 100;
    private static final int W_SAME_DEVICE      = 150;
    private static final int W_FAST_SIGNUP      = 100;
    private static final int W_NO_TRADE         = 200;
    private static final int W_MULTI_WITHDRAW   = 100;
    private static final int W_VPN_PROXY        = 80;
    private static final int W_REFERRAL_ABUSE   = 120;
    private static final int W_SHARED_UPI       = 120;

    public RiskResult scoreUser(Long userId) {
        throw new UnsupportedOperationException("Calling scoreUser without an HttpServletRequest is not supported. Use scoreUser(userId, request) instead.");
    }

    public RiskResult scoreUser(Long userId, HttpServletRequest request) {
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        return evaluate(user, null, request);
    }

    public RiskResult evaluate(User user, String paymentDetails, HttpServletRequest request) {
        int score = 0;
        List<String> flags = new ArrayList<>();
        ZoneOffset zone = ZoneOffset.of(timezoneOffset);
        String ip = extractIp(request);

        // 1. Same IP
        long sameIpUsers = userRepo.countActiveUsersWithSameIp(ip, user.getId());
        if (sameIpUsers > maxSharedIpUsers) {
            score += W_SAME_IP;
            flags.add("SAME_IP(" + sameIpUsers + ")");
        }

        // 2. Same device
        String deviceHash = request.getHeader("X-Device-ID");
        if (deviceHash != null && !deviceHash.isBlank()) {
            long sameDeviceUsers = fingerprintRepo.countOtherUsersWithDeviceId(deviceHash, user.getId());
            if (sameDeviceUsers > maxSharedDeviceUsers) {
                score += W_SAME_DEVICE;
                flags.add("SAME_DEVICE(" + sameDeviceUsers + ")");
            }
        }

        // 3. Shared UPI
        if (paymentDetails != null && !paymentDetails.isBlank()) {
            long upiCount = redeemLogRepo.countOtherUsersWithSamePaymentDetails(paymentDetails.toLowerCase().trim(), user.getId());
            if (upiCount > 0) {
                score += W_SHARED_UPI;
                flags.add("SHARED_UPI(" + upiCount + ")");
            }
        }

        // 4. Fast signup
        long accountAgeSeconds = java.time.temporal.ChronoUnit.SECONDS.between(user.getCreatedAt(), OffsetDateTime.now(zone));
        if (accountAgeSeconds < fastSignupSeconds) {
            score += W_FAST_SIGNUP;
            flags.add("FAST_SIGNUP(" + accountAgeSeconds + "s)");
        }

        // 5. No trade activity
        long tradeCount = txRepo.countEarnTransactionsByUser(user.getId());
        if (tradeCount == 0) {
            score += W_NO_TRADE;
            flags.add("NO_TRADE_ACTIVITY");
        }

        // 6. Multiple withdrawals
        OffsetDateTime since24h = OffsetDateTime.now(zone).minusHours(24);
        long withdrawLast24h = redeemLogRepo.countByUserIdAndCreatedAtAfter(user.getId(), since24h);
        if (withdrawLast24h > multiWithdraw24h) {
            score += W_MULTI_WITHDRAW;
            flags.add("MULTI_WITHDRAW_24H(" + withdrawLast24h + ")");
        }

        // 7. VPN / Proxy
        if (ipIntelligenceService.isVpnOrProxy(ip)) {
            score += W_VPN_PROXY;
            flags.add("VPN_PROXY");
        }

        // 8. Referral abuse
        boolean hasReferralBonus = referralRepo.existsByReferredUserIdAndBonusGiven(user.getId(), true);
        if (hasReferralBonus && tradeCount == 0) {
            score += W_REFERRAL_ABUSE;
            flags.add("REFERRAL_ABUSE");
        }

        boolean hardBlock = score >= blockThreshold;
        boolean hold      = score >= holdThreshold;

        log.info("[RISK] userId={} score={} flags={} decision={}", user.getId(), score, flags, hardBlock ? "BLOCK" : hold ? "HOLD" : "CLEAR");

        return new RiskResult(score, hardBlock, hold, flags);
    }

    private String extractIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}