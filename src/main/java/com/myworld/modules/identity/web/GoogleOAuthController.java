package com.myworld.modules.identity.web;

// ============================================================
// FILE: src/main/java/com/myworld/modules/identity/web/GoogleOAuthController.java
// ============================================================

import com.myworld.core.dto.ApiResponse;
import com.myworld.modules.identity.api.LoginResponseDTO;
import com.myworld.modules.identity.application.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class GoogleOAuthController {

    private final GoogleOAuthService googleOAuthService;

    /**
     * POST /api/auth/google
     *
     * Frontend sends the Google ID Token received from Google Sign-In.
     * Backend verifies it, creates/finds user, returns JWT tokens.
     *
     * Body: { "credential": "<google_id_token>" }
     */
    @PostMapping("/google")
    public ApiResponse<LoginResponseDTO> googleLogin(@RequestBody GoogleLoginRequest request) {
        LoginResponseDTO response = googleOAuthService.loginWithGoogle(request.getCredential());
        return ApiResponse.success(response, "Google login successful");
    }

    // Simple inner DTO (or you can make a separate file)
    public static class GoogleLoginRequest {
        private String credential;
        public String getCredential() { return credential; }
        public void setCredential(String credential) { this.credential = credential; }
    }
}