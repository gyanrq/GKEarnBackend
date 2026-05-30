package com.myworld.modules.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;

@Entity
@Table(name = "otps", indexes = {
    @Index(name = "idx_otp_identifier", columnList = "identifier")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Otp {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String identifier;

    @Column(nullable = false)
    private String codeHash;

    @Builder.Default
    private Integer attempts = 0;

    @Builder.Default
    private Boolean used = false;

    @CreationTimestamp
    private OffsetDateTime createdAt;

    private OffsetDateTime expiresAt;
}
