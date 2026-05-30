package com.myworld.modules.identity.web;

import com.myworld.core.dto.ApiResponse;
import com.myworld.modules.identity.api.*;
import com.myworld.modules.identity.application.MfaService;
import com.myworld.modules.identity.application.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final MfaService  mfaService;

    // ── Registration ──────────────────────────────────────────────────────────

    @PostMapping("/register")
    public ApiResponse<UserResponseDTO> register(@Valid @RequestBody UserRequestDTO dto) {
        return ApiResponse.success(userService.createUser(dto), "Registration successful");
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Step 1: Password verification.
     *
     * Response A (MFA disabled):
     *   { mfaRequired: false, accessToken: "...", refreshToken: "...", user: {...} }
     *
     * Response B (MFA enabled):
     *   { mfaRequired: true, mfaChallenge: { sessionToken, emailRequired, mobileRequired,
     *     totpRequired, maskedEmail, maskedPhone } }
     *   → Frontend renders MFA steps and calls the /mfa/* endpoints below.
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto,
                                               HttpServletRequest request) {
        return ApiResponse.success(userService.login(dto, request), "Login successful");
    }

    // ── MFA Step Verification ─────────────────────────────────────────────────

    /**
     * Step 2a: Verify email OTP received during MFA challenge.
     * Call this if mfaChallenge.emailRequired == true.
     */
    @PostMapping("/mfa/verify-email-otp")
    public ApiResponse<String> verifyMfaEmailOtp(@Valid @RequestBody MfaOtpVerifyRequest req) {
        mfaService.verifyMfaEmailOtp(req.getSessionToken(), req.getOtp());
        return ApiResponse.success("Email OTP verified", "Step complete");
    }

    /**
     * Step 2b: Verify mobile OTP received during MFA challenge.
     * Call this if mfaChallenge.mobileRequired == true.
     * (Mobile OTP delivery is mocked — check server logs in dev.)
     */
    @PostMapping("/mfa/verify-mobile-otp")
    public ApiResponse<String> verifyMfaMobileOtp(@Valid @RequestBody MfaOtpVerifyRequest req) {
        mfaService.verifyMfaMobileOtp(req.getSessionToken(), req.getOtp());
        return ApiResponse.success("Mobile OTP verified", "Step complete");
    }

    /**
     * Step 2c: Verify TOTP code from Google Authenticator.
     * Call this if mfaChallenge.totpRequired == true.
     */
    @PostMapping("/mfa/verify-totp")
    public ApiResponse<String> verifyMfaTotp(@Valid @RequestBody MfaTotpVerifyRequest req) {
        mfaService.verifyMfaTotp(req.getSessionToken(), req.getCode());
        return ApiResponse.success("Authenticator code verified", "Step complete");
    }

    /**
     * Step 3: Complete MFA login after all required steps are verified.
     * Returns real access + refresh tokens.
     */
    @PostMapping("/mfa/complete")
    public ApiResponse<AuthResponseDTO> completeMfaLogin(@RequestBody MfaCompleteRequest req,
                                                         HttpServletRequest request) {
        return ApiResponse.success(
                userService.completeMfaLogin(req.getSessionToken(), request),
                "Login successful");
    }

    // ── Token management ──────────────────────────────────────────────────────

    @PostMapping("/refresh-token")
    public ApiResponse<AuthResponseDTO> refresh(@Valid @RequestBody RefreshTokenRequestDTO dto) {
        return ApiResponse.success(userService.refreshToken(dto.getRefreshToken()), "Token refreshed");
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout(Authentication auth) {
        userService.logout(auth.getName());
        return ApiResponse.success("Logged out successfully", "Logout");
    }

    // ── Password ──────────────────────────────────────────────────────────────

    @PostMapping("/forgot-password")
    public ApiResponse<String> forgotPassword(@Valid @RequestBody ForgotPasswordDTO dto) {
        userService.forgotPassword(dto);
        return ApiResponse.success(
                "If an account exists for that identifier, a password reset OTP will be sent.",
                "OTP Request Accepted");
    }

    @PostMapping("/reset-password")
    public ApiResponse<String> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        userService.resetPassword(dto);
        return ApiResponse.success("Password reset successfully", "Password Reset");
    }
}
