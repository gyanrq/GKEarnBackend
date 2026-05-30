package com.myworld.modules.admin.api;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AdminDashboardDTO {
    // User stats
    private long totalUsers;
    private long activeUsers;
    private long blockedUsers;
    private long deletedUsers;
    private long newUsersToday;
    private long newUsersThisWeek;
    private long newUsersThisMonth;

    // KYC stats
    private long kycNotStarted;
    private long kycPending;
    private long kycUnderReview;
    private long kycVerified;
    private long kycRejected;

    // Credits stats
    private long totalCreditsInCirculation;
    private long totalCreditsRedeemed;

    // Redemption stats
    private long redemptionsPending;
    private long redemptionsCompleted;

    // Referral stats
    private long totalReferrals;
    private long successfulReferrals;

    // Campaign stats
    private long activeCampaigns;
    private long totalLeads;
    private long pendingLeads;

    // ── Risk & Liquidity Monitor (NEW) ────────────────────────────────────────
    private long potentialLiability;       // unredeemed credits = platform debt
    private long highRiskBalanceCount;     // users with balance > 5000 credits
    private double avgDailyIssued;         // 30-day rolling avg credits/day issued
    private double avgDailyRedeemed;       // 30-day rolling avg credits/day redeemed
    private double burnRate;               // avgDailyIssued / avgDailyRedeemed
}