package com.myworld.modules.spin.domain;

import com.myworld.core.domain.BaseEntity;
import com.myworld.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "spin_history",
    indexes = @Index(name = "idx_spin_user", columnList = "user_id")
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpinHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Long creditsWon;
}