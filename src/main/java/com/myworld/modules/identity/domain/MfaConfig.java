package com.myworld.modules.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Stores MFA configuration for a user.
 *
 * MFA method selection logic:
 *   - mfaEnabled = false → login never triggers MFA (all flags below are irrelevant)
 *   - mfaEnabled = true  → at least one of the three method flags must be true
 *
 * Valid combinations when mfaEnabled=true:
 *   emailOtpEnabled only        → email OTP only
 *   mobileOtpEnabled only       → SMS OTP only  (mocked — just logs)
 *   totpEnabled only            → Google Authenticator only
 *   emailOtpEnabled+totpEnabled → both required (strictest)
 *   mobileOtpEnabled+totpEnabled→ both required
 *   emailOtpEnabled+mobileOtp   → both required
 *   all three                   → all three required
 *
 * The service enforces: at least one method must remain enabled when saving.
 */
@Entity
@Table(name = "mfa_configs", indexes = {
    @Index(name = "idx_mfa_user_id", columnList = "user_id", unique = true)
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MfaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Master switch. When false, no MFA is applied at login. */
    @Builder.Default
    @Column(nullable = false)
    private Boolean mfaEnabled = false;

    // ── Method flags ──────────────────────────────────────────────────────────

    /** Send OTP to user's email. Uses real SMTP (JavaMailSender). */
    @Builder.Default
    @Column(nullable = false)
    private Boolean emailOtpEnabled = false;

    /**
     * Send OTP to user's phone via SMS.
     * INTENTIONALLY MOCKED — logs the OTP instead of calling Twilio/SMS gateway.
     * Replace NotificationServiceImpl#sendSms when you have a real provider.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean mobileOtpEnabled = false;

    /** Google Authenticator / any TOTP app (RFC 6238). */
    @Builder.Default
    @Column(nullable = false)
    private Boolean totpEnabled = false;

    /**
     * Base32-encoded TOTP secret (32 chars).
     * NULL until user completes TOTP setup (scan QR → verify first code).
     * Never expose this field in any response DTO.
     */
    @Column(length = 64)
    private String totpSecret;

    /** True only after the user has scanned the QR and verified the first TOTP code. */
    @Builder.Default
    @Column(nullable = false)
    private Boolean totpVerified = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;
}