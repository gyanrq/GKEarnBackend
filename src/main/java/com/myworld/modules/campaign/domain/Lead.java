package com.myworld.modules.campaign.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.myworld.core.domain.BaseEntity;
import com.myworld.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "campaign_leads",
    indexes = {
        @Index(name = "idx_lead_user",     columnList = "user_id"),
        @Index(name = "idx_lead_campaign", columnList = "campaign_id"),
        @Index(name = "idx_lead_status",   columnList = "status")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Lead extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LeadStatus status = LeadStatus.PENDING;

    private String registeredMobile;
    private String registeredEmail;
    private String proofUrl;          // screenshot / receipt link

    private String adminNotes;
    private String rejectionReason;
}
