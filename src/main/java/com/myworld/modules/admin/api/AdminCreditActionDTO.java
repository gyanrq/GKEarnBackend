package com.myworld.modules.admin.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AdminCreditActionDTO {

    @NotNull(message = "Credits amount is required")
    @Min(value = 1, message = "Credits must be at least 1")
    private Long credits;

    @NotBlank(message = "Reason is required")
    private String reason;
}