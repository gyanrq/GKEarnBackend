package com.myworld.modules.admin.api;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeadRewardSummaryDTO {
    private Long   campaignId;
    private String campaignName;
    private long   totalLeads;
    private long   totalCredits;
}