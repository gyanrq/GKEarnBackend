package com.myworld.modules.referral.domain;

import com.myworld.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "milestones")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Milestone extends BaseEntity {
    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MilestoneType milestoneType;

    @Column(nullable = false)
    private Integer targetValue;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal rewardAmount;

    private String description;

    @Builder.Default
    private Boolean isActive = true;
}
