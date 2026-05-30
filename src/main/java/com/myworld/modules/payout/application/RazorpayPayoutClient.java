package com.myworld.modules.payout.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Razorpay Payout X API client.
 * Docs: https://razorpay.com/docs/razorpay-x/payouts/
 *
 * Set env vars:
 *   RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, RAZORPAY_ACCOUNT_NUMBER
 */
@Slf4j
@Component
public class RazorpayPayoutClient {

    @Value("${razorpay.key-id:}")
    private String keyId;

    @Value("${razorpay.key-secret:}")
    private String keySecret;

    @Value("${razorpay.account-number:}")
    private String accountNumber;

    @Value("${razorpay.payout.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String BASE_URL = "https://api.razorpay.com/v1";

    /**
     * Initiate a payout via Razorpay X.
     * @return Razorpay payout ID on success, null if disabled/failed
     */
    public String initiatePayout(Long userId, Long credits, BigDecimal amountRupees,
                                  String payoutType, String payoutDetails,
                                  String referenceId) {
        if (!enabled) {
            log.info("[RAZORPAY] Payout disabled — manual approval mode. ref={}", referenceId);
            return null;
        }
        if (keyId.isBlank() || keySecret.isBlank() || accountNumber.isBlank()) {
            log.warn("[RAZORPAY] Missing credentials — skipping auto-payout. ref={}", referenceId);
            return null;
        }

        try {
            Map<String, Object> payload = buildPayload(
                userId, credits, amountRupees, payoutType, payoutDetails, referenceId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBasicAuth(keyId, keySecret);
            headers.set("X-Payout-Idempotency", referenceId);

            String body = objectMapper.writeValueAsString(payload);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                BASE_URL + "/payouts", entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String razorpayId = (String) response.getBody().get("id");
                log.info("[RAZORPAY] Payout initiated: id={} ref={}", razorpayId, referenceId);
                return razorpayId;
            }
        } catch (Exception e) {
            log.error("[RAZORPAY] Payout initiation failed: ref={} error={}", referenceId, e.getMessage());
        }
        return null;
    }

    private Map<String, Object> buildPayload(Long userId, Long credits, BigDecimal amountRupees,
                                              String payoutType, String payoutDetails,
                                              String referenceId) {
        // Amount in paise (1 rupee = 100 paise)
        long amountPaise = amountRupees.multiply(BigDecimal.valueOf(100)).longValue();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("account_number", accountNumber);
        payload.put("amount", amountPaise);
        payload.put("currency", "INR");
        payload.put("mode", resolveMode(payoutType));
        payload.put("purpose", "payout");
        payload.put("reference_id", referenceId);
        payload.put("narration", "EarnX Reward Payout");

        // Fund account details
        Map<String, Object> fundAccount = new LinkedHashMap<>();
        fundAccount.put("account_type", resolveAccountType(payoutType));

        Map<String, Object> details = new LinkedHashMap<>();
        if ("UPI".equalsIgnoreCase(payoutType)) {
            details.put("address", payoutDetails);
            fundAccount.put("vpa", details);
        } else {
            details.put("ifsc", ""); // Would need IFSC for bank
            details.put("account_number", payoutDetails);
            details.put("name", "EarnX User");
            fundAccount.put("bank_account", details);
        }

        Map<String, Object> contact = Map.of(
            "name", "EarnX User " + userId,
            "type", "customer"
        );
        fundAccount.put("contact", contact);
        payload.put("fund_account", fundAccount);

        // Notes for webhook correlation
        payload.put("notes", Map.of(
            "userId", String.valueOf(userId),
            "credits", String.valueOf(credits),
            "referenceId", referenceId
        ));

        return payload;
    }

    private String resolveMode(String payoutType) {
        if (payoutType == null) return "UPI";
        return switch (payoutType.toUpperCase()) {
            case "BANK" -> "NEFT";
            case "IMPS" -> "IMPS";
            default     -> "UPI";
        };
    }

    private String resolveAccountType(String payoutType) {
        if ("UPI".equalsIgnoreCase(payoutType)) return "vpa";
        return "bank_account";
    }
}
