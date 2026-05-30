package com.myworld.modules.identity.api;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MfaOtpVerifyRequest {
    @NotBlank private String sessionToken;
    @NotBlank private String otp;
}