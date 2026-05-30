package com.myworld.modules.identity.application;

// ============================================================
// FILE: src/main/java/com/myworld/modules/identity/application/GoogleOAuthService.java
// FIX: Multiple Google client IDs support (Web + Android)
// ============================================================

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.myworld.core.constant.Role;
import com.myworld.core.exception.BadRequestException;
import com.myworld.core.security.CustomUserDetails;
import com.myworld.core.security.JwtUtil;
import com.myworld.modules.identity.api.LoginResponseDTO;
import com.myworld.modules.identity.api.MfaChallengeResponse;
import com.myworld.modules.identity.api.UserRegisteredEvent;
import com.myworld.modules.identity.application.MfaService;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.identity.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private final UserRepository            userRepo;
    private final RefreshTokenService       refreshTokenService;
    private final JwtUtil                   jwtUtil;
    private final UserMapper                userMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final MfaService                mfaService;  // ✅ FIX: inject to check MFA on Google login

    // ✅ Comma-separated: Web client ID + Android client ID dono
    @Value("${app.google.client-ids}")
    private String googleClientIds;

    @Value("${app.referral.prefix:EX-}")
    private String referralPrefix;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public LoginResponseDTO loginWithGoogle(String idTokenString) {
        GoogleIdToken.Payload payload = verifyGoogleToken(idTokenString);

        String  email         = payload.getEmail().toLowerCase().trim();
        Boolean emailVerified = payload.getEmailVerified();
        String  name          = (String) payload.get("name");
        String  pictureUrl    = (String) payload.get("picture");

        if (!Boolean.TRUE.equals(emailVerified)) {
            throw new BadRequestException("Google account email is not verified.");
        }

        User user = userRepo.findByEmail(email).orElseGet(() -> {
            log.info("New Google OAuth user, creating account: email={}", email);
            return createGoogleUser(email, name, pictureUrl);
        });

        if (Boolean.TRUE.equals(user.getIsBlocked())) {
            throw new BadRequestException("Your account has been blocked. Contact support.");
        }

        if (user.getProfilePictureUrl() == null && pictureUrl != null) {
            user.setProfilePictureUrl(pictureUrl);
        }

        if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
            user.setIsEmailVerified(true);
        }

        user.setLastLoginAt(OffsetDateTime.now());
        userRepo.save(user);

        // ✅ FIX: Check if user has MFA enabled — Google login must also go through MFA challenge
        MfaChallengeResponse mfaChallenge = mfaService.initiateMfaChallenge(user.getId());
        if (mfaChallenge != null) {
            log.info("Google login requires MFA challenge: userId={}", user.getId());
            return LoginResponseDTO.builder()
                    .mfaRequired(true)
                    .mfaChallenge(mfaChallenge)
                    .build();
        }

        return buildLoginResponse(user);
    }

    private GoogleIdToken.Payload verifyGoogleToken(String idTokenString) {
        try {
            // ✅ Dono client IDs (Web + Android) ko list mein convert karo
            List<String> clientIds = Arrays.stream(googleClientIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            log.debug("Verifying Google token against {} client IDs", clientIds.size());

            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(clientIds)  // ✅ List pass karo — dono accept hoga
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new BadRequestException("Invalid Google token. Please try again.");
            }
            return idToken.getPayload();

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google token verification failed", e);
            throw new BadRequestException("Google authentication failed. Please try again.");
        }
    }

    private User createGoogleUser(String email, String name, String pictureUrl) {
        String referralCode = generateUniqueReferralCode();
        // Unusable password — Google users can only log in via Google
        String unusablePassword = "GOOGLE_" + HexFormat.of().formatHex(SECURE_RANDOM.generateSeed(16));

        User newUser = User.builder()
                .email(email)
                .name(name != null && !name.isBlank() ? name : email.split("@")[0])
                .phone(null)           // ✅ null allowed — Google user, phone baad mein add karega
                .password(unusablePassword)
                .role(Role.USER)
                .isEmailVerified(true)
                .isPhoneVerified(false)
                .isBlocked(false)
                .isDeleted(false)
                .profilePictureUrl(pictureUrl)
                .referralCode(referralCode)
                .build();

        User saved = userRepo.save(newUser);
        eventPublisher.publishEvent(new UserRegisteredEvent(this, saved, null));
        log.info("Google user created: id={}, email={}", saved.getId(), email);
        return saved;
    }

    private String generateUniqueReferralCode() {
        String code;
        int attempts = 0;
        do {
            code = referralPrefix + HexFormat.of()
                    .formatHex(SECURE_RANDOM.generateSeed(4))
                    .toUpperCase();
            if (++attempts > 10) throw new RuntimeException("Could not generate unique referral code");
        } while (userRepo.existsByReferralCode(code));
        return code;
    }

    private LoginResponseDTO buildLoginResponse(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken = jwtUtil.generateAccessToken(
                user.getEmail(), user.getId(), userDetails.getAuthorities());
        var refresh = refreshTokenService.createRefreshToken(user.getId());

        return LoginResponseDTO.builder()
                .mfaRequired(false)
                .accessToken(accessToken)
                .refreshToken(refresh.getToken())
                .user(userMapper.toDTO(user))
                .build();
    }
}