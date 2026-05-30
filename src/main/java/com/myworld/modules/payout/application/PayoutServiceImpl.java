package com.myworld.modules.payout.application;

import com.myworld.core.dto.PageResponse;
import com.myworld.core.exception.BadRequestException;
import com.myworld.core.exception.ResourceNotFoundException;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.notification.api.NotificationEvent;
import com.myworld.modules.payout.api.PayoutDTO;
import com.myworld.modules.payout.domain.PaymentStatus;
import com.myworld.modules.payout.domain.PayoutRequest;
import com.myworld.modules.payout.infrastructure.PayoutRepository;
import com.myworld.modules.payout.mapper.PayoutMapper;
import com.myworld.modules.rewards.application.RewardService;
import com.myworld.modules.fraud.application.RiskResult;
import com.myworld.modules.fraud.application.RiskScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutServiceImpl implements PayoutService {

    private final PayoutRepository payoutRepo;
    private final UserRepository userRepo;
    private final RewardService rewardService;
    private final ApplicationEventPublisher eventPublisher;
    private final RazorpayPayoutClient razorpayClient;
    private final RiskScoringService riskScoringService;

    @Value("${app.reward.conversion-rate:10}")
    private BigDecimal conversionRate;

    @Value("${app.fraud.scoring.block-threshold:500}") 
    private int fraudBlockThreshold;

    @Value("${app.fraud.scoring.hold-threshold:200}")  
    private int fraudHoldThreshold;

    @Value("${app.payout.max-redeems-per-day:2}")
    private long maxRedeemsPerDay;

    @Value("${app.payout.max-rupees-per-day:5000}")
    private BigDecimal maxRupeesPerDay;

    @Override
    @Transactional
    public void requestPayout(Long userId, PayoutDTO dto, jakarta.servlet.http.HttpServletRequest httpRequest) {
        // Idempotency guard
        if (dto.getIdempotencyKey() != null && !dto.getIdempotencyKey().isBlank()) {
            if (payoutRepo.existsByIdempotencyKey(dto.getIdempotencyKey())) {
                log.warn("Idempotent request rejected: {}", dto.getIdempotencyKey());
                throw new BadRequestException("This payout request has already been processed.");
            }
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!Boolean.TRUE.equals(user.getIsEmailVerified()))
            throw new BadRequestException("Email verification required before requesting a payout.");

        if (user.getPhone() == null || !Boolean.TRUE.equals(user.getIsPhoneVerified()))
            throw new BadRequestException(
                "Phone number verification is required before requesting a payout.");

        java.time.OffsetDateTime todayMidnight = java.time.OffsetDateTime.now().with(java.time.LocalTime.MIDNIGHT);
        long todayCount = payoutRepo.countByUserIdAndStatusAndCreatedAtAfter(
            userId, PaymentStatus.PENDING, todayMidnight);
        if (todayCount >= maxRedeemsPerDay)
            throw new BadRequestException("Daily payout limit reached (" + maxRedeemsPerDay + " requests/day).");

        BigDecimal todayAmount = payoutRepo.sumAmountByUserIdAndCreatedAtAfter(userId, todayMidnight);
        if (todayAmount != null && todayAmount.add(dto.getAmount()).compareTo(maxRupeesPerDay) > 0)
            throw new BadRequestException("Daily payout amount limit reached (₹" + maxRupeesPerDay + "/day).");

        RiskResult risk = riskScoringService.scoreUser(userId, httpRequest);
        if (risk.getScore() >= fraudBlockThreshold) {
            log.warn("[FRAUD] Payout blocked: userId={} score={}", userId, risk.getScore());
            throw new BadRequestException(
                "Your account has been flagged for review. Contact support.");
        }
        
        PaymentStatus initialStatus = PaymentStatus.PENDING;
        if (risk.getScore() >= fraudHoldThreshold) {
            log.warn("[FRAUD] Payout held for review: userId={} score={}", userId, risk.getScore());
            initialStatus = PaymentStatus.ON_HOLD;
        }

        long credits = dto.getAmount().multiply(conversionRate).longValue();
        String referenceId = "PAYOUT-" + UUID.randomUUID();

        rewardService.redeemCredits(userId, credits,
                "Payout Request [" + dto.getPayoutType() + "] via " + dto.getPayoutDetails(),
                referenceId);

        PayoutRequest payoutRequest = PayoutRequest.builder()
                .user(user)
                .amount(dto.getAmount())
                .payoutType(dto.getPayoutType())
                .paymentDetails(dto.getPayoutDetails())
                .status(initialStatus)
                .transactionRef(referenceId)
                .idempotencyKey(dto.getIdempotencyKey())
                .build();
        payoutRepo.save(payoutRequest);

        log.info("Payout requested: userId={} amount={} credits={} ref={}",
                userId, dto.getAmount(), credits, referenceId);

        eventPublisher.publishEvent(new NotificationEvent(userId, "PAYOUT",
                "Payout Request Submitted",
                "Your payout request of ₹" + dto.getAmount() + " via " + dto.getPayoutType() +
                " is pending review."));
    }

    @Override
    @Transactional
    public void approvePayout(Long payoutId, String adminEmail) {
        PayoutRequest req = findPending(payoutId);
        req.setStatus(PaymentStatus.APPROVED);
        req.setProcessedBy(adminEmail);
        payoutRepo.save(req);

        rewardService.approveRedeem(req.getTransactionRef());

        log.info("Payout approved: payoutId={} by={}", payoutId, adminEmail);

        // 🚀 Auto-initiate Razorpay payout
        long credits = req.getAmount().multiply(conversionRate).longValue();
        String razorpayId = razorpayClient.initiatePayout(
                req.getUser().getId(), credits, req.getAmount(),
                req.getPayoutType(), req.getPaymentDetails(),
                req.getTransactionRef());

        if (razorpayId != null) {
            // Razorpay initiated — will be confirmed via webhook
            req.setStatus(PaymentStatus.PROCESSING);
            req.setAdminNotes("Razorpay payout initiated: " + razorpayId);
            payoutRepo.save(req);
            eventPublisher.publishEvent(new NotificationEvent(req.getUser().getId(), "PAYOUT",
                    "Payout Processing ⏳",
                    "Your payout of ₹" + req.getAmount() + " is being processed. Expect transfer in 1-2 hours."));
        } else {
            // Manual mode — admin will mark paid
            eventPublisher.publishEvent(new NotificationEvent(req.getUser().getId(), "PAYOUT",
                    "Payout Approved ✅",
                    "Your payout of ₹" + req.getAmount() + " has been approved and will be transferred shortly."));
        }
    }

    @Override
    @Transactional
    public void rejectPayout(Long payoutId, String reason, String adminEmail) {
        PayoutRequest req = findPending(payoutId);
        req.setStatus(PaymentStatus.REJECTED);
        req.setAdminNotes(reason);
        req.setProcessedBy(adminEmail);
        payoutRepo.save(req);

        rewardService.rejectRedeem(req.getTransactionRef());

        long credits = req.getAmount().multiply(conversionRate).longValue();
        log.info("Payout rejected: payoutId={} by={}", payoutId, adminEmail);

        String displayReason = (reason != null && !reason.isBlank()) ? reason : "Please contact support.";
        eventPublisher.publishEvent(new NotificationEvent(req.getUser().getId(), "PAYOUT",
                "Payout Rejected — Credits Refunded",
                "Your payout was rejected. Reason: " + displayReason + ". " + credits + " credits returned."));
    }

    @Override
    @Transactional
    public void markPaid(Long payoutId, String transactionRef, String adminEmail) {
        PayoutRequest req = payoutRepo.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found: " + payoutId));
        if (req.getStatus() != PaymentStatus.APPROVED && req.getStatus() != PaymentStatus.PROCESSING)
            throw new BadRequestException("Only APPROVED/PROCESSING payouts can be marked as PAID");

        req.setStatus(PaymentStatus.PAID);
        req.setTransactionRef(transactionRef);
        req.setProcessedBy(adminEmail);
        payoutRepo.save(req);

        eventPublisher.publishEvent(new NotificationEvent(req.getUser().getId(), "PAYOUT",
                "Payout Completed 🎉",
                "Your payout of ₹" + req.getAmount() + " has been transferred successfully!"));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PayoutDTO> getUserPayouts(Long userId, int page, int size) {
        return toPageResponse(payoutRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PayoutDTO> getAllPayouts(int page, int size) {
        return toPageResponse(payoutRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PayoutDTO> getPayoutsByStatus(PaymentStatus status, int page, int size) {
        return toPageResponse(payoutRepo.findByStatusOrderByCreatedAtAsc(status, PageRequest.of(page, size)));
    }

    private PayoutRequest findPending(Long payoutId) {
        PayoutRequest req = payoutRepo.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found: " + payoutId));
        if (req.getStatus() != PaymentStatus.PENDING)
            throw new BadRequestException("Only PENDING payouts can be actioned.");
        return req;
    }

    private PageResponse<PayoutDTO> toPageResponse(Page<PayoutRequest> pg) {
        List<PayoutDTO> content = pg.getContent().stream().map(PayoutMapper::toDTO).toList();
        return PageResponse.<PayoutDTO>builder()
                .content(content).page(pg.getNumber()).size(pg.getSize())
                .totalElements(pg.getTotalElements()).totalPages(pg.getTotalPages())
                .last(pg.isLast()).build();
    }
}
