package com.myworld.modules.admin.api;

import com.myworld.core.constant.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AdminUserActionDTO {
    private String reason;  // for block/reject
    private String notes;   // admin notes
}
