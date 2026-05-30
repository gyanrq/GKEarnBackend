package com.myworld.modules.identity.application;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.myworld.core.exception.BadRequestException;
import com.myworld.core.exception.ResourceNotFoundException;
import com.myworld.modules.identity.api.*;
import com.myworld.modules.identity.domain.*;
import com.myworld.modules.identity.infrastructure.*;
import com.myworld.modules.notification.application.NotificationService;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MfaServiceImpl implements MfaService {

    private final MfaConfigRepository     mfaConfigRepo;
    private final MfaPendingSessionRepository sessionRepo;
    private final OtpRepository           otpRepository;
    private final OtpService              otpService;
    private final UserRepository          userRepo;
    private final NotificationService     notificationService;

    @Value("${app.mfa.totp.issuer:EarnX3}")
    private String totpIssuer;

    @Value("${app.mfa.session.expiry-minutes:10}")
    private int sessionExpiryMinutes;

    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ── Settings ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public MfaSettingsResponse getMfaSettings(Long userId) {
        MfaConfig cfg = getOrCreateConfig(userId);
        return MfaSettingsResponse.builder()
                .mfaEnabled(cfg.getMfaEnabled())
                .emailOtpEnabled(cfg.getEmailOtpEnabled())
                .mobileOtpEnabled(cfg.getMobileOtpEnabled())
                .totpEnabled(cfg.getTotpEnabled())
                .totpVerified(cfg.getTotpVerified())
                .build();
    }

    @Override
    @Transactional
    public void updateMfaSettings(Long userId, MfaSettingsRequest request) {
        MfaConfig cfg = getOrCreateConfig(userId);

        boolean enabling = Boolean.TRUE.equals(request.getMfaEnabled());

        if (enabling) {
            boolean anyMethod = Boolean.TRUE.equals(request.getEmailOtpEnabled())
                    || Boolean.TRUE.equals(request.getMobileOtpEnabled())
                    || Boolean.TRUE.equals(request.getTotpEnabled());
            if (!anyMethod) {
                throw new BadRequestException(
                    "When enabling MFA, select at least one method: email OTP, mobile OTP, or Authenticator app.");
            }
            // If user enables TOTP but it hasn't been set up/verified yet, reject
            if (Boolean.TRUE.equals(request.getTotpEnabled()) && !Boolean.TRUE.equals(cfg.getTotpVerified())) {
                throw new BadRequestException(
                    "Set up Google Authenticator first before enabling it. Use /api/users/mfa/totp/setup.");
            }
            
            if (Boolean.TRUE.equals(request.getMobileOtpEnabled())) {
                User user = userRepo.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                if (user.getPhone() == null || !Boolean.TRUE.equals(user.getIsPhoneVerified())) {
                    throw new BadRequestException(
                        "Please add and verify your mobile number first before enabling SMS OTP.");
                }
            }
        }

        cfg.setMfaEnabled(enabling);
        cfg.setEmailOtpEnabled(enabling && Boolean.TRUE.equals(request.getEmailOtpEnabled()));
        cfg.setMobileOtpEnabled(enabling && Boolean.TRUE.equals(request.getMobileOtpEnabled()));
        cfg.setTotpEnabled(enabling && Boolean.TRUE.equals(request.getTotpEnabled()));

        mfaConfigRepo.save(cfg);
        log.info("MFA settings updated: userId={} enabled={} email={} mobile={} totp={}",
                userId, enabling,
                cfg.getEmailOtpEnabled(), cfg.getMobileOtpEnabled(), cfg.getTotpEnabled());
    }

    // ── TOTP Setup ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public TotpSetupResponse initiateTotpSetup(Long userId) {
        User user = findUser(userId);
        MfaConfig cfg = getOrCreateConfig(userId);

        // Generate a new secret (replaces any existing unverified secret)
        GoogleAuthenticatorKey credentials = gAuth.createCredentials();
        String secret = credentials.getKey();

        cfg.setTotpSecret(secret);
        cfg.setTotpVerified(false);
        mfaConfigRepo.save(cfg);

        // Build the OTP Auth URI for QR code
        String otpAuthUri = GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(
                totpIssuer, user.getEmail(), credentials);

        // Generate QR code as inline base64 PNG
        String qrDataUri = generateQrDataUri(otpAuthUri);

        log.info("TOTP setup initiated: userId={} email={}", userId, user.getEmail());

        return TotpSetupResponse.builder()
                .qrDataUri(qrDataUri)
                .manualEntryKey(secret)
                .issuer(totpIssuer)
                .accountName(user.getEmail())
                .build();
    }

    @Override
    @Transactional
    public void verifyAndActivateTotp(Long userId, String code) {
        MfaConfig cfg = mfaConfigRepo.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("TOTP setup not initiated. Call /mfa/totp/setup first."));

        if (cfg.getTotpSecret() == null) {
            throw new BadRequestException("TOTP setup not initiated. Call /mfa/totp/setup first.");
        }

        boolean valid = gAuth.authorize(cfg.getTotpSecret(), parseCode(code));
        if (!valid) {
            throw new BadRequestException("Invalid authenticator code. Make sure your device time is synced.");
        }

        cfg.setTotpVerified(true);
        mfaConfigRepo.save(cfg);
        log.info("TOTP activated: userId={}", userId);
    }

    @Override
    @Transactional
    public void disableTotp(Long userId) {
        MfaConfig cfg = mfaConfigRepo.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("MFA config not found"));

        cfg.setTotpEnabled(false);
        cfg.setTotpVerified(false);
        cfg.setTotpSecret(null);

        // If TOTP was the only enabled method, disable MFA entirely
        if (!Boolean.TRUE.equals(cfg.getEmailOtpEnabled()) && !Boolean.TRUE.equals(cfg.getMobileOtpEnabled())) {
            cfg.setMfaEnabled(false);
        }

        mfaConfigRepo.save(cfg);
        log.info("TOTP disabled and secret wiped: userId={}", userId);
    }

    // ── Login MFA Challenge ───────────────────────────────────────────────────

    /**
     * Public entry point — intentionally NOT @Transactional at this level.
     *
     * Why: the OTP delivery (email/SMS) happens AFTER the DB session is committed.
     * If mail/SMS throws a RuntimeException (e.g. MailSendException due to SMTP
     * timeout) and this method were @Transactional, Spring would roll back the
     * MfaPendingSession row and the client would receive a 500 even though nothing
     * was wrong with the credentials or DB.  By splitting into two phases we ensure:
     *   1. DB work commits cleanly in createMfaPendingSession().
     *   2. OTP delivery errors are caught, logged, and never propagate to the caller.
     */
    @Override
    public MfaChallengeResponse initiateMfaChallenge(Long userId) {
        MfaChallengeResponse challenge = createMfaPendingSession(userId);
        if (challenge == null) return null; // MFA disabled

        sendMfaOtps(userId, challenge);
        return challenge;
    }

    /** Phase 1: persist the pending session — committed before OTPs are sent. */
    @Transactional
    protected MfaChallengeResponse createMfaPendingSession(Long userId) {
        MfaConfig cfg = mfaConfigRepo.findByUserId(userId).orElse(null);

        if (cfg == null || !Boolean.TRUE.equals(cfg.getMfaEnabled())) {
            return null;
        }

        User user = findUser(userId);

        sessionRepo.deleteByUserId(userId);

        String sessionToken = UUID.randomUUID().toString().replace("-", "") +
                              UUID.randomUUID().toString().replace("-", "");
        MfaPendingSession session = MfaPendingSession.builder()
                .sessionToken(sessionToken)
                .user(user)
                .expiresAt(OffsetDateTime.now().plusMinutes(sessionExpiryMinutes))
                .build();
        sessionRepo.save(session);

        return MfaChallengeResponse.builder()
                .sessionToken(sessionToken)
                .emailRequired(Boolean.TRUE.equals(cfg.getEmailOtpEnabled()))
                .mobileRequired(Boolean.TRUE.equals(cfg.getMobileOtpEnabled()))
                .totpRequired(Boolean.TRUE.equals(cfg.getTotpEnabled()))
                .maskedEmail(maskEmail(user.getEmail()))
                .maskedPhone(maskPhone(user.getPhone()))
                .build();
    }

    /**
     * Phase 2: send OTP(s) — runs OUTSIDE any DB transaction.
     * Delivery failures are caught and logged; they must not crash the login flow.
     */
    /**
     * Phase 2: send OTP(s) — runs OUTSIDE any DB transaction.
     * Delivery failures are caught and logged; they must not crash the login flow.
     */
    private void sendMfaOtps(Long userId, MfaChallengeResponse challenge) {
        MfaConfig cfg = mfaConfigRepo.findByUserId(userId).orElse(null);
        if (cfg == null) return;

        User user = findUser(userId);

        if (Boolean.TRUE.equals(cfg.getEmailOtpEnabled())) {
            try {
                otpService.generateAndSendOtp(user.getEmail());
            } catch (BadRequestException e) {
                // Propagate validation/rate-limit exceptions so UI displays the error
                throw e;
            } catch (Exception e) {
                log.error("MFA email OTP delivery failed for userId={}: {}", userId, e.getMessage(), e);
            }
        }
        
        if (Boolean.TRUE.equals(cfg.getMobileOtpEnabled())) {
            try {
                otpService.generateAndSendOtp(user.getPhone());
            } catch (BadRequestException e) {
                // Propagate validation/rate-limit exceptions so UI displays the error
                throw e;
            } catch (Exception e) {
                log.error("MFA mobile OTP delivery failed for userId={}: {}", userId, e.getMessage(), e);
            }
        }
    }

    @Override
    @Transactional
    public void verifyMfaEmailOtp(String sessionToken, String otp) {
        MfaPendingSession session = getValidSession(sessionToken);
        User user = session.getUser();

        // Verify using the existing OtpService (same table, identifier = email)
        otpService.verifyOtp(user.getEmail(), otp);
        session.setEmailVerified(true);
        sessionRepo.save(session);
        log.info("MFA email OTP verified: userId={}", user.getId());
    }

    @Override
    @Transactional
    public void verifyMfaMobileOtp(String sessionToken, String otp) {
        MfaPendingSession session = getValidSession(sessionToken);
        User user = session.getUser();

        // OTP was generated against user.getPhone() identifier
        otpService.verifyOtp(user.getPhone(), otp);
        session.setMobileVerified(true);
        sessionRepo.save(session);
        log.info("MFA mobile OTP verified: userId={}", user.getId());
    }

    @Override
    @Transactional
    public void verifyMfaTotp(String sessionToken, String totpCode) {
        MfaPendingSession session = getValidSession(sessionToken);
        User user = session.getUser();

        MfaConfig cfg = mfaConfigRepo.findByUserId(user.getId())
                .orElseThrow(() -> new BadRequestException("MFA not configured"));

        if (!Boolean.TRUE.equals(cfg.getTotpVerified()) || cfg.getTotpSecret() == null) {
            throw new BadRequestException("Google Authenticator is not set up for this account.");
        }

        boolean valid = gAuth.authorize(cfg.getTotpSecret(), parseCode(totpCode));
        if (!valid) {
            throw new BadRequestException("Invalid authenticator code.");
        }

        session.setTotpVerified(true);
        sessionRepo.save(session);
        log.info("MFA TOTP verified: userId={}", user.getId());
    }

    @Override
    @Transactional
    public Long completeMfaAndGetUserId(String sessionToken) {
        MfaPendingSession session = getValidSession(sessionToken);
        User user = session.getUser();

        MfaConfig cfg = mfaConfigRepo.findByUserId(user.getId())
                .orElseThrow(() -> new BadRequestException("MFA not configured"));

        // Check each required method
        if (Boolean.TRUE.equals(cfg.getEmailOtpEnabled()) && !Boolean.TRUE.equals(session.getEmailVerified())) {
            throw new BadRequestException("Email OTP verification is still required.");
        }
        if (Boolean.TRUE.equals(cfg.getMobileOtpEnabled()) && !Boolean.TRUE.equals(session.getMobileVerified())) {
            throw new BadRequestException("Mobile OTP verification is still required.");
        }
        if (Boolean.TRUE.equals(cfg.getTotpEnabled()) && !Boolean.TRUE.equals(session.getTotpVerified())) {
            throw new BadRequestException("Google Authenticator verification is still required.");
        }

        // All required methods passed — clean up session
        sessionRepo.delete(session);
        log.info("MFA fully completed: userId={}", user.getId());
        return user.getId();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MfaPendingSession getValidSession(String sessionToken) {
        MfaPendingSession session = sessionRepo.findBySessionToken(sessionToken)
                .orElseThrow(() -> new BadRequestException("Invalid or expired MFA session."));
        if (session.isExpired()) {
            sessionRepo.delete(session);
            throw new BadRequestException("MFA session has expired. Please log in again.");
        }
        return session;
    }

    private MfaConfig getOrCreateConfig(Long userId) {
        return mfaConfigRepo.findByUserId(userId).orElseGet(() -> {
            User user = findUser(userId);
            return mfaConfigRepo.save(MfaConfig.builder().user(user).build());
        });
    }

    private User findUser(Long userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private int parseCode(String code) {
        try {
            return Integer.parseInt(code.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Authenticator code must be a 6-digit number.");
        }
    }

    private String generateQrDataUri(String otpAuthUri) {
        try {
            QRCodeWriter qrWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = qrWriter.encode(otpAuthUri, BarcodeFormat.QR_CODE, 200, 200, hints);

            ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOut);
            String base64 = Base64.getEncoder().encodeToString(pngOut.toByteArray());
            return "data:image/png;base64," + base64;
        } catch (WriterException | IOException e) {
            log.error("QR code generation failed", e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@");
        String local = parts[0];
        String domain = parts[1];
        if (local.length() <= 2) return "***@" + domain;
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + "@" + domain;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "******";
        return "******" + phone.substring(phone.length() - 4);
    }
}