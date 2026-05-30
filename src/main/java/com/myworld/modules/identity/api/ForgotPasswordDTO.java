package com.myworld.modules.identity.api;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ForgotPasswordDTO {
    @NotBlank private String identifier;  // email or phone
}
