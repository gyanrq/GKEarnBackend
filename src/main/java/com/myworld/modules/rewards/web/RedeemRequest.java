package com.myworld.modules.rewards.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

/**
 * RedeemRequest — UPDATED VERSION.
 *
 * Changes:
 *  - Added Bean Validation annotations (@NotNull, @Positive, @NotBlank)
 *  - Added idempotencyKey for Layer 5 duplicate-request protection
 *
 * idempotencyKey: generate a UUID on the frontend before submitting.
 * If the same request is accidentally sent twice (slow network, double-tap),
 * the second call returns ALREADY_PROCESSED without deducting credits again.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RedeemRequest {

    @NotNull(message = "Credits must be provided")
    @Positive(message = "Credits must be a positive number")
    private Long credits;

    @NotBlank(message = "Redeem type is required")
    private String redeemType;        // CASH_REWARD | MOBILE_RECHARGE | GIFT_CARD

    @NotBlank(message = "Payment details (UPI ID / mobile number) are required")
    private String paymentDetails;

    /**
     * Optional client-generated UUID. If provided and already seen in redeem_log,
     * the request is ignored and ALREADY_PROCESSED is returned.
     * Strongly recommended for mobile clients on slow connections.
     */
    private String idempotencyKey;
}