package com.myworld.modules.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder

public class ResetPasswordDTO {
    @NotBlank private String identifier;
    @NotBlank private String otp;
    @NotBlank @Size(min = 8) private String newPassword;
}
