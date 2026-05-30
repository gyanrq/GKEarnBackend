package com.myworld.modules.identity.api;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoginResponseDTO {
    private boolean             mfaRequired;
    // Present when mfaRequired=false
    private String              accessToken;
    private String              refreshToken;
    private UserResponseDTO     user;
    // Present when mfaRequired=true
    private MfaChallengeResponse mfaChallenge;
}