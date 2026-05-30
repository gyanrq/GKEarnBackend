package com.myworld.modules.identity.api;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MfaSettingsRequest {
    @NotNull private Boolean mfaEnabled;
    @Builder.Default private Boolean emailOtpEnabled  = false;
    @Builder.Default private Boolean mobileOtpEnabled = false;
    @Builder.Default private Boolean totpEnabled      = false;
}
