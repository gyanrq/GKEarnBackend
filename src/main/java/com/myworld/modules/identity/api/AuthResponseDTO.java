package com.myworld.modules.identity.api;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuthResponseDTO {
    private String accessToken;
    private String refreshToken;
    private UserResponseDTO user;
}
