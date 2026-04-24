package com.buct.adminbackend.repository;

import com.buct.adminbackend.entity.RolePermissionAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolePermissionAssignmentRepository extends JpaRepository<RolePermissionAssignment, Long> {
    List<RolePermissionAssignment> findByRoleId(Long roleId);

    void deleteByRoleId(Long roleId);
}
