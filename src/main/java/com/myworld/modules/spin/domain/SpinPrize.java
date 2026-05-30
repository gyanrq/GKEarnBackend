package com.myworld.modules.spin.domain;

import com.myworld.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "spin_prizes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpinPrize extends BaseEntity {

    @Column(nullable = false)
    private Long credits;

    @Column(nullable = false)
    private Integer weight;

    @Column(nullable = false)
    private String label;

    private String color;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @Column(nullable = false)
    private Integer sortOrder = 0;
}