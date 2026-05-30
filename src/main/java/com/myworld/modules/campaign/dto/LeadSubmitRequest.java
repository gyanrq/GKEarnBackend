package com.myworld.modules.campaign.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeadSubmitRequest {
    @NotNull(message = "Campaign ID is required")
    private Long campaignId;
    private String registeredMobile;
    private String registeredEmail;
    private String proofUrl;
}
