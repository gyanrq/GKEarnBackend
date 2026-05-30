package com.myworld.modules.admin.infrastructure;

import com.myworld.modules.admin.domain.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditRepository extends JpaRepository<AdminAuditLog, Long> {
}