package com.myworld.modules.referral.api;

import lombok.*;
import java.time.OffsetDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReferralResponseDTO {
    private Long id;
    private String referredUserName;
    private String referredUserEmail;
    private String status;
    private OffsetDateTime createdAt;
}
