package com.myworld.modules.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * Tracks a partially-authenticated login session.
 *
 * Flow:
 *   1. User provides correct password → create MfaPendingSession (pre-auth token issued)
 *   2. Frontend calls /api/auth/mfa/verify-* for each required method
 *   3. Once all required methods pass → issue real JWT access + refresh tokens
 *   4. Session deleted after success or expiry
 *
 * emailVerified / mobileVerified / totpVerified track which steps are done.
 * The service checks these against the user's MfaConfig to decide if login is complete.
 */
@Entity
@Table(name = "mfa_pending_sessions", indexes = {
    @Index(name = "idx_mfa_session_token", columnList = "session_token", unique = true),
    @Index(name = "idx_mfa_session_user",  columnList = "user_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MfaPendingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sessionToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @Column(nullable = false)
    private Boolean emailVerified = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean mobileVerified = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean totpVerified = false;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }
}