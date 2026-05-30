package com.myworld.modules.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ChangePasswordDTO {
    @NotBlank private String oldPassword;
    @NotBlank @Size(min = 8) private String newPassword;
}
