package com.myworld.modules.rewards.application;

import com.myworld.core.exception.BadRequestException;
import com.myworld.core.exception.ResourceNotFoundException;
import com.myworld.modules.fraud.application.FraudCheckService;
import com.myworld.modules.fraud.application.FraudResult;
import com.myworld.modules.fraud.application.RiskResult;
import com.myworld.modules.fraud.application.RiskScoringService;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.payout.application.AuditService;
import com.myworld.modules.rewards.domain.RewardConfig;
import com.myworld.modules.rewards.domain.UserReward;
import com.myworld.modules.rewards.infrastructure.RedeemLogRepository;
import com.myworld.modules.rewards.infrastructure.RewardConfigRepository;
import com.myworld.modules.rewards.web.RedeemRequest;
import com.myworld.modules.rewards.web.RedeemResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedeemService {

    private final RewardService          rewardService;
    private final RewardConfigRepository configRepo;
    private final UserRepository         userRepo;
    private final RiskScoringService     riskScoringService;
    private final RedeemLogRepository    redeemLogRepo;
    private final AuditService           auditService;

    @Value("${app.timezone:+05:30}")
    private String timezoneOffset;

    @Value("${app.payout.max-redeems-per-day:3}")
    private int maxRedeemsPerDay;

    @Value("${app.payout.max-rupees-per-day:500}")
    private BigDecimal maxRupeesPerDay;

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public RedeemResult redeem(Long userId, RedeemRequest req, HttpServletRequest httpRequest) {

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (Boolean.TRUE.equals(user.getIsBlocked()))
            throw new BadRequestException("Your account is blocked. Contact support to resolve this.");

        if (!Boolean.TRUE.equals(user.getIsEmailVerified()))
            throw new BadRequestException("Email verification is required before redeeming.");

        if (req.getCredits() == null || req.getCredits() <= 0)
            throw new BadRequestException("Credits must be a positive number.");

        if (req.getPaymentDetails() == null || req.getPaymentDetails().isBlank())
            throw new BadRequestException("Payment details (UPI ID / mobile number) are required.");

        String clientRef = req.getIdempotencyKey();
        if (clientRef != null && !clientRef.isBlank()
                && redeemLogRepo.existsByReferenceId(clientRef)) {
            log.warn("Duplicate redeem attempt ignored: userId={} ref={}", userId, clientRef);
            return RedeemResult.builder()
                    .referenceId(clientRef)
                    .status("ALREADY_PROCESSED")
                    .message("This redemption was already processed. Check your transaction history.")
                    .build();
        }

        ZoneOffset zone = ZoneOffset.of(timezoneOffset);
        OffsetDateTime midnightIST = OffsetDateTime.now(zone).toLocalDate().atStartOfDay().atOffset(zone);

        long todayRedeems = redeemLogRepo.countByUserIdAndCreatedAtAfter(userId, midnightIST);
        if (todayRedeems >= maxRedeemsPerDay)
            throw new BadRequestException("You have reached the daily limit of " + maxRedeemsPerDay + " redemptions.");

        RewardConfig config = configRepo.findFirstByIsActiveTrue().orElseGet(RewardConfig::defaults);

        String windowStart = config.getRedeemWindowStart();
        String windowEnd   = config.getRedeemWindowEnd();
        if (windowStart != null && !windowStart.isBlank() && windowEnd != null && !windowEnd.isBlank()) {
            LocalTime now   = OffsetDateTime.now(zone).toLocalTime();
            LocalTime start = LocalTime.parse(windowStart);
            LocalTime end   = LocalTime.parse(windowEnd);
            boolean inWindow = start.isBefore(end)
                    ? (!now.isBefore(start) && !now.isAfter(end))
                    : (!now.isBefore(start) || !now.isAfter(end));
            if (!inWindow)
                throw new BadRequestException("Redemptions are only allowed between " + windowStart + " and " + windowEnd + " IST.");
        }

        long requiredSeconds = config.getRedemptionWaitSeconds() != null ? config.getRedemptionWaitSeconds() : 0L;
        if (requiredSeconds > 0) {
            long secondsSinceJoin = ChronoUnit.SECONDS.between(user.getCreatedAt(), OffsetDateTime.now(zone));
            if (secondsSinceJoin < requiredSeconds) {
                long remaining = requiredSeconds - secondsSinceJoin;
                throw new BadRequestException("You can redeem after " + remaining + " more second(s).");
            }
        }

        long minCredits = config.getMinRedeemCredits();
        if (req.getCredits() < minCredits)
            throw new BadRequestException("Minimum redemption is " + minCredits + " credits.");

        UserReward balance = rewardService.getBalance(userId);
        long available = balance.getTotalCredits() - balance.getRedeemedCredits() - balance.getPendingCredits();

        if (available < req.getCredits())
            throw new BadRequestException("Insufficient credits. Available: " + available);

        int creditsPerRupee = config.getCreditsPerRupee();
        BigDecimal rupeeValue = BigDecimal.valueOf(req.getCredits())
                .divide(BigDecimal.valueOf(creditsPerRupee), 2, RoundingMode.FLOOR);

        BigDecimal todayTotal = redeemLogRepo.sumSuccessfulAmountByUserIdAndCreatedAtAfter(userId, midnightIST);
        if (todayTotal == null) todayTotal = BigDecimal.ZERO;
        if (todayTotal.add(rupeeValue).compareTo(maxRupeesPerDay) > 0)
            throw new BadRequestException("Daily payout cap of ₹" + maxRupeesPerDay + " reached.");

        RiskResult risk = riskScoringService.evaluate(user, req.getPaymentDetails(), httpRequest);

        String referenceId = (clientRef != null && !clientRef.isBlank()) ? clientRef : UUID.randomUUID().toString();
        String clientIp    = extractIp(httpRequest);
        String flagsStr    = String.join(",", risk.getFlags());

        if (risk.isHardBlock()) {
            auditService.logRedeemAttempt(userId, req.getCredits(), rupeeValue, req.getPaymentDetails(), referenceId, "BLOCKED", flagsStr, clientIp);
            throw new BadRequestException("Redemption blocked due to suspicious activity. Reference: " + referenceId);
        }

        if (risk.isHold()) {
            auditService.logRedeemAttempt(userId, req.getCredits(), rupeeValue, req.getPaymentDetails(), referenceId, "UNDER_REVIEW", flagsStr, clientIp);
            return RedeemResult.builder()
                    .referenceId(referenceId)
                    .creditsRedeemed(req.getCredits())
                    .approxRupees(rupeeValue)
                    .status("UNDER_REVIEW")
                    .message("Your request is under review. Reference: " + referenceId)
                    .build();
        }

        auditService.logRedeemAttempt(userId, req.getCredits(), rupeeValue, req.getPaymentDetails(), referenceId, "INITIATED", flagsStr, clientIp);
        rewardService.redeemCredits(userId, req.getCredits(), "Payout Request: " + req.getRedeemType(), referenceId);

        return RedeemResult.builder()
                .referenceId(referenceId)
                .creditsRedeemed(req.getCredits())
                .approxRupees(rupeeValue)
                .status("INITIATED")
                .message("Redemption initiated successfully. Reference: " + referenceId)
                .build();
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}