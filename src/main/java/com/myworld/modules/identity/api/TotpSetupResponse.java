package com.myworld.modules.identity.api;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TotpSetupResponse {
    private String qrDataUri;       // data:image/png;base64,...
    private String manualEntryKey;  // plain Base32 secret
    private String issuer;
    private String accountName;
}