package com.myworld.modules.payout.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.myworld.core.domain.BaseEntity;
import com.myworld.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "payout_requests",
    indexes = {
        @Index(name = "idx_payout_user",   columnList = "user_id"),
        @Index(name = "idx_payout_status", columnList = "status"),
        @Index(name = "idx_payout_idempotency", columnList = "idempotency_key", unique = true) // Unique index!
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PayoutRequest extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String payoutType;

    @Column(nullable = false)
    private String paymentDetails;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    // FIX: Field to guarantee double-clicks don't duplicate withdrawal
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    private String adminNotes;

    @Column(name = "transaction_ref")
    private String transactionRef;

    private String processedBy;
}