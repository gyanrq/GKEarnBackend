package com.myworld.modules.identity.application;

import com.myworld.core.exception.BadRequestException;
import com.myworld.modules.identity.api.VerifyOtpDTO;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.referral.application.EventProcessorService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for verifyEmail() referral chain integration.
 *
 * These tests protect the CRITICAL fix:
 *   verifyEmail() must call eventProcessorService.processKycVerified()
 *   on first-time email verification, so referrers receive their credits.
 *
 * If someone refactors UserServiceImpl and removes this call, these tests
 * will immediately fail — the regression cannot be silent.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserServiceImpl — verifyEmail() referral chain")
class UserServiceImplVerifyEmailTest {

    @Mock private UserRepository         userRepo;
    @Mock private OtpService             otpService;
    @Mock private EventProcessorService  eventProcessorService;
    @Mock private MfaService             mfaService;

    // Remaining deps needed by @InjectMocks but not used in these tests
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock private com.myworld.core.security.LoginAttemptService loginAttemptService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private com.myworld.modules.identity.mapper.UserMapper userMapper;
    @Mock private com.myworld.modules.identity.application.RefreshTokenService refreshTokenService;
    @Mock private com.myworld.core.security.JwtUtil jwtUtil;

    @InjectMocks
    private UserServiceImpl service;

    private User unverifiedUser;
    private User verifiedUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "referralPrefix", "EX-");

        unverifiedUser = User.builder()
                .email("new@earnx.com").name("New User").password("hashed").build();
        ReflectionTestUtils.setField(unverifiedUser, "id", 10L);
        unverifiedUser.setIsEmailVerified(false);

        verifiedUser = User.builder()
                .email("already@earnx.com").name("Old User").password("hashed").build();
        ReflectionTestUtils.setField(verifiedUser, "id", 20L);
        verifiedUser.setIsEmailVerified(true);
    }

    @Test
    @DisplayName("CRITICAL: processKycVerified called on first-time email verification")
    void firstTimeVerification_triggersReferralChain() {
        when(userRepo.findByEmail("new@earnx.com"))
                .thenReturn(Optional.of(unverifiedUser));
        doNothing().when(otpService).verifyOtp(any(), any());

        service.verifyEmail(new VerifyOtpDTO("new@earnx.com", "123456"));

        verify(eventProcessorService).processKycVerified(10L);
    }

    @Test
    @DisplayName("CRITICAL: processKycVerified NOT called if already verified (no double-trigger)")
    void alreadyVerified_doesNotTriggerChainAgain() {
        when(userRepo.findByEmail("already@earnx.com"))
                .thenReturn(Optional.of(verifiedUser));
        doNothing().when(otpService).verifyOtp(any(), any());

        service.verifyEmail(new VerifyOtpDTO("already@earnx.com", "123456"));

        verify(eventProcessorService, never()).processKycVerified(anyLong());
    }

    @Test
    @DisplayName("isEmailVerified set to true after verification")
    void setsEmailVerifiedTrue() {
        when(userRepo.findByEmail("new@earnx.com"))
                .thenReturn(Optional.of(unverifiedUser));
        doNothing().when(otpService).verifyOtp(any(), any());

        service.verifyEmail(new VerifyOtpDTO("new@earnx.com", "123456"));

        assertThat(unverifiedUser.getIsEmailVerified()).isTrue();
        verify(userRepo).save(unverifiedUser);
    }

    @Test
    @DisplayName("invalid OTP — exception thrown, no state changed, referral chain NOT triggered")
    void invalidOtp_throwsAndNoSideEffects() {
        when(userRepo.findByEmail("new@earnx.com"))
                .thenReturn(Optional.of(unverifiedUser));
        doThrow(new BadRequestException("Invalid OTP"))
                .when(otpService).verifyOtp(any(), any());

        assertThatThrownBy(() -> service.verifyEmail(new VerifyOtpDTO("new@earnx.com", "000000")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid OTP");

        assertThat(unverifiedUser.getIsEmailVerified()).isFalse();
        verify(eventProcessorService, never()).processKycVerified(anyLong());
        verify(userRepo, never()).save(any());
    }

    @Test
    @DisplayName("processKycVerified called with correct userId (not hardcoded)")
    void correctUserIdPassed_toReferralChain() {
        User anotherUser = User.builder()
                .email("user99@earnx.com").name("User 99").password("x").build();
        ReflectionTestUtils.setField(anotherUser, "id", 99L);
        anotherUser.setIsEmailVerified(false);

        when(userRepo.findByEmail("user99@earnx.com"))
                .thenReturn(Optional.of(anotherUser));
        doNothing().when(otpService).verifyOtp(any(), any());

        service.verifyEmail(new VerifyOtpDTO("user99@earnx.com", "654321"));

        verify(eventProcessorService).processKycVerified(99L);
    }
}
