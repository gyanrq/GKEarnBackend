package com.myworld.modules.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "login_attempts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoginAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String identifier;
    private String ipAddress;
    private Boolean success;
    private OffsetDateTime attemptedAt;
}
