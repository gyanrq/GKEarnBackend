package com.myworld.modules.rewards.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.myworld.core.domain.BaseEntity;
import com.myworld.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_rewards",
    uniqueConstraints = @UniqueConstraint(columnNames = "user_id"),
    indexes = @Index(name = "idx_user_rewards_user", columnList = "user_id")
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class UserReward extends BaseEntity {

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    @Column(nullable = false)
    private Long totalCredits = 0L;

    @Builder.Default
    @Column(nullable = false)
    private Long redeemedCredits = 0L;

    @Builder.Default
    @Column(nullable = false)
    private Long pendingCredits = 0L;
}