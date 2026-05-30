package com.myworld.modules.fraud.api;

import lombok.*;

import java.util.List;

// ── Duplicate group (IP / Device / UPI) ──────────────────────────────────────
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DuplicateGroupDTO {
    private String       type;       // "IP" | "DEVICE" | "UPI"
    private String       value;      // the shared IP / deviceHash / upiId
    private int          userCount;
    private List<DupUserDTO> users;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DupUserDTO {
        private Long   userId;
        private String name;
        private String email;
        private String phone;
        private String createdAt;
        private boolean isBlocked;
    }
}
