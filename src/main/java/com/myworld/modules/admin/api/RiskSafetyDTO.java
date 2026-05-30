package com.myworld.modules.admin.api;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RiskSafetyDTO {
    private long currentLiability;
    private long highRiskUsers;
    private double avgDailyIssued;
    private double avgDailyRedeemed;
    private double burnRate;
}