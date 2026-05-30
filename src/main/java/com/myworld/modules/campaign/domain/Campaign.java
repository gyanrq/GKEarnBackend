package com.myworld.modules.campaign.domain;

import com.myworld.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

@Entity
@Table(name = "campaigns",
    indexes = {
        @Index(name = "idx_campaign_active", columnList = "is_active"),
        @Index(name = "idx_campaign_type",   columnList = "campaign_type")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Campaign extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_type")
    private CampaignType campaignType;

    @Column(nullable = false)
    private String trackingLink;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal rewardAmount;

    @Column(columnDefinition = "TEXT")
    private String termsUrl;

    private String logoUrl;

    private String advertiserName;

    private OffsetDateTime startAt;
    private OffsetDateTime endAt;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Builder.Default
    private Integer maxLeadsPerUser = 1;

    @Builder.Default
    private Integer totalLeadsCap = 0;  // 0 = unlimited

    public long getRewardCredits() {
        if (rewardAmount == null) return 0L;

        // Example: ₹1 = 100 credits (recommended for precision systems)
        return rewardAmount
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.DOWN)
                .longValue();
    }
}
