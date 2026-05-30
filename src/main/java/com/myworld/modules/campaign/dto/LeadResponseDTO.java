package com.myworld.modules.campaign.dto;

import com.myworld.modules.campaign.domain.Lead;
import com.myworld.modules.campaign.domain.LeadStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Response DTO for Lead — decouples the REST API contract from the JPA entity.
 *
 * Returning the raw Lead entity from AdminLeadController caused two problems:
 *   1. LazyInitializationException — the user and campaign relations are LAZY.
 *      Once the Hibernate session closes after the service method returns, Jackson
 *      tries to serialize them and Hibernate throws because the session is gone.
 *   2. Infinite-loop risk — Lead → User → List<Referral> → Referral → User (cycle).
 *
 * This DTO flattens both relations into scalar fields so neither problem can occur.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeadResponseDTO {

    private Long   id;
    private String uuid;

    // ── User projection — flat scalars, no nested entity ──────────────────────
    private Long   userId;
    private String userName;
    private String userEmail;
    private String userPhone;

    // ── Campaign projection — flat scalars, no nested entity ─────────────────
    private Long       campaignId;
    private String     campaignName;
    private BigDecimal campaignRewardAmount;

    // ── Lead fields ───────────────────────────────────────────────────────────
    private LeadStatus status;
    private String     registeredMobile;
    private String     registeredEmail;
    private String     proofUrl;
    private String     adminNotes;
    private String     rejectionReason;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static LeadResponseDTO from(Lead lead) {
        LeadResponseDTOBuilder b = LeadResponseDTO.builder()
                .id(lead.getId())
                .uuid(lead.getUuid())
                .status(lead.getStatus())
                .registeredMobile(lead.getRegisteredMobile())
                .registeredEmail(lead.getRegisteredEmail())
                .proofUrl(lead.getProofUrl())
                .adminNotes(lead.getAdminNotes())
                .rejectionReason(lead.getRejectionReason())
                .createdAt(lead.getCreatedAt())
                .updatedAt(lead.getUpdatedAt());

        if (lead.getUser() != null) {
            b.userId(lead.getUser().getId())
             .userName(lead.getUser().getName())
             .userEmail(lead.getUser().getEmail())
             .userPhone(lead.getUser().getPhone());
        }

        if (lead.getCampaign() != null) {
            b.campaignId(lead.getCampaign().getId())
             .campaignName(lead.getCampaign().getName())
             .campaignRewardAmount(lead.getCampaign().getRewardAmount());
        }

        return b.build();
    }
}