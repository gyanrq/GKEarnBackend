package com.myworld.modules.rewards.web;

import lombok.*;
import java.math.BigDecimal;

/**
 * RedeemResult — UPDATED VERSION.
 *
 * Added `message` field for user-facing explanation of non-COMPLETED statuses.
 *
 * status values:
 *   COMPLETED          — credits deducted, payout confirmed
 *   INITIATED          — payout sent to provider, awaiting webhook confirmation
 *   UNDER_REVIEW       — held by fraud engine, admin will review within 24 h
 *   BLOCKED            — hard-blocked (should not reach client — exception thrown)
 *   ALREADY_PROCESSED  — duplicate idempotency key, no action taken
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RedeemResult {
    private String     referenceId;
    private Long       creditsRedeemed;
    private BigDecimal approxRupees;   // informational — not stored in DB
    private String     status;
    private String     message;
}