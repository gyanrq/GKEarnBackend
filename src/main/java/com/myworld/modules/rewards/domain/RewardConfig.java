package com.myworld.modules.rewards.domain;

import com.myworld.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "reward_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RewardConfig extends BaseEntity {

    // 10 credits = ₹1  →  1000 credits = ₹100
    @Builder.Default
    @Column(nullable = false)
    private Integer creditsPerRupee = 10;

    @Builder.Default
    @Column(nullable = false)
    private Long minRedeemCredits = 1000L;

    // Max credits a user can earn per day across all sources
    @Builder.Default
    @Column(nullable = false)
    private Long maxDailyEarn = 4000L;

    /**
     * Account age gate — minimum SECONDS since registration before a user can redeem.
     * DEFAULT = 0 (no restriction). 60 = 1 minute, 3600 = 1 hour.
     */
    @Builder.Default
    @Column(nullable = false)
    private Long redemptionWaitSeconds = 0L;

    /**
     * Informational — how many SECONDS admin takes to process payouts.
     * Shown on the frontend redeem form.
     */
    @Builder.Default
    @Column(nullable = false)
    private Long payoutProcessingSeconds = 604800L; // 7 days in seconds by default

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    /**
     * Redeem time window (IST, 24-hour clock, stored as "HH:mm:ss").
     * NULL means no restriction (open all day).
     */
    @Column(length = 8)
    private String redeemWindowStart;

    @Column(length = 8)
    private String redeemWindowEnd;

    /**
     * Safe fallback factory — used when no config row exists in DB.
     */
    public static RewardConfig defaults() {
        RewardConfig cfg = new RewardConfig();
        cfg.creditsPerRupee         = 10;
        cfg.minRedeemCredits        = 1000L;
        cfg.maxDailyEarn            = 4000L;
        cfg.redemptionWaitSeconds   = 0L;         // No account age restriction by default
        cfg.payoutProcessingSeconds = 604800L;    // 7 days processing time by default
        cfg.isActive                = true;
        cfg.redeemWindowStart       = null;
        cfg.redeemWindowEnd         = null;
        return cfg;
    }
}