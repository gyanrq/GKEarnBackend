package com.myworld.modules.admin.api;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TopEarnerDTO {
    private Long   userId;
    private String email;
    private long   totalEarned;
    private long   totalRedeemed;
    private long   netBalance;
    private double redemptionRate;
}