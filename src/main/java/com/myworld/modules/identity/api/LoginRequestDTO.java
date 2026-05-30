package com.myworld.modules.identity.api;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class LoginRequestDTO {
    @NotBlank private String username;  // email or phone
    @NotBlank private String password;
}
