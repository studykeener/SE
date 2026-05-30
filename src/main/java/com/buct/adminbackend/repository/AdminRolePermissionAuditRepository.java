package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.AdminRolePermissionAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRolePermissionAuditRepository extends JpaRepository<AdminRolePermissionAudit, Long> {
}
