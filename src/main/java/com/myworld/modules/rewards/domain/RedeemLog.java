package com.myworld.modules.rewards.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * RedeemLog — immutable audit record for every redemption attempt.
 *
 * Written for EVERY attempt — success, blocked, under-review, duplicate.
 * Never deleted. Never updated (except status transitions by admin).
 *
 * Table: redeem_log
 *
 * status values:
 *   INITIATED     — passed all checks, payout provider called
 *   COMPLETED     — payout confirmed via webhook
 *   BLOCKED       — hard-blocked by fraud engine
 *   UNDER_REVIEW  — soft-held for admin review
 *   ALREADY_PROCESSED — duplicate idempotency key
 *   FAILED        — payout provider returned failure
 */
@Entity
@Table(
    name = "redeem_log",
    indexes = {
        @Index(name = "idx_rl_user_created",  columnList = "user_id, created_at"),
        @Index(name = "idx_rl_reference_id",  columnList = "reference_id", unique = true),
        @Index(name = "idx_rl_status",        columnList = "status")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RedeemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long credits;

    @Column(name = "rupee_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal rupeeValue;

    @Column(name = "payment_details", nullable = false, length = 100)
    private String paymentDetails;

    @Column(name = "reference_id", nullable = false, unique = true, length = 64)
    private String referenceId;

    /** INITIATED | COMPLETED | BLOCKED | UNDER_REVIEW | ALREADY_PROCESSED | FAILED */
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "INITIATED";

    /** Comma-separated fraud flag names, empty string if none */
    @Column(name = "fraud_flags", length = 255)
    @Builder.Default
    private String fraudFlags = "";

    /** Client IP at the time of the request */
    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}