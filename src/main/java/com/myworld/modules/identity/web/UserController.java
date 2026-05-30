package com.myworld.modules.identity.web;

import com.myworld.core.dto.ApiResponse;
import com.myworld.core.exception.BadRequestException;
import com.myworld.core.security.CustomUserDetails;
import com.myworld.modules.identity.api.*;
import com.myworld.modules.identity.application.MfaService;
import com.myworld.modules.identity.application.UserService;
import com.myworld.modules.identity.application.DashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService      userService;
    private final DashboardService dashboardService;
    private final MfaService       mfaService;

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public ApiResponse<UserDashboardDTO> getDashboard(Authentication auth) {
        return ApiResponse.success(
                dashboardService.getDashboardData(auth.getName()),
                "Dashboard fetched successfully");
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    @GetMapping("/me")
    public ApiResponse<UserResponseDTO> getMyProfile(Authentication auth) {
        return ApiResponse.success(userService.getCurrentUserByEmail(auth.getName()), "Profile fetched");
    }

    @PutMapping("/me")
    public ApiResponse<UserResponseDTO> updateProfile(@Valid @RequestBody UserProfileUpdateDTO dto,
                                                      Authentication auth) {
        return ApiResponse.success(
                userService.updateProfile(auth.getName(), dto), "Profile updated");
    }

    // ── Password ──────────────────────────────────────────────────────────────

    @PostMapping("/change-password")
    public ApiResponse<String> changePassword(@Valid @RequestBody ChangePasswordDTO dto,
                                              Authentication auth) {
        userService.changePassword(auth.getName(), dto);
        return ApiResponse.success("Password changed successfully", "Done");
    }

    // ── Email Verification ────────────────────────────────────────────────────

    @PostMapping("/send-email-verification")
    public ApiResponse<String> sendEmailOtp(Authentication auth) {
        userService.sendEmailVerification(auth.getName());
        return ApiResponse.success("OTP sent to your email", "OTP Sent");
    }

    @PostMapping("/verify-email")
    public ApiResponse<String> verifyEmail(@RequestBody Map<String, String> payload,
                                           Authentication auth) {
        String otp = payload.get("otp");
        if (otp == null || otp.trim().isEmpty()) {
            throw new BadRequestException("OTP is required");
        }
        userService.verifyEmail(new VerifyOtpDTO(auth.getName(), otp));
        return ApiResponse.success("Email verified successfully", "Verified");
    }

   
   

    // ── MFA Settings ─────────────────────────────────────────────────────────

    /**
     * Get current MFA settings for the authenticated user.
     *
     * Response:
     * {
     *   "mfaEnabled": true,
     *   "emailOtpEnabled": true,
     *   "mobileOtpEnabled": false,
     *   "totpEnabled": true,
     *   "totpVerified": true
     * }
     */
    @GetMapping("/mfa/settings")
    public ApiResponse<MfaSettingsResponse> getMfaSettings(Authentication auth) {
        Long userId = getUserId(auth);
        return ApiResponse.success(mfaService.getMfaSettings(userId), "MFA settings fetched");
    }

    /**
     * Update MFA settings — turn MFA on/off and choose which methods to require.
     *
     * Rules enforced by MfaService:
     *   - If mfaEnabled=true, at least one method flag must be true.
     *   - totpEnabled=true requires TOTP to be set up first (/mfa/totp/setup → /mfa/totp/verify-setup).
     *   - Setting mfaEnabled=false disables all methods regardless of individual flags.
     *
     * Example payloads:
     *   Email only:          { "mfaEnabled": true, "emailOtpEnabled": true }
     *   Mobile only:         { "mfaEnabled": true, "mobileOtpEnabled": true }
     *   TOTP only:           { "mfaEnabled": true, "totpEnabled": true }
     *   Email + TOTP:        { "mfaEnabled": true, "emailOtpEnabled": true, "totpEnabled": true }
     *   All three:           { "mfaEnabled": true, "emailOtpEnabled": true, "mobileOtpEnabled": true, "totpEnabled": true }
     *   Disable MFA:         { "mfaEnabled": false }
     */
    @PutMapping("/mfa/settings")
    public ApiResponse<String> updateMfaSettings(@Valid @RequestBody MfaSettingsRequest request,
                                                  Authentication auth) {
        Long userId = getUserId(auth);
        mfaService.updateMfaSettings(userId, request);
        return ApiResponse.success("MFA settings updated", "Done");
    }

    /**
     * Step 1 of Google Authenticator setup.
     * Returns a QR code (base64 PNG data URI) and a manual entry key.
     *
     * Frontend: render <img src="{qrDataUri}"> and ask user to scan with Google Authenticator.
     * Then call POST /mfa/totp/verify-setup with the 6-digit code shown in the app.
     */
    @PostMapping("/mfa/totp/setup")
    public ApiResponse<TotpSetupResponse> initiateTotpSetup(Authentication auth) {
        Long userId = getUserId(auth);
        return ApiResponse.success(mfaService.initiateTotpSetup(userId), "Scan the QR code with Google Authenticator, then verify.");
    }

    /**
     * Step 2 of Google Authenticator setup.
     * User submits the 6-digit code shown in their authenticator app.
     * On success, TOTP is marked as verified and can be enabled in MFA settings.
     */
    @PostMapping("/mfa/totp/verify-setup")
    public ApiResponse<String> verifyTotpSetup(@RequestBody Map<String, String> payload,
                                                Authentication auth) {
        Long userId = getUserId(auth);
        String code = payload.get("code");
        if (code == null || code.trim().isEmpty()) {
            throw new BadRequestException("Authenticator code is required");
        }
        mfaService.verifyAndActivateTotp(userId, code.trim());
        return ApiResponse.success(
                "Google Authenticator linked successfully. You can now enable it in MFA settings.",
                "TOTP activated");
    }

    /**
     * Remove Google Authenticator from the account.
     * Wipes the TOTP secret. User must go through setup again to re-enable.
     */
    @DeleteMapping("/mfa/totp")
    public ApiResponse<String> disableTotp(Authentication auth) {
        Long userId = getUserId(auth);
        mfaService.disableTotp(userId);
        return ApiResponse.success("Google Authenticator removed from your account.", "Done");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Long getUserId(Authentication auth) {
        CustomUserDetails principal = (CustomUserDetails) auth.getPrincipal();
        return principal.getId();
    }
    
 // ============================================================
 // FILE: src/main/java/com/myworld/modules/identity/web/UserController.java
 // ADD THESE ENDPOINTS inside the class (after the existing /verify-email endpoint)
 // ============================================================

     // ── Email Update ──────────────────────────────────────────────────────────

     /**
      * Step 1: Send OTP to the NEW email address.
      * Body: { "newEmail": "newemail@example.com" }
      */
     @PostMapping("/send-email-update-otp")
     public ApiResponse<String> sendEmailUpdateOtp(@RequestBody Map<String, String> payload,
                                                    Authentication auth) {
         String newEmail = payload.get("newEmail");
         if (newEmail == null || newEmail.trim().isEmpty()) {
             throw new BadRequestException("New email is required");
         }
         userService.sendEmailUpdateOtp(auth.getName(), newEmail.trim());
         return ApiResponse.success("OTP sent to " + newEmail.trim(), "OTP Sent");
     }

     /**
      * Step 2: Verify OTP and update email.
      * Body: { "newEmail": "newemail@example.com", "otp": "123456" }
      */
     @PostMapping("/verify-and-update-email")
     public ApiResponse<UserResponseDTO> verifyAndUpdateEmail(@RequestBody Map<String, String> payload,
                                                               Authentication auth) {
         String newEmail = payload.get("newEmail");
         String otp      = payload.get("otp");
         if (newEmail == null || newEmail.trim().isEmpty())
             throw new BadRequestException("New email is required");
         if (otp == null || otp.trim().isEmpty())
             throw new BadRequestException("OTP is required");
         userService.verifyAndUpdateEmail(auth.getName(), newEmail.trim(), otp.trim());
         return ApiResponse.success(
             userService.getCurrentUserByEmail(newEmail.trim().toLowerCase()),
             "Email updated successfully");
     }

     // ── Phone Add / Update + Verification ────────────────────────────────────

     /**
      * Step 1: Send OTP to the phone number.
      * Body: { "phone": "9876543210" }
      */
     @PostMapping("/send-phone-otp")
     public ApiResponse<String> sendPhoneOtp(@RequestBody Map<String, String> payload,
                                              Authentication auth) {
         String phone = payload.get("phone");
         if (phone == null || phone.trim().isEmpty())
             throw new BadRequestException("Phone number is required");
         userService.sendPhoneVerificationOtp(auth.getName(), phone.trim());
         return ApiResponse.success("OTP sent to " + phone.trim(), "OTP Sent");
     }

     /**
      * Step 2: Verify OTP and save phone as verified.
      * Body: { "phone": "9876543210", "otp": "123456" }
      */
     @PostMapping("/verify-phone")
     public ApiResponse<UserResponseDTO> verifyPhone(@RequestBody Map<String, String> payload,
                                                      Authentication auth) {
         String phone = payload.get("phone");
         String otp   = payload.get("otp");
         if (phone == null || phone.trim().isEmpty())
             throw new BadRequestException("Phone number is required");
         if (otp == null || otp.trim().isEmpty())
             throw new BadRequestException("OTP is required");
         userService.verifyAndSavePhone(auth.getName(), phone.trim(), otp.trim());
         // After phone update, fetch fresh user by email (auth.getName() is still email)
         return ApiResponse.success(
             userService.getCurrentUserByEmail(auth.getName()),
             "Phone verified successfully");
     }

 // ============================================================
 // ALSO ADD these imports at top of UserController.java if missing:
 // import java.util.Map;   ← already exists in original file
 // ============================================================
}