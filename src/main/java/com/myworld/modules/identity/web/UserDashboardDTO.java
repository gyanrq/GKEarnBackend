// backend/identity/web/UserDashboardDTO.java
// FIX: Added emailVerified field — DashboardScreen reads data.emailVerified
//      but this field was missing from the DTO
package com.myworld.modules.identity.web;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserDashboardDTO {
    private String name;
    private String email;
    private String referralCode;
    private Long totalCredits;
    private long totalReferrals;
    private long successfulReferrals;
    private long pendingLeads;
    private long rewardedLeads;
    // FIX: Added — DashboardScreen checks emailVerified to show/hide verify banner
    private boolean emailVerified;
}