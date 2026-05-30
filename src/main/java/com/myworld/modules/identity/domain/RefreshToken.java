package com.myworld.modules.identity.domain;

import com.myworld.core.constant.AppConstants;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    @Builder.Default
    private Boolean revoked = false;

    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }
}
