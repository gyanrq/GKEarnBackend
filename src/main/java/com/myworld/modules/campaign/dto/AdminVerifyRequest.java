package com.myworld.modules.campaign.dto;

import com.myworld.modules.campaign.domain.LeadStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AdminVerifyRequest {
    @NotNull
    private LeadStatus status;   // APPROVED or REJECTED
    private String notes;
    private String rejectionReason;
}
