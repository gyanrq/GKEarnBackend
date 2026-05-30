package com.myworld.modules.tasks.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request body for POST /api/tasks/submit-lead
 * User must provide email + mobile before partner URL is unlocked.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskLeadSubmitDTO {

    @NotBlank(message = "taskType is required")
    private String taskType;

    @NotBlank(message = "Email is required")
    @Email(message = "Valid email required")
    private String email;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Valid 10-digit Indian mobile number required")
    private String mobile;
}