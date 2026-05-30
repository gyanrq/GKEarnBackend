package com.myworld.modules.identity.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myworld.core.exception.BadRequestException;
import com.myworld.core.exception.GlobalExceptionHandler;
import com.myworld.modules.identity.api.*;
import com.myworld.modules.identity.application.MfaService;
import com.myworld.modules.identity.application.UserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthController — Unit Tests")
class AuthControllerTest {

    @Mock UserService userService;
    @Mock MfaService  mfaService;

    @InjectMocks AuthController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @Nested @DisplayName("POST /api/auth/register")
    class Register {

        @Test @DisplayName("valid request → 200 + user response")
        void validRequest_returns200() throws Exception {
            UserRequestDTO dto = UserRequestDTO.builder()
                    .name("Test User").email("test@earnx.com")
                    .phone("9876543210").password("password123").build();

            UserResponseDTO response = UserResponseDTO.builder()
                    .id(1L).name("Test User").email("test@earnx.com").build();

            when(userService.createUser(any(UserRequestDTO.class))).thenReturn(response);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.email").value("test@earnx.com"));
        }

        @Test @DisplayName("duplicate email → 400")
        void duplicateEmail_returns400() throws Exception {
            UserRequestDTO dto = UserRequestDTO.builder()
                    .name("Test User").email("dup@earnx.com")
                    .phone("9876543210").password("password123").build();
            when(userService.createUser(any())).thenThrow(new BadRequestException("Email already registered"));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested @DisplayName("POST /api/auth/login")
    class Login {

        @Test @DisplayName("valid credentials, no MFA → returns token")
        void validCredentials_noMfa_returnsToken() throws Exception {
            LoginRequestDTO dto = new LoginRequestDTO("user@earnx.com", "password123");

            LoginResponseDTO response = LoginResponseDTO.builder()
                    .mfaRequired(false).accessToken("jwt-token-here")
                    .refreshToken("refresh-here")
                    .user(UserResponseDTO.builder().email("user@earnx.com").build()).build();

            when(userService.login(any(LoginRequestDTO.class), any())).thenReturn(response);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.mfaRequired").value(false))
                    .andExpect(jsonPath("$.data.accessToken").value("jwt-token-here"));
        }

        @Test @DisplayName("MFA enabled → returns mfaChallenge")
        void mfaEnabled_returnsMfaChallenge() throws Exception {
            LoginRequestDTO dto = new LoginRequestDTO("user@earnx.com", "password123");
            MfaChallengeResponse challenge = MfaChallengeResponse.builder()
                    .sessionToken("session-abc").emailRequired(true).build();
            LoginResponseDTO response = LoginResponseDTO.builder()
                    .mfaRequired(true).mfaChallenge(challenge).build();
            when(userService.login(any(), any())).thenReturn(response);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.mfaRequired").value(true))
                    .andExpect(jsonPath("$.data.mfaChallenge.sessionToken").value("session-abc"));
        }

        @Test @DisplayName("wrong password → 400")
        void wrongPassword_returns400() throws Exception {
            LoginRequestDTO dto = new LoginRequestDTO("user@earnx.com", "wrongpass");
            when(userService.login(any(), any())).thenThrow(new BadRequestException("Invalid credentials"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("rate-limited user → 400")
        void rateLimited_returns400() throws Exception {
            LoginRequestDTO dto = new LoginRequestDTO("blocked@earnx.com", "pass");
            when(userService.login(any(), any()))
                    .thenThrow(new BadRequestException("Too many attempts. Try again later."));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested @DisplayName("POST /api/auth/refresh-token")
    class RefreshToken {

        @Test @DisplayName("valid refresh token → returns new access token")
        void validToken_returnsNewAccess() throws Exception {
            RefreshTokenRequestDTO dto = new RefreshTokenRequestDTO("valid-refresh-token");
            AuthResponseDTO response = AuthResponseDTO.builder()
                    .accessToken("new-access-token").refreshToken("valid-refresh-token").build();
            when(userService.refreshToken("valid-refresh-token")).thenReturn(response);

            mockMvc.perform(post("/api/auth/refresh-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
        }
    }

    @Nested @DisplayName("MFA endpoints")
    class MfaEndpoints {

        @Test @DisplayName("verify-email-otp → 200")
        void verifyEmailOtp_200() throws Exception {
            MfaOtpVerifyRequest req = new MfaOtpVerifyRequest("session-token", "123456");
            doNothing().when(mfaService).verifyMfaEmailOtp("session-token", "123456");

            mockMvc.perform(post("/api/auth/mfa/verify-email-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("mfa/complete → returns full auth tokens")
        void mfaComplete_returnsTokens() throws Exception {
            MfaCompleteRequest req = new MfaCompleteRequest("session-token");
            AuthResponseDTO response = AuthResponseDTO.builder().accessToken("final-token").build();
            when(userService.completeMfaLogin(eq("session-token"), any())).thenReturn(response);

            mockMvc.perform(post("/api/auth/mfa/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("final-token"));
        }
    }

    @Nested @DisplayName("Password reset flow")
    class PasswordReset {

        @Test @DisplayName("forgot-password → 200")
        void forgotPassword_returns200() throws Exception {
            ForgotPasswordDTO dto = new ForgotPasswordDTO("user@earnx.com");
            doNothing().when(userService).forgotPassword(any());

            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("reset-password → 200 on success")
        void resetPassword_returns200() throws Exception {
            ResetPasswordDTO dto = ResetPasswordDTO.builder()
                    .identifier("user@earnx.com").otp("123456")
                    .newPassword("newSecurePass123").build();
            doNothing().when(userService).resetPassword(any());

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
        }
    }
}
