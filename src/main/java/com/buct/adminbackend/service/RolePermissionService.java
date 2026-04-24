package com.buct.adminbackend.service;

import com.buct.adminbackend.entity.*;
import com.buct.adminbackend.enums.RoleType;
import com.buct.adminbackend.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RolePermissionService {

    private final RoleDefinitionRepository roleDefinitionRepository;
    private final PermissionDefinitionRepository permissionDefinitionRepository;
    private final RolePermissionAssignmentRepository rolePermissionAssignmentRepository;
    private final AdminRoleAssignmentRepository adminRoleAssignmentRepository;
    private final AdminUserRepository adminUserRepository;

    @PostConstruct
    @Transactional
    public void initDefaults() {
        initRole("SUPER_ADMIN", "超级管理员", "系统最高权限");
        initRole("CONTENT_REVIEWER", "内容审核员", "负责审核内容");
        initRole("DATA_ADMIN", "数据管理员", "负责数据管理");

        List<String> perms = List.of(
                "USER_VIEW", "USER_EDIT", "USER_BAN",
                "REVIEW_VIEW", "REVIEW_ACTION",
                "ARTIFACT_VIEW", "ARTIFACT_EDIT", "ARTIFACT_IMPORT_EXPORT",
                "LOG_VIEW", "STATS_VIEW",
                "ROLE_VIEW", "ROLE_ASSIGN", "PERMISSION_ASSIGN"
        );
        for (String code : perms) {
            initPermission(code, code, code);
        }
    }

    private void initRole(String code, String name, String desc) {
        if (roleDefinitionRepository.findByCode(code).isPresent()) {
            return;
        }
        RoleDefinition r = new RoleDefinition();
        r.setCode(code);
        r.setName(name);
        r.setDescription(desc);
        roleDefinitionRepository.save(r);
    }

    private void initPermission(String code, String name, String desc) {
        if (permissionDefinitionRepository.findByCode(code).isPresent()) {
            return;
        }
        PermissionDefinition p = new PermissionDefinition();
        p.setCode(code);
        p.setName(name);
        p.setDescription(desc);
        permissionDefinitionRepository.save(p);
    }

    @Transactional
    public void assignRolePermissions(Long roleId, List<Long> permissionIds) {
        rolePermissionAssignmentRepository.deleteByRoleId(roleId);
        for (Long pid : permissionIds) {
            RolePermissionAssignment item = new RolePermissionAssignment();
            item.setRoleId(roleId);
            item.setPermissionId(pid);
            rolePermissionAssignmentRepository.save(item);
        }
    }

    @Transactional
    public void assignAdminRoles(Long adminId, List<Long> roleIds) {
        adminRoleAssignmentRepository.deleteByAdminUserId(adminId);
        for (Long rid : roleIds) {
            AdminRoleAssignment item = new AdminRoleAssignment();
            item.setAdminUserId(adminId);
            item.setRoleId(rid);
            adminRoleAssignmentRepository.save(item);
        }
        adminUserRepository.findById(adminId).ifPresent(admin -> {
            roleIds.stream().findFirst()
                    .flatMap(roleDefinitionRepository::findById)
                    .ifPresent(role -> {
                        try {
                            admin.setRole(RoleType.valueOf(role.getCode()));
                            adminUserRepository.save(admin);
                        } catch (IllegalArgumentException ignored) {
                        }
                    });
        });
    }

    public List<String> getPermissionCodesByAdminId(Long adminId) {
        List<AdminRoleAssignment> roles = adminRoleAssignmentRepository.findByAdminUserId(adminId);
        if (roles.isEmpty()) {
            return List.of();
        }
        Set<Long> roleIds = new HashSet<>();
        for (AdminRoleAssignment role : roles) {
            roleIds.add(role.getRoleId());
        }
        Set<Long> permIds = new HashSet<>();
        for (Long roleId : roleIds) {
            for (RolePermissionAssignment mapping : rolePermissionAssignmentRepository.findByRoleId(roleId)) {
                permIds.add(mapping.getPermissionId());
            }
        }
        List<String> result = new ArrayList<>();
        for (Long permId : permIds) {
            permissionDefinitionRepository.findById(permId).ifPresent(p -> result.add(p.getCode()));
        }
        return result;
    }
}
