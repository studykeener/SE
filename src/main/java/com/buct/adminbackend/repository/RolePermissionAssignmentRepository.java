package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.RolePermissionAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface RolePermissionAssignmentRepository extends JpaRepository<RolePermissionAssignment, Long> {
    List<RolePermissionAssignment> findByRoleId(Long roleId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    void deleteByRoleId(Long roleId);
}
