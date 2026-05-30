package com.myworld.modules.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "device_fingerprints")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeviceFingerprint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String deviceId;
    private String userAgent;
    private String ipAddress;
    private OffsetDateTime lastSeenAt;
    private Boolean trusted;
}
