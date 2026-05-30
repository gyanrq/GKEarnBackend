package com.myworld.modules.referral.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.myworld.core.domain.BaseEntity;
import com.myworld.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "referrals",
    indexes = {
        @Index(name = "idx_ref_referrer",  columnList = "referrer_id"),
        @Index(name = "idx_ref_referred",  columnList = "referred_id"),
        @Index(name = "idx_ref_status",    columnList = "status")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Referral extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_id", nullable = false)
    private User referrer;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_id", nullable = false)
    private User referred;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReferralStatus status = ReferralStatus.PENDING;

    private String referralCode;

    private String notes;

    @Column(name = "bonus_given", nullable = false)
    @Builder.Default
    private Boolean bonusGiven = false;
}