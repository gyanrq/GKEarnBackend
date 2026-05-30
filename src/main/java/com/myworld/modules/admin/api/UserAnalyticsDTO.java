package com.myworld.modules.admin.api;

import lombok.*;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Full 360-degree user analytics DTO.
 * Returned by GET /api/admin/users/{userId}/analytics
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserAnalyticsDTO {

    // ── Identity ──────────────────────────────────────────────────────────────
    private Long     userId;
    private String   name;
    private String   email;
    private String   phone;
    private String   role;
    private String   referralCode;
    private Boolean  isBlocked;
    private Boolean  isEmailVerified;
    private Boolean  isPhoneVerified;
    private String   profilePictureUrl;

    // ── Account Lifecycle ────────────────────────────────────────────────────
    private OffsetDateTime joinedAt;
    private long           accountAgeDays;        // today - joinedAt
    private OffsetDateTime lastLoginAt;
    private String         lastLoginIp;
    private long           daysSinceLastLogin;    // churn indicator

    // ── Credit Summary ────────────────────────────────────────────────────────
    private long totalEarned;
    private long totalRedeemed;
    private long netBalance;
    private double redemptionRate;               // redeemed / earned * 100

    // ── Activity Breakdown ────────────────────────────────────────────────────
    private long totalTransactions;
    private List<SourceBreakdownItem> earningsBySource;

    // ── Task History ──────────────────────────────────────────────────────────
    private long totalTasksCompleted;
    private List<TaskHistoryItem> recentTasks;    // last 30 completions

    // ── Referral History ──────────────────────────────────────────────────────
    private String  referredByCode;               // who referred this user
    private long    referralsMade;                // how many users this person referred
    private long    referralsSuccessful;

    // ── Device & Login Security ───────────────────────────────────────────────
    private long              uniqueDevices;
    private List<DeviceItem>  deviceHistory;
    private long              totalLoginAttempts;
    private long              failedLoginAttempts;

    // ── Fraud / Risk Score ────────────────────────────────────────────────────
    private int    riskScore;                     // 0–100
    private String riskLevel;                     // LOW / MEDIUM / HIGH / CRITICAL
    private List<String> riskFlags;               // human-readable reasons

    // ── Timeline ─────────────────────────────────────────────────────────────
    private List<TimelineEvent> timeline;

    // ──────────────────────────────────────────────────────────────────────────
    //  Nested value objects
    // ──────────────────────────────────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SourceBreakdownItem {
        private String source;
        private long   credits;
        private double percentage;
        private long   count;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TaskHistoryItem {
        private String         taskType;
        private long           creditsEarned;
        private OffsetDateTime completedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DeviceItem {
        private String         deviceId;
        private String         userAgent;
        private String         ipAddress;
        private OffsetDateTime lastSeenAt;
        private Boolean        trusted;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TimelineEvent {
        private OffsetDateTime occurredAt;
        private String         type;       // JOIN, TASK, LOGIN, REFERRAL, PAYOUT, BLOCK, etc.
        private String         title;
        private String         detail;
        private String         severity;   // INFO, SUCCESS, WARNING, DANGER
    }
}