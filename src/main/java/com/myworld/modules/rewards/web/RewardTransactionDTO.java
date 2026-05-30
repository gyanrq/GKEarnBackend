package com.myworld.modules.rewards.web;

import com.myworld.modules.rewards.domain.RewardSource;
import com.myworld.modules.rewards.domain.RewardTransaction;
import com.myworld.modules.rewards.domain.RewardTxStatus;
import com.myworld.modules.rewards.domain.RewardTxType;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * Safe DTO for RewardTransaction — excludes the lazy User association
 * so Jackson never touches the Hibernate proxy and throws
 * HttpMessageConversionException / ByteBuddyInterceptor errors.
 *
 * File: src/main/java/com/myworld/modules/rewards/web/RewardTransactionDTO.java
 */
@Getter
public class RewardTransactionDTO {

    private final Long            id;
    private final String          uuid;
    private final Long            credits;
    private final RewardTxType    type;
    private final RewardSource    source;
    private final String          description;
    private final RewardTxStatus  status;
    private final String          referenceId;
    private final OffsetDateTime  createdAt;
    private final OffsetDateTime  updatedAt;

    /** Factory method — call inside a live Hibernate session (before tx closes). */
    public static RewardTransactionDTO from(RewardTransaction tx) {
        return new RewardTransactionDTO(tx);
    }

    private RewardTransactionDTO(RewardTransaction tx) {
        this.id          = tx.getId();
        this.uuid        = tx.getUuid();
        this.credits     = tx.getCredits();
        this.type        = tx.getType();
        this.source      = tx.getSource();
        this.description = tx.getDescription();
        this.status      = tx.getStatus();
        this.referenceId = tx.getReferenceId();
        this.createdAt   = tx.getCreatedAt();
        this.updatedAt   = tx.getUpdatedAt();
        // Intentionally omits: tx.getUser() — that's the lazy proxy causing the crash
    }
}