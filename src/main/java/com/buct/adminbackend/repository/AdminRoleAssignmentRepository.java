package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.AdminRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminRoleAssignmentRepository extends JpaRepository<AdminRoleAssignment, Long> {
    List<AdminRoleAssignment> findByAdminUserId(Long adminUserId);

    void deleteByAdminUserId(Long adminUserId);
}
