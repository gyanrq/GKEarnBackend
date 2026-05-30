package com.myworld.modules.identity.application;

import com.myworld.modules.identity.api.*;

public interface MfaService {

    // ── Settings (authenticated user) ─────────────────────────────────────────

    MfaSettingsResponse getMfaSettings(Long userId);

    void updateMfaSettings(Long userId, MfaSettingsRequest request);

    // ── TOTP Setup ────────────────────────────────────────────────────────────

    /** Step 1: generate secret + QR code. Saves secret but totpVerified stays false. */
    TotpSetupResponse initiateTotpSetup(Long userId);

    /** Step 2: user scans QR, submits first TOTP code → sets totpVerified=true. */
    void verifyAndActivateTotp(Long userId, String code);

    /** Disable TOTP and wipe the secret. */
    void disableTotp(Long userId);

    // ── Login MFA Challenge ────────────────────────────────────────────────────

    /**
     * Called immediately after password verification succeeds.
     * If MFA is enabled:
     *   - Creates an MfaPendingSession
     *   - Sends OTP(s) for enabled email/mobile methods
     *   - Returns MfaChallengeResponse (sessionToken + which methods are required)
     * If MFA is disabled:
     *   - Returns null (caller should issue tokens directly)
     */
    MfaChallengeResponse initiateMfaChallenge(Long userId);

    /** Verify email OTP step during MFA login. */
    void verifyMfaEmailOtp(String sessionToken, String otp);

    /** Verify mobile OTP step during MFA login. */
    void verifyMfaMobileOtp(String sessionToken, String otp);

    /** Verify TOTP step during MFA login. */
    void verifyMfaTotp(String sessionToken, String totpCode);

    /**
     * Check if all required MFA steps are complete.
     * Returns the userId if complete (so caller can issue tokens), throws otherwise.
     */
    Long completeMfaAndGetUserId(String sessionToken);
}