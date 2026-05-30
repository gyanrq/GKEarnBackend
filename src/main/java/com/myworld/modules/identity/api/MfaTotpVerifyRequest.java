package com.myworld.modules.identity.api;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MfaTotpVerifyRequest {
    @NotBlank private String sessionToken;
    @NotBlank private String code;
}