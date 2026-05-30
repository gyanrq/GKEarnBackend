package com.myworld.modules.admin.domain;

import com.myworld.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "admin_audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AdminAuditLog extends BaseEntity {

    @Column(nullable = false)
    private String adminEmail;

    @Column(nullable = false)
    private String actionType; // e.g., "MANUAL_CREDIT", "MANUAL_DEBIT"

    @Column(nullable = false)
    private Long targetUserId;

    private Long amount;

    @Column(length = 500)
    private String reason;
}