package com.myworld.modules.identity.application;

import com.myworld.core.exception.BadRequestException;
import com.myworld.core.exception.ResourceNotFoundException;
import com.myworld.core.security.JwtUtil;
import com.myworld.core.security.LoginAttemptService;
import com.myworld.modules.identity.api.*;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.identity.mapper.UserMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserServiceImpl — Unit Tests")
class UserServiceImplTest {

    @Mock UserRepository           userRepo;
    @Mock PasswordEncoder          passwordEncoder;
    @Mock RefreshTokenService      refreshTokenService;
    @Mock JwtUtil                  jwtUtil;
    @Mock LoginAttemptService      loginAttemptService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock UserMapper               userMapper;
    @Mock OtpService               otpService;
    @Mock MfaService               mfaService;

    @InjectMocks UserServiceImpl service;

    private User testUser;
    private UserRequestDTO validDto;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "referralPrefix", "EX-");

        testUser = User.builder()
                .email("test@earnx.com").name("Test User")
                .password("$2a$encoded").phone("9876543210")
                .build();
        testUser.setId(1L);
        testUser.setIsEmailVerified(false);
        testUser.setIsBlocked(false);
        testUser.setIsDeleted(false);

        validDto = UserRequestDTO.builder()
                .name("Test User").email("test@earnx.com")
                .phone("9876543210").password("rawPassword123").build();
    }

    // ── createUser ────────────────────────────────────────────────────────────

    @Nested @DisplayName("createUser()")
    class CreateUser {

        @Test @DisplayName("happy path — saves user and publishes event")
        void happyPath_savesAndPublishes() {
            when(userRepo.existsByEmailIncludingDeleted("test@earnx.com")).thenReturn(false);
            when(userRepo.existsByPhoneIncludingDeleted("9876543210")).thenReturn(false);
            when(userRepo.existsByReferralCode(anyString())).thenReturn(false);
            when(userMapper.toEntity(validDto)).thenReturn(testUser);
            when(passwordEncoder.encode("rawPassword123")).thenReturn("$2a$encoded");
            when(userRepo.save(any(User.class))).thenReturn(testUser);
            when(userMapper.toDTO(testUser)).thenReturn(
                    UserResponseDTO.builder().id(1L).email("test@earnx.com").build());

            UserResponseDTO result = service.createUser(validDto);

            assertThat(result.getEmail()).isEqualTo("test@earnx.com");
            verify(userRepo).save(any(User.class));
            verify(eventPublisher).publishEvent(any());
        }

        @Test @DisplayName("duplicate email → BadRequestException")
        void duplicateEmail_throws() {
            when(userRepo.existsByEmailIncludingDeleted("test@earnx.com")).thenReturn(true);

            assertThatThrownBy(() -> service.createUser(validDto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Email already registered");
        }

        @Test @DisplayName("duplicate phone → BadRequestException")
        void duplicatePhone_throws() {
            when(userRepo.existsByEmailIncludingDeleted("test@earnx.com")).thenReturn(false);
            when(userRepo.existsByPhoneIncludingDeleted("9876543210")).thenReturn(true);

            assertThatThrownBy(() -> service.createUser(validDto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Phone number already registered");
        }
    }

    // ── forgotPassword ────────────────────────────────────────────────────────

    @Nested @DisplayName("forgotPassword()")
    class ForgotPassword {

        @Test @DisplayName("known email → OTP sent")
        void knownEmail_sendsOtp() {
            ForgotPasswordDTO dto = new ForgotPasswordDTO("test@earnx.com");
            when(userRepo.findByEmailOrPhone("test@earnx.com", "test@earnx.com"))
                    .thenReturn(Optional.of(testUser));
            doNothing().when(otpService).generateAndSendOtp(anyString());

            assertThatCode(() -> service.forgotPassword(dto)).doesNotThrowAnyException();
            verify(otpService).generateAndSendOtp(anyString());
        }

        @Test @DisplayName("unknown identifier → still succeeds (security: no user enumeration)")
        void unknownIdentifier_noException() {
            ForgotPasswordDTO dto = new ForgotPasswordDTO("unknown@earnx.com");
            when(userRepo.findByEmailOrPhone("unknown@earnx.com", "unknown@earnx.com"))
                    .thenReturn(Optional.empty());

            // Should not throw — prevents user enumeration attacks
            assertThatCode(() -> service.forgotPassword(dto)).doesNotThrowAnyException();
        }
    }

    // ── sendEmailUpdateOtp ────────────────────────────────────────────────────

    @Nested @DisplayName("sendEmailUpdateOtp()")
    class SendEmailUpdateOtp {

        @Test @DisplayName("new email not taken → OTP sent to new email")
        void newEmailFree_sendsOtp() {
            when(userRepo.existsByEmailIncludingDeleted("new@earnx.com")).thenReturn(false);
            when(userRepo.findByEmail("test@earnx.com")).thenReturn(Optional.of(testUser));
            doNothing().when(otpService).generateAndSendOtp("new@earnx.com");

            assertThatCode(() -> service.sendEmailUpdateOtp("test@earnx.com", "new@earnx.com"))
                    .doesNotThrowAnyException();
            verify(otpService).generateAndSendOtp("new@earnx.com");
        }

        @Test @DisplayName("new email already taken → BadRequestException")
        void newEmailTaken_throws() {
            when(userRepo.existsByEmailIncludingDeleted("taken@earnx.com")).thenReturn(true);

            assertThatThrownBy(() -> service.sendEmailUpdateOtp("test@earnx.com", "taken@earnx.com"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already registered");
        }

        @Test @DisplayName("new email same as current → BadRequestException")
        void sameEmail_throws() {
            when(userRepo.existsByEmailIncludingDeleted("test@earnx.com")).thenReturn(false);
            when(userRepo.findByEmail("test@earnx.com")).thenReturn(Optional.of(testUser));

            assertThatThrownBy(() -> service.sendEmailUpdateOtp("test@earnx.com", "test@earnx.com"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("same as your current email");
        }
    }

    // ── sendPhoneVerificationOtp ──────────────────────────────────────────────

    @Nested @DisplayName("sendPhoneVerificationOtp()")
    class SendPhoneVerificationOtp {

        @Test @DisplayName("new phone not taken → OTP sent")
        void newPhone_sendsOtp() {
            when(userRepo.findByEmail("test@earnx.com")).thenReturn(Optional.of(testUser));
            when(userRepo.findByPhone("9999999999")).thenReturn(Optional.empty());
            doNothing().when(otpService).generateAndSendOtp("9999999999");

            assertThatCode(() -> service.sendPhoneVerificationOtp("test@earnx.com", "9999999999"))
                    .doesNotThrowAnyException();
            verify(otpService).generateAndSendOtp("9999999999");
        }

        @Test @DisplayName("phone taken by other user → BadRequestException")
        void phoneTakenByOther_throws() {
            User otherUser = User.builder().email("other@earnx.com").name("Other")
                    .password("x").build();
            otherUser.setId(99L);

            when(userRepo.findByEmail("test@earnx.com")).thenReturn(Optional.of(testUser));
            when(userRepo.findByPhone("9999999999")).thenReturn(Optional.of(otherUser));

            assertThatThrownBy(() -> service.sendPhoneVerificationOtp("test@earnx.com", "9999999999"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already registered with another account");
        }
    }
}
