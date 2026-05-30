package com.myworld.modules.identity.application;

import com.myworld.core.exception.BadRequestException;
import com.myworld.modules.identity.domain.Otp;
import com.myworld.modules.identity.infrastructure.OtpRepository;
import com.myworld.modules.notification.application.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpRepository     otpRepository;
    private final PasswordEncoder   passwordEncoder;
    private final Environment       environment;
    private final NotificationService notificationService;

    @Value("${app.otp.rate-window-minutes:15}")
    private int rateWindowMinutes;

    @Value("${app.otp.rate-limit:3}")
    private int rateLimit;

    @Value("${app.otp.expiry-minutes:5}")
    private int expiryMinutes;

    @Value("${app.otp.max-attempts:3}")
    private int maxAttempts;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private boolean isDevProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }

    /** Returns true if identifier looks like an email address (contains @). */
    private boolean isEmail(String identifier) {
        return identifier != null && identifier.contains("@");
    }

    @Transactional
    public void generateAndSendOtp(String identifier) {
        // Prevent database queries with null identifiers which cause global rate limit locks
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BadRequestException("Valid contact identifier is required to send OTP.");
        }

        // Bypass rate limits in DEV profile to prevent login loops during testing
        if (!isDevProfile()) {
            long recent = otpRepository.countRecentOtps(
                    identifier,
                    OffsetDateTime.now().minusMinutes(rateWindowMinutes)
            );
            if (recent >= rateLimit) {
                throw new BadRequestException(
                    "Too many OTP requests. Try after " + rateWindowMinutes + " minutes.");
            }
        }

        String otpCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        String hashed  = passwordEncoder.encode(otpCode);

        Otp otp = Otp.builder()
                .identifier(identifier)
                .codeHash(hashed)
                .expiresAt(OffsetDateTime.now().plusMinutes(expiryMinutes))
                .build();
        otpRepository.save(otp);

        // ── Always log in dev so you can test without real providers ──────────
        if (isDevProfile()) {
            log.info("🔔 [OTP DEV-ONLY] identifier={} code={}", identifier, otpCode);
        }

        // ── Send via real channel ─────────────────────────────────────────────
        if (isEmail(identifier)) {
            notificationService.sendOtpEmail(identifier, otpCode);
        } else {
            // Phone number — mocked (logs only) until a real SMS gateway is added
            notificationService.sendOtpSms(identifier, otpCode);
        }
    }

    @Transactional
    public void verifyOtp(String identifier, String rawOtp) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BadRequestException("Identifier cannot be empty");
        }

        Otp otp = otpRepository.findTopByIdentifierOrderByIdDesc(identifier)
                .orElseThrow(() -> new BadRequestException("No OTP found for this identifier"));

        if (Boolean.TRUE.equals(otp.getUsed())) {
            throw new BadRequestException("OTP has already been used");
        }
        if (OffsetDateTime.now().isAfter(otp.getExpiresAt())) {
            throw new BadRequestException("OTP has expired");
        }
        if (otp.getAttempts() >= maxAttempts) {
            throw new BadRequestException("Max OTP attempts exceeded. Request a new one.");
        }

        // ─── DEV BYPASS START: Phone ke liye koi bhi 6-digit accept karega ───
        // Agar identifier email nahi hai (matlab phone number hai) aur OTP me 6 digits hain,
        // toh password encoder ka check bypass kar do.
        if (!isEmail(identifier) && rawOtp != null && rawOtp.matches("\\d{6}")) {
            log.info("🔧 DEV BYPASS: Phone {} ke liye dummy OTP {} accept kar liya.", identifier, rawOtp);
        } 
        // ─── DEV BYPASS END ─────────────────────────────────────────────────
        else if (!passwordEncoder.matches(rawOtp, otp.getCodeHash())) {
            // Email OTP ke liye ya agar bypass fail ho jaye
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepository.save(otp);
            throw new BadRequestException("Invalid OTP");
        }

        otp.setUsed(true);
        otpRepository.save(otp);
    }
}