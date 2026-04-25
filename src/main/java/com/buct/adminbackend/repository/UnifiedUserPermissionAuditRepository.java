package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.UnifiedUserPermissionAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnifiedUserPermissionAuditRepository extends JpaRepository<UnifiedUserPermissionAudit, Long> {
}

