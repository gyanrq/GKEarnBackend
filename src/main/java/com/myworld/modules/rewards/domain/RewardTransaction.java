package com.myworld.modules.rewards.domain;

import com.myworld.core.domain.BaseEntity;
import com.myworld.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "reward_transactions",
    indexes = {
        @Index(name = "idx_rtx_user",   columnList = "user_id"),
        @Index(name = "idx_rtx_type",   columnList = "type"),
        @Index(name = "idx_rtx_status", columnList = "status"),
        @Index(name = "idx_rtx_source", columnList = "source")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RewardTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Long credits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "type")
    private RewardTxType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "source")
    private RewardSource source;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RewardTxStatus status = RewardTxStatus.COMPLETED;

    private String referenceId;
}