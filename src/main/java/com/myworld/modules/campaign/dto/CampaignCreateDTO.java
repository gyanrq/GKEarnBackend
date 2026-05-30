package com.myworld.modules.campaign.dto;

import com.myworld.modules.campaign.domain.CampaignType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CampaignCreateDTO {
    @NotBlank  private String name;
    @NotBlank  private String trackingLink;
    @NotNull   private CampaignType campaignType;
    @NotNull @DecimalMin("0.01") private BigDecimal rewardAmount;
    private String description;
    private String termsUrl;
    private String logoUrl;
    private String advertiserName;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private Integer maxLeadsPerUser;
    private Integer totalLeadsCap;
}
