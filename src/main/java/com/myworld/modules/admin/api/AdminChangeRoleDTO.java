package com.myworld.modules.admin.api;

import com.myworld.core.constant.Role;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AdminChangeRoleDTO {
    @NotNull(message = "Role is required")
    private Role role;
}
