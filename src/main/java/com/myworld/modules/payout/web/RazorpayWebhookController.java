package com.myworld.modules.payout.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myworld.modules.notification.api.NotificationEvent;
import com.myworld.modules.payout.domain.PaymentStatus;
import com.myworld.modules.payout.infrastructure.PayoutRepository;
import com.myworld.modules.rewards.application.RewardService;
import com.myworld.modules.rewards.infrastructure.RedeemLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * FIX 6: Razorpay webhook — HMAC-SHA256 signature verification + Jackson parsing.
 *
 * v38 problems fixed:
 *   1. Custom extractJsonField() (manual string scanning) → replaced with Jackson JsonNode.
 *      The old parser broke on nested objects, arrays, and special chars.
 *   2. String.equalsIgnoreCase() for HMAC comparison → replaced with
 *      MessageDigest.isEqual() (constant-time) to prevent timing attacks.
 *
 * Security model:
 *   1. Verify X-Razorpay-Signature (HMAC-SHA256) before processing anything.
 *   2. Idempotency guard: skip if referenceId already processed (redeem_log).
 *   3. Credits deducted ONLY after Razorpay confirms payout.processed — never before.
 *
 * Setup in Razorpay Dashboard:
 *   Webhooks → Add → URL: https://yourdomain.com/api/webhook/razorpay
 *   Events: payout.processed, payout.failed
 *   Secret: set as env var RAZORPAY_WEBHOOK_SECRET
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook/razorpay")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final RewardService            rewardService;
    private final RedeemLogRepository      redeemLogRepo;
    private final ObjectMapper             objectMapper;
    private final PayoutRepository         payoutRepo;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${razorpay.webhook.secret:change-me-in-prod}")
    private String webhookSecret;

    @PostMapping(consumes = "application/json")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        // 1. Verify signature — reject anything without a valid HMAC
        if (!isSignatureValid(rawBody, signature)) {
            log.warn("[WEBHOOK] Invalid or missing Razorpay signature — rejected");
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        // 2. Parse with Jackson
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            log.error("[WEBHOOK] Malformed JSON: {}", ex.getMessage());
            return ResponseEntity.badRequest().body("Malformed JSON");
        }

        String event       = root.path("event").asText(null);
        JsonNode entity    = root.path("payload").path("payout").path("entity");
        String referenceId = entity.path("reference_id").asText(null);
        JsonNode notes     = entity.path("notes");
        String userIdStr   = notes.path("userId").asText(null);
        String creditsStr  = notes.path("credits").asText(null);

        if (referenceId == null || referenceId.isBlank()) {
            log.warn("[WEBHOOK] Missing reference_id — skipping");
            return ResponseEntity.ok("skipped: no reference_id");
        }

        log.info("[WEBHOOK] event={} ref={}", event, referenceId);

        if (event == null) return ResponseEntity.ok("ignored: null event");

        return switch (event) {
            case "payout.processed" -> handleProcessed(referenceId, userIdStr, creditsStr);
            case "payout.failed"    -> handleFailed(referenceId);
            default -> { log.debug("[WEBHOOK] Unhandled event: {}", event);
                         yield ResponseEntity.ok("ignored: " + event); }
        };
    }

    private ResponseEntity<String> handleProcessed(String ref, String userIdStr, String creditsStr) {
        if (userIdStr == null || creditsStr == null) {
            log.error("[WEBHOOK] payout.processed missing notes — ref={}", ref);
            return ResponseEntity.ok("skipped: missing notes");
        }
        if (isAlreadyProcessed(ref)) {
            log.info("[WEBHOOK] Already processed ref={} — idempotency guard", ref);
            return ResponseEntity.ok("already_processed");
        }
        try {
            long userId  = Long.parseLong(userIdStr.trim());
            long credits = Long.parseLong(creditsStr.trim());
            rewardService.redeemCredits(userId, credits, "Payout confirmed by Razorpay webhook", ref);
            redeemLogRepo.updateStatusByReferenceId(ref, "COMPLETED");
            log.info("[WEBHOOK] Credits deducted: userId={} credits={} ref={}", userId, credits, ref);
            return ResponseEntity.ok("ok");
        } catch (NumberFormatException ex) {
            log.error("[WEBHOOK] Invalid userId/credits — ref={}", ref);
            return ResponseEntity.badRequest().body("Invalid notes format");
        }
    }

    private ResponseEntity<String> handleFailed(String ref) {
        redeemLogRepo.updateStatusByReferenceId(ref, "FAILED");
        log.warn("[WEBHOOK] Payout FAILED — no credit change. ref={}", ref);

        // Notify the user their payout was rejected by Razorpay
        payoutRepo.findByTransactionRef(ref).ifPresentOrElse(payout -> {
            payout.setStatus(PaymentStatus.FAILED);
            payoutRepo.save(payout);
            eventPublisher.publishEvent(new NotificationEvent(
                payout.getUser().getId(),
                "PAYOUT_FAILED",
                "Payout Failed",
                "Your payout request of \u20b9" + payout.getAmount() + " via " + payout.getPayoutType() +
                " could not be processed. Please contact support with reference: " + ref
            ));
            log.info("[WEBHOOK] Payout failure notification sent: userId={} ref={}",
                payout.getUser().getId(), ref);
        }, () -> log.error("[WEBHOOK] PayoutRequest not found for ref={} — user not notified", ref));

        return ResponseEntity.ok("ok");
    }

    /**
     * FIX 6: Constant-time HMAC-SHA256 verification.
     * Uses MessageDigest.isEqual() to prevent timing side-channel attacks.
     */
    private boolean isSignatureValid(String rawBody, String signature) {
        if (signature == null || signature.isBlank()) {
            log.warn("[WEBHOOK] Missing X-Razorpay-Signature header");
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] received = HexFormat.of().parseHex(signature.toLowerCase());
            return MessageDigest.isEqual(computed, received); // constant-time comparison
        } catch (Exception ex) {
            log.error("[WEBHOOK] Signature error: {}", ex.getMessage());
            return false;
        }
    }

    private boolean isAlreadyProcessed(String referenceId) {
        return redeemLogRepo.findByStatusOrderByCreatedAtAsc("COMPLETED")
                .stream().anyMatch(r -> referenceId.equals(r.getReferenceId()));
    }
}
