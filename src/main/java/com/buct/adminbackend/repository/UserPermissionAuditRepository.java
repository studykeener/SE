package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.UserPermissionAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPermissionAuditRepository extends JpaRepository<UserPermissionAudit, Long> {

    List<UserPermissionAudit> findByUserIdOrderByOperatedAtDesc(Long userId);
}
