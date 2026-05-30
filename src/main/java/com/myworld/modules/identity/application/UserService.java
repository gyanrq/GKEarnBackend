package com.myworld.modules.identity.application;

// ============================================================
// FILE: src/main/java/com/myworld/modules/identity/application/UserService.java
// CHANGES: Added phone + email update/verify methods
// ============================================================

import com.myworld.core.dto.PageResponse;
import com.myworld.modules.identity.api.*;
import jakarta.servlet.http.HttpServletRequest;

public interface UserService {

    // ── Auth ────────────────────────────────────────────────────────────────
    UserResponseDTO createUser(UserRequestDTO dto);
    LoginResponseDTO login(LoginRequestDTO dto, HttpServletRequest request);
    AuthResponseDTO completeMfaLogin(String sessionToken, HttpServletRequest request);
    AuthResponseDTO refreshToken(String refreshToken);
    void logout(String email);

    // ── Profile ─────────────────────────────────────────────────────────────
    UserResponseDTO getCurrentUserByEmail(String email);
    UserResponseDTO updateProfile(String email, UserProfileUpdateDTO dto);
    UserResponseDTO getUser(Long id);
    PageResponse<UserResponseDTO> getAllUsers(int page, int size);

    // ── Password / IAM ──────────────────────────────────────────────────────
    void changePassword(String email, ChangePasswordDTO dto);
    void forgotPassword(ForgotPasswordDTO dto);
    void resetPassword(ResetPasswordDTO dto);

    // ── Email Verification ──────────────────────────────────────────────────
    void sendEmailVerification(String email);
    void verifyEmail(VerifyOtpDTO dto);

    // ── Email Update (authenticated) ────────────────────────────────────────
    /** Step 1: User enters new email → OTP sent to that new email */
    void sendEmailUpdateOtp(String currentEmail, String newEmail);
    /** Step 2: User enters OTP → email updated and verified */
    void verifyAndUpdateEmail(String currentEmail, String newEmail, String otp);

    // ── Phone Add/Update + Verification ────────────────────────────────────
    /** Step 1: User enters phone number → OTP sent to that phone */
    void sendPhoneVerificationOtp(String email, String phone);
    /** Step 2: User enters OTP → phone saved and marked verified */
    void verifyAndSavePhone(String email, String phone, String otp);
}