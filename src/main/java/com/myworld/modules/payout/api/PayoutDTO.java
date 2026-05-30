package com.myworld.modules.payout.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayoutDTO {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;

    // FIX: Idempotency Key added from Frontend
    private String idempotencyKey;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "10.00", message = "Minimum payout is ₹10")
    private BigDecimal amount;

    @NotBlank(message = "Payout type is required")
    private String payoutType;

    /**
     * FIX: payoutDetails was a free-text field with no validation.
     *
     * For UPI payouts the value must be a valid VPA (Virtual Payment Address):
     *   localPart@handle  e.g.  rajesh@upi  or  9876543210@paytm
     *
     * The regex deliberately allows digits-only local parts (phone-based VPAs)
     * and long handles (ybl, okicici, oksbi, apl …).
     *
     * Note: Bean Validation runs this pattern for EVERY payout type.
     * If you add non-UPI types in future (bank-transfer, cheque) that need a
     * different format, replace this with a custom @ValidPayoutDetails
     * constraint that branches on payoutType.
     */
    @NotBlank(message = "Payment details are required")
    @Pattern(
        regexp = "^[a-zA-Z0-9._%+\\-]{2,256}@[a-zA-Z]{2,64}$",
        message = "Invalid UPI ID. Expected format: localpart@handle (e.g. name@upi)"
    )
    private String payoutDetails;

    private String status;
    private String adminNotes;
    private String transactionRef;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}