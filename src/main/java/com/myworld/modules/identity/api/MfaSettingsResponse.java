package com.myworld.modules.identity.api;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MfaSettingsResponse {
    private Boolean mfaEnabled;
    private Boolean emailOtpEnabled;
    private Boolean mobileOtpEnabled;
    private Boolean totpEnabled;
    private Boolean totpVerified;
}
