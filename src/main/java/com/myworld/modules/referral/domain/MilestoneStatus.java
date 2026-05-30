package com.myworld.modules.referral.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.myworld.core.domain.BaseEntity;
import com.myworld.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "milestone_statuses",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","milestone_id"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MilestoneStatus extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id", nullable = false)
    private Milestone milestone;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MilestoneState state = MilestoneState.LOCKED;

    private Integer currentProgress;
}
