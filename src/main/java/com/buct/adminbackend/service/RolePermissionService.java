package com.buct.adminbackend.service;

import com.buct.adminbackend.entity.*;
import com.buct.adminbackend.repository.*;
import com.buct.adminbackend.security.PermissionCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RolePermissionService {

    private final RoleDefinitionRepository roleDefinitionRepository;
    private final PermissionDefinitionRepository permissionDefinitionRepository;
    private final RolePermissionAssignmentRepository rolePermissionAssignmentRepository;
    private final AdminUserRepository adminUserRepository;
    private final AdminRolePermissionAuditRepository adminRolePermissionAuditRepository;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    @PostConstruct
    @Transactional
    public void initDefaults() {
        initRole("SUPER_ADMIN", "超级管理员", "系统最高权限", true);
        initRole("CONTENT_REVIEWER", "内容审核员", "仅负责内容审核，无法操作用户数据与系统配置", true);
        initRole("DATA_ADMIN", "数据管理员", "负责数据管理", true);

        initPermission(PermissionCodes.USER_VIEW, "查看用户", "USER", "VIEW", "查看前台用户");
        initPermission(PermissionCodes.USER_EDIT, "编辑用户", "USER", "EDIT", "创建/更新用户");
        initPermission(PermissionCodes.USER_DELETE, "删除用户", "USER", "DELETE", "删除用户");
        initPermission(PermissionCodes.USER_BAN, "禁用用户", "USER", "BAN", "禁用/启用用户及评论上传权限");

        initPermission(PermissionCodes.REVIEW_VIEW, "查看审核", "REVIEW", "VIEW", "查看待审内容与敏感词");
        initPermission(PermissionCodes.REVIEW_ACTION, "审核操作", "REVIEW", "EDIT", "审核通过/拒绝/复审");

        initPermission(PermissionCodes.ARTIFACT_VIEW, "查看文物", "ARTIFACT", "VIEW", "查看文物数据");
        initPermission(PermissionCodes.ARTIFACT_EDIT, "编辑文物", "ARTIFACT", "EDIT", "新增/修改文物");
        initPermission(PermissionCodes.ARTIFACT_DELETE, "删除文物", "ARTIFACT", "DELETE", "删除文物");
        initPermission(PermissionCodes.ARTIFACT_IMPORT_EXPORT, "导入导出文物", "ARTIFACT", "EXPORT", "导入导出文物");

        initPermission(PermissionCodes.LOG_VIEW, "查看日志", "LOG", "VIEW", "查看操作/登录/安全日志");
        initPermission(PermissionCodes.STATS_VIEW, "查看看板", "STATS", "VIEW", "查看统计看板");

        initPermission(PermissionCodes.ROLE_VIEW, "查看角色权限", "ROLE", "VIEW", "查看角色与权限定义");
        initPermission(PermissionCodes.ROLE_CREATE, "创建角色", "ROLE", "CREATE", "创建自定义角色");
        initPermission(PermissionCodes.ROLE_ASSIGN, "分配角色", "ROLE", "ASSIGN", "为管理员分配角色");
        initPermission(PermissionCodes.PERMISSION_ASSIGN, "分配权限", "ROLE", "ASSIGN", "为角色分配权限");
        initPermission(PermissionCodes.ADMIN_MANAGE, "管理员管理", "ADMIN", "EDIT", "管理后台管理员账号");

        initPermission(PermissionCodes.BACKUP_MANAGE, "备份恢复", "BACKUP", "EDIT", "备份与恢复");

        assignDefaultRolePermissions();
        ensureSuperAdminHasAllPermissions();
        syncLegacyAdminRoles();
    }

    /** 应用就绪后校正内置角色权限（delete/insert 必须在事务中执行） */
    @EventListener(ApplicationReadyEvent.class)
    public void syncSystemBuiltinRolePermissionsOnStartup() {
        syncSystemBuiltinRolePermissions();
    }

    private void assignDefaultRolePermissions() {
        Map<String, List<String>> matrix = Map.of(
                "SUPER_ADMIN", List.of(),
                "CONTENT_REVIEWER", List.of(
                        PermissionCodes.REVIEW_VIEW, PermissionCodes.REVIEW_ACTION
                ),
                "DATA_ADMIN", List.of(
                        PermissionCodes.ARTIFACT_VIEW, PermissionCodes.ARTIFACT_EDIT,
                        PermissionCodes.ARTIFACT_DELETE, PermissionCodes.ARTIFACT_IMPORT_EXPORT,
                        PermissionCodes.STATS_VIEW
                )
        );
        for (Map.Entry<String, List<String>> e : matrix.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            roleDefinitionRepository.findByCode(e.getKey()).ifPresent(role -> {
                if (!rolePermissionAssignmentRepository.findByRoleId(role.getId()).isEmpty()) {
                    return;
                }
                List<Long> permIds = resolvePermissionIds(e.getValue());
                assignRolePermissions(role.getId(), permIds, "system", null, false);
            });
        }
    }

    /** 系统内置角色权限以代码为准，启动时校正（避免历史库中权限残留） */
    private void syncSystemBuiltinRolePermissions() {
        syncRolePermissions("CONTENT_REVIEWER", List.of(
                PermissionCodes.REVIEW_VIEW, PermissionCodes.REVIEW_ACTION
        ));
    }

    private void syncRolePermissions(String roleCode, List<String> expectedCodes) {
        roleDefinitionRepository.findByCode(roleCode).ifPresent(role -> {
            List<String> current = resolvePermissionCodesByRoleId(role.getId());
            List<String> expected = expectedCodes.stream().sorted().toList();
            if (current.equals(expected)) {
                return;
            }
            Long roleId = role.getId();
            List<Long> permIds = resolvePermissionIds(expectedCodes);
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(status -> assignRolePermissions(roleId, permIds, "system", null, false));
        });
    }

    private void ensureSuperAdminHasAllPermissions() {
        roleDefinitionRepository.findByCode("SUPER_ADMIN").ifPresent(role -> {
            Set<Long> existing = rolePermissionAssignmentRepository.findByRoleId(role.getId()).stream()
                    .map(RolePermissionAssignment::getPermissionId)
                    .collect(Collectors.toSet());
            for (PermissionDefinition permission : permissionDefinitionRepository.findAll()) {
                if (existing.contains(permission.getId())) {
                    continue;
                }
                RolePermissionAssignment item = new RolePermissionAssignment();
                item.setRoleId(role.getId());
                item.setPermissionId(permission.getId());
                rolePermissionAssignmentRepository.save(item);
            }
        });
    }

    private void syncLegacyAdminRoles() {
        for (AdminUser admin : adminUserRepository.findAll()) {
            if (admin.getRoleId() != null) {
                continue;
            }
            roleDefinitionRepository.findByCode("SUPER_ADMIN").ifPresent(role -> {
                admin.setRoleId(role.getId());
                adminUserRepository.save(admin);
            });
        }
    }

    private void initRole(String code, String name, String desc, boolean system) {
        roleDefinitionRepository.findByCode(code).ifPresentOrElse(role -> {
            boolean changed = false;
            if (!name.equals(role.getName())) {
                role.setName(name);
                changed = true;
            }
            if (desc != null && !desc.equals(role.getDescription())) {
                role.setDescription(desc);
                changed = true;
            }
            if (changed) {
                roleDefinitionRepository.save(role);
            }
        }, () -> {
            RoleDefinition r = new RoleDefinition();
            r.setCode(code);
            r.setName(name);
            r.setDescription(desc);
            r.setIsSystem(system);
            roleDefinitionRepository.save(r);
        });
    }

    private void initPermission(String code, String name, String module, String action, String desc) {
        permissionDefinitionRepository.findByCode(code).ifPresentOrElse(p -> {
            p.setName(name);
            p.setModule(module);
            p.setAction(action);
            p.setDescription(desc);
            permissionDefinitionRepository.save(p);
        }, () -> {
            PermissionDefinition p = new PermissionDefinition();
            p.setCode(code);
            p.setName(name);
            p.setModule(module);
            p.setAction(action);
            p.setDescription(desc);
            permissionDefinitionRepository.save(p);
        });
    }

    @Transactional
    public void assignRolePermissions(Long roleId, List<Long> permissionIds, String operatorName, Long operatorId) {
        assignRolePermissions(roleId, permissionIds, operatorName, operatorId, true);
    }

    @Transactional
    public void assignRolePermissions(Long roleId,
                                    List<Long> permissionIds,
                                    String operatorName,
                                    Long operatorId,
                                    boolean writeLogs) {
        roleDefinitionRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        List<String> beforeCodes = resolvePermissionCodesByRoleId(roleId);
        rolePermissionAssignmentRepository.deleteByRoleId(roleId);
        rolePermissionAssignmentRepository.flush();
        List<Long> distinctIds = permissionIds == null ? List.of() : permissionIds.stream().distinct().toList();
        for (Long pid : distinctIds) {
            if (!permissionDefinitionRepository.existsById(pid)) {
                throw new IllegalArgumentException("权限不存在: " + pid);
            }
            RolePermissionAssignment item = new RolePermissionAssignment();
            item.setRoleId(roleId);
            item.setPermissionId(pid);
            rolePermissionAssignmentRepository.save(item);
        }
        List<String> afterCodes = resolvePermissionCodesByRoleId(roleId);
        if (!writeLogs) {
            return;
        }
        String beforeJson = toJson(beforeCodes);
        String afterJson = toJson(afterCodes);
        saveAudit(operatorId, operatorName, "ROLE_PERMISSION", roleId, "ASSIGN", beforeJson, afterJson);
        operationLogService.logChange(
                operatorName,
                operatorId,
                "ROLE",
                "ASSIGN_ROLE_PERMISSION",
                "role:" + roleId,
                String.valueOf(roleId),
                beforeJson,
                afterJson,
                "分配角色权限"
        );
    }

    @Transactional
    public void assignAdminRole(Long adminId, Long roleId, String operatorName, Long operatorId) {
        AdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("管理员不存在"));
        roleDefinitionRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        String beforeRole = resolveRoleCode(admin.getRoleId());
        Long oldRoleId = admin.getRoleId();
        admin.setRoleId(roleId);
        adminUserRepository.save(admin);
        String afterRole = resolveRoleCode(roleId);
        String beforeJson = toJson(Map.of("roleId", oldRoleId, "roleCode", beforeRole));
        String afterJson = toJson(Map.of("roleId", roleId, "roleCode", afterRole));
        saveAudit(operatorId, operatorName, "ADMIN_USER", adminId, "ASSIGN_ROLE", beforeJson, afterJson);
        operationLogService.logChange(
                operatorName,
                operatorId,
                "ROLE",
                "ASSIGN_ADMIN_ROLE",
                "admin:" + adminId,
                String.valueOf(adminId),
                beforeJson,
                afterJson,
                "为管理员分配角色"
        );
    }

    public void logRoleCreated(String operatorName, Long operatorId, RoleDefinition role) {
        String afterJson = toJson(Map.of(
                "id", role.getId(),
                "code", role.getCode(),
                "name", role.getName()
        ));
        saveAudit(operatorId, operatorName, "ROLE", role.getId(), "CREATE", null, afterJson);
        operationLogService.logChange(
                operatorName,
                operatorId,
                "ROLE",
                "CREATE_ROLE",
                "role:" + role.getId(),
                String.valueOf(role.getId()),
                null,
                afterJson,
                "创建自定义角色"
        );
    }

    public List<Long> getPermissionIdsByRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return rolePermissionAssignmentRepository.findByRoleId(roleId).stream()
                .map(RolePermissionAssignment::getPermissionId)
                .sorted()
                .toList();
    }

    public List<String> getPermissionCodesByAdminId(Long adminId) {
        return adminUserRepository.findById(adminId)
                .map(AdminUser::getRoleId)
                .map(this::resolvePermissionCodesByRoleId)
                .orElse(List.of());
    }

    public String getRoleCodeByAdminId(Long adminId) {
        return adminUserRepository.findById(adminId)
                .flatMap(a -> roleDefinitionRepository.findById(a.getRoleId()))
                .map(RoleDefinition::getCode)
                .orElse("CONTENT_REVIEWER");
    }

    public Long getRoleIdByCode(String code) {
        return roleDefinitionRepository.findByCode(code)
                .map(RoleDefinition::getId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + code));
    }

    private List<String> resolvePermissionCodesByRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        return rolePermissionAssignmentRepository.findByRoleId(roleId).stream()
                .map(RolePermissionAssignment::getPermissionId)
                .map(permissionDefinitionRepository::findById)
                .flatMap(Optional::stream)
                .map(PermissionDefinition::getCode)
                .sorted()
                .toList();
    }

    private List<Long> resolvePermissionIds(List<String> codes) {
        return codes.stream()
                .map(permissionDefinitionRepository::findByCode)
                .flatMap(Optional::stream)
                .map(PermissionDefinition::getId)
                .toList();
    }

    private String resolveRoleCode(Long roleId) {
        if (roleId == null) {
            return null;
        }
        return roleDefinitionRepository.findById(roleId)
                .map(RoleDefinition::getCode)
                .orElse(null);
    }

    private void saveAudit(Long operatorId, String operatorName, String targetType, Long targetId,
                           String action, String before, String after) {
        AdminRolePermissionAudit audit = new AdminRolePermissionAudit();
        audit.setOperatorId(operatorId == null ? 0L : operatorId);
        audit.setOperatorName(operatorName == null ? "system" : operatorName);
        audit.setTargetType(targetType);
        audit.setTargetId(targetId);
        audit.setAction(action);
        audit.setBeforeSnapshot(before);
        audit.setAfterSnapshot(after);
        adminRolePermissionAuditRepository.save(audit);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
