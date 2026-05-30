package com.myworld.modules.campaign.dto;

import com.myworld.modules.campaign.domain.Campaign;
import com.myworld.modules.campaign.domain.CampaignType;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Response DTO for Campaign — decouples the REST API contract from the JPA entity.
 *
 * This prevents internal fields (e.g. DB-level flags, lazy-loaded relations) from
 * accidentally leaking into the API response, and lets the entity schema evolve
 * independently of the API contract.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CampaignResponseDTO {

    private Long id;
    private String name;
    private String description;
    private CampaignType campaignType;
    private String trackingLink;
    private BigDecimal rewardAmount;
    private String termsUrl;
    private String logoUrl;
    private String advertiserName;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private Boolean isActive;
    private Integer maxLeadsPerUser;
    private Integer totalLeadsCap;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static CampaignResponseDTO from(Campaign c) {
        return CampaignResponseDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .campaignType(c.getCampaignType())
                .trackingLink(c.getTrackingLink())
                .rewardAmount(c.getRewardAmount())
                .termsUrl(c.getTermsUrl())
                .logoUrl(c.getLogoUrl())
                .advertiserName(c.getAdvertiserName())
                .startAt(c.getStartAt())
                .endAt(c.getEndAt())
                .isActive(c.getIsActive())
                .maxLeadsPerUser(c.getMaxLeadsPerUser())
                .totalLeadsCap(c.getTotalLeadsCap())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}