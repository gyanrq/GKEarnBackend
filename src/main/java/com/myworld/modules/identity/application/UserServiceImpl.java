package com.myworld.modules.identity.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.myworld.core.dto.PageResponse;
import com.myworld.core.exception.BadRequestException;
import com.myworld.core.exception.ResourceNotFoundException;
import com.myworld.core.security.CustomUserDetails;
import com.myworld.core.security.JwtUtil;
import com.myworld.core.security.LoginAttemptService;
import com.myworld.modules.identity.api.*;
import com.myworld.modules.identity.domain.*;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.identity.mapper.UserMapper;
import com.myworld.modules.referral.application.EventProcessorService;

import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository          userRepo;
    private final PasswordEncoder         passwordEncoder;
    private final RefreshTokenService     refreshTokenService;
    private final JwtUtil                 jwtUtil;
    private final LoginAttemptService     loginAttemptService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserMapper              userMapper;
    private final OtpService             otpService;
    private final MfaService             mfaService;
    private final EventProcessorService  eventProcessorService;

    @Value("${app.referral.prefix:EX-}")
    private String referralPrefix;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ── Registration ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserResponseDTO createUser(UserRequestDTO dto) {
        log.info("Registering user: email={}", dto.getEmail());

        if (userRepo.existsByEmailIncludingDeleted(dto.getEmail()))
            throw new BadRequestException("Email already registered");
        if (userRepo.existsByPhoneIncludingDeleted(dto.getPhone()))
            throw new BadRequestException("Phone number already registered");

        User user = userMapper.toEntity(dto);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setReferralCode(generateUniqueReferralCode());

        User saved = userRepo.save(user);
        eventPublisher.publishEvent(new UserRegisteredEvent(this, saved, dto.getReferredByCode()));
        return userMapper.toDTO(saved);
    }

    // ── Login (MFA-aware) ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public LoginResponseDTO login(LoginRequestDTO dto, HttpServletRequest request) {
        String input = dto.getUsername().trim();
        String ip    = request.getRemoteAddr();

        if (loginAttemptService.isBlocked(input, ip))
            throw new BadRequestException("Too many attempts. Try again later.");

        User user = userRepo.findByEmailOrPhone(input, input).orElseGet(() -> {
            loginAttemptService.loginFailed(input, ip);
            throw new BadRequestException("Invalid credentials");
        });

        if (Boolean.TRUE.equals(user.getIsBlocked()))
            throw new BadRequestException("Your account has been blocked. Contact support.");

        if (Boolean.TRUE.equals(user.getIsDeleted()))
            throw new BadRequestException("Account not found");

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            loginAttemptService.loginFailed(input, ip);
            throw new BadRequestException("Invalid credentials");
        }

        loginAttemptService.loginSuccess(input, ip);
        user.setLastLoginAt(OffsetDateTime.now());
        user.setLastLoginIp(ip);
        userRepo.save(user);

        // ── Check MFA ────────────────────────────────────────────────────────
        MfaChallengeResponse challenge = mfaService.initiateMfaChallenge(user.getId());

        if (challenge != null) {
            // MFA required — return challenge, no tokens yet
            log.info("MFA challenge issued: userId={}", user.getId());
            return LoginResponseDTO.builder()
                    .mfaRequired(true)
                    .mfaChallenge(challenge)
                    .build();
        }

        // MFA disabled — issue tokens immediately
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponseDTO completeMfaLogin(String sessionToken, HttpServletRequest request) {
        // Validate all MFA steps are done, get userId
        Long userId = mfaService.completeMfaAndGetUserId(sessionToken);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Update last login
        user.setLastLoginAt(OffsetDateTime.now());
        user.setLastLoginIp(request.getRemoteAddr());
        userRepo.save(user);

        log.info("MFA login complete, issuing tokens: userId={}", userId);
        return buildAuthTokenResponse(user);
    }

    // ── Token helpers ─────────────────────────────────────────────────────────

    private LoginResponseDTO buildAuthResponse(User user) {
        AuthResponseDTO auth = buildAuthTokenResponse(user);
        return LoginResponseDTO.builder()
                .mfaRequired(false)
                .accessToken(auth.getAccessToken())
                .refreshToken(auth.getRefreshToken())
                .user(auth.getUser())
                .build();
    }

    private AuthResponseDTO buildAuthTokenResponse(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken  = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), userDetails.getAuthorities());
        RefreshToken refresh = refreshTokenService.createRefreshToken(user.getId());

        return AuthResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refresh.getToken())
                .user(userMapper.toDTO(user))
                .build();
    }

    // ── Token refresh / logout ────────────────────────────────────────────────

    @Override
    @Transactional
    public AuthResponseDTO refreshToken(String token) {
        RefreshToken rt = refreshTokenService.validateAndGet(token);
        User user = rt.getUser();

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String newAccess   = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), userDetails.getAuthorities());
        RefreshToken newRefresh = refreshTokenService.createRefreshToken(user.getId());

        return AuthResponseDTO.builder()
                .accessToken(newAccess)
                .refreshToken(newRefresh.getToken())
                .user(userMapper.toDTO(user))
                .build();
    }

    @Override
    @Transactional
    public void logout(String email) {
        User user = findByEmailOrThrow(email);
        refreshTokenService.revokeAllForUser(user.getId());
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    @Override
    public UserResponseDTO getCurrentUserByEmail(String email) {
        return userMapper.toDTO(findByEmailOrThrow(email));
    }

    @Override
    @Transactional
    public UserResponseDTO updateProfile(String email, UserProfileUpdateDTO dto) {
        User user = findByEmailOrThrow(email);
        if (dto.getName()              != null) user.setName(dto.getName());
        if (dto.getDateOfBirth()       != null) user.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getAddress()           != null) user.setAddress(dto.getAddress());
        if (dto.getCity()              != null) user.setCity(dto.getCity());
        if (dto.getState()             != null) user.setState(dto.getState());
        if (dto.getPincode()           != null) user.setPincode(dto.getPincode());
        if (dto.getProfilePictureUrl() != null) user.setProfilePictureUrl(dto.getProfilePictureUrl());
        return userMapper.toDTO(userRepo.save(user));
    }

    @Override
    public UserResponseDTO getUser(Long id) {
        return userMapper.toDTO(userRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found")));
    }

    @Override
    public PageResponse<UserResponseDTO> getAllUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<User> userPage = userRepo.findAll(pageable);
        List<UserResponseDTO> content = userPage.getContent().stream().map(userMapper::toDTO).toList();
        return buildPage(content, userPage);
    }

    // ── Password ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void changePassword(String email, ChangePasswordDTO dto) {
        User user = findByEmailOrThrow(email);
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword()))
            throw new BadRequestException("Old password is incorrect");
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepo.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
    }

    @Override
    public void forgotPassword(ForgotPasswordDTO dto) {
        userRepo.findByEmailOrPhone(dto.getIdentifier(), dto.getIdentifier())
                .ifPresent(user -> otpService.generateAndSendOtp(dto.getIdentifier()));
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordDTO dto) {
        otpService.verifyOtp(dto.getIdentifier(), dto.getOtp());
        User user = userRepo.findByEmailOrPhone(dto.getIdentifier(), dto.getIdentifier())
                .orElseThrow(() -> new BadRequestException("User not found"));
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepo.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
    }

    // ── Email Verification ────────────────────────────────────────────────────

    @Override
    public void sendEmailVerification(String email) {
        User user = findByEmailOrThrow(email);
        if (Boolean.TRUE.equals(user.getIsEmailVerified()))
            throw new BadRequestException("Email is already verified");
        otpService.generateAndSendOtp(email);
    }

    @Override
    @Transactional
    public void verifyEmail(VerifyOtpDTO dto) {
        otpService.verifyOtp(dto.getIdentifier(), dto.getOtp());
        User user = findByEmailOrThrow(dto.getIdentifier());
        // FIX: track first-time verification to avoid re-triggering referral chain
        boolean wasAlreadyVerified = Boolean.TRUE.equals(user.getIsEmailVerified());
        user.setIsEmailVerified(true);
        userRepo.save(user);
        // FIX: trigger referral reward chain on first-time email verification.
        // Previously verifyEmail() set isEmailVerified=true but never called
        // processKycVerified(), so PENDING referrals stayed PENDING forever
        // and the referrer never received reward credits.
        if (!wasAlreadyVerified) {
            eventProcessorService.processKycVerified(user.getId());
            log.info("Email verified — referral chain triggered: userId={}", user.getId());
        }
    }

    // ── Phone Verification ────────────────────────────────────────────────

     // ── Email Update (change to new email) ───────────────────────────────────

     @Override
     @Transactional
     public void sendEmailUpdateOtp(String currentEmail, String newEmail) {
         String normalizedNew = newEmail.trim().toLowerCase();

         // Make sure new email not already taken by someone else
         if (userRepo.existsByEmailIncludingDeleted(normalizedNew)) {
             throw new BadRequestException("This email address is already registered.");
         }

         User user = findByEmailOrThrow(currentEmail);

         // Don't allow changing to same email
         if (user.getEmail().equalsIgnoreCase(normalizedNew)) {
             throw new BadRequestException("New email is the same as your current email.");
         }

         // Send OTP to the NEW email (user must prove they own it)
         otpService.generateAndSendOtp(normalizedNew);
         log.info("Email update OTP sent: userId={}, newEmail={}", user.getId(), normalizedNew);
     }

     @Override
     @Transactional
     public void verifyAndUpdateEmail(String currentEmail, String newEmail, String otp) {
         String normalizedNew = newEmail.trim().toLowerCase();

         // Verify OTP was sent to the new email and is valid
         otpService.verifyOtp(normalizedNew, otp);

         User user = findByEmailOrThrow(currentEmail);

         // Double-check no one else grabbed it while user was verifying
         if (userRepo.existsByEmailIncludingDeleted(normalizedNew)
                 && !user.getEmail().equalsIgnoreCase(normalizedNew)) {
             throw new BadRequestException("This email was just registered by another account.");
         }

         user.setEmail(normalizedNew);
         user.setIsEmailVerified(true);
         userRepo.save(user);
         log.info("Email updated: userId={}, newEmail={}", user.getId(), normalizedNew);
     }

     // ── Phone Add / Update + Verification ────────────────────────────────────

     @Override
     @Transactional
     public void sendPhoneVerificationOtp(String email, String phone) {
         String normalizedPhone = phone.trim();

         User user = findByEmailOrThrow(email);

         // Check phone not already taken by a different account
         userRepo.findByPhone(normalizedPhone).ifPresent(existing -> {
             if (!existing.getId().equals(user.getId())) {
                 throw new BadRequestException("This phone number is already registered with another account.");
             }
         });

         // Send OTP to the phone number
         otpService.generateAndSendOtp(normalizedPhone);
         log.info("Phone OTP sent: userId={}, phone={}", user.getId(), normalizedPhone);
     }

     @Override
     @Transactional
     public void verifyAndSavePhone(String email, String phone, String otp) {
         String normalizedPhone = phone.trim();

         // Verify the OTP that was sent to this phone
         otpService.verifyOtp(normalizedPhone, otp);

         User user = findByEmailOrThrow(email);

         // Final uniqueness check
         userRepo.findByPhone(normalizedPhone).ifPresent(existing -> {
             if (!existing.getId().equals(user.getId())) {
                 throw new BadRequestException("This phone number is already registered with another account.");
             }
         });

         user.setPhone(normalizedPhone);
         user.setIsPhoneVerified(true);
         userRepo.save(user);
         log.info("Phone verified and saved: userId={}, phone={}", user.getId(), normalizedPhone);
     }

 // ════════════════════════════════════════════════════════════
 // END OF METHODS TO ADD
 // ════════════════════════════════════════════════════════════

    // ── Private helpers ───────────────────────────────────────────────────────

    private String generateUniqueReferralCode() {
        String code;
        int attempts = 0;
        do {
            byte[] bytes = new byte[3];
            SECURE_RANDOM.nextBytes(bytes);
            code = referralPrefix + HexFormat.of().formatHex(bytes).toUpperCase();
            attempts++;
            if (attempts > 10)
                throw new IllegalStateException("Could not generate unique referral code after 10 attempts");
        } while (userRepo.existsByReferralCode(code));
        return code;
    }

    private User findByEmailOrThrow(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private <T> PageResponse<T> buildPage(List<T> content, Page<?> page) {
        return PageResponse.<T>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
