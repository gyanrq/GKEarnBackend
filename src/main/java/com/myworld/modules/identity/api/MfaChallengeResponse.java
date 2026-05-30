package com.myworld.modules.identity.api;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MfaChallengeResponse {
    private String  sessionToken;
    private boolean emailRequired;
    private boolean mobileRequired;
    private boolean totpRequired;
    private String  maskedEmail;
    private String  maskedPhone;
}
