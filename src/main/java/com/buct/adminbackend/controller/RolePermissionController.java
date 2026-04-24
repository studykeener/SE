package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.AssignIdsRequest;
import com.buct.adminbackend.dto.CreateRoleRequest;
import com.buct.adminbackend.entity.PermissionDefinition;
import com.buct.adminbackend.entity.RoleDefinition;
import com.buct.adminbackend.repository.PermissionDefinitionRepository;
import com.buct.adminbackend.repository.RoleDefinitionRepository;
import com.buct.adminbackend.service.AuditLogService;
import com.buct.adminbackend.service.RolePermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/rbac")
@RequiredArgsConstructor
public class RolePermissionController {

    private final RoleDefinitionRepository roleDefinitionRepository;
    private final PermissionDefinitionRepository permissionDefinitionRepository;
    private final RolePermissionService rolePermissionService;
    private final AuditLogService auditLogService;

    @GetMapping("/roles")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    public ApiResponse<List<RoleDefinition>> roles() {
        return ApiResponse.ok(roleDefinitionRepository.findAll());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    public ApiResponse<RoleDefinition> createRole(@Valid @RequestBody CreateRoleRequest request, Authentication auth) {
        RoleDefinition role = new RoleDefinition();
        role.setCode(request.code());
        role.setName(request.name());
        role.setDescription(request.description());
        RoleDefinition saved = roleDefinitionRepository.save(role);
        auditLogService.logDataChange(auth.getName(), "CREATE", "ROLE", String.valueOf(saved.getId()), request.code());
        return ApiResponse.ok("创建成功", saved);
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    public ApiResponse<List<PermissionDefinition>> permissions() {
        return ApiResponse.ok(permissionDefinitionRepository.findAll());
    }

    @PostMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    public ApiResponse<Void> assignRolePermissions(@PathVariable Long roleId,
                                                   @Valid @RequestBody AssignIdsRequest request,
                                                   Authentication auth) {
        rolePermissionService.assignRolePermissions(roleId, request.ids());
        auditLogService.logDataChange(auth.getName(), "ASSIGN", "ROLE_PERMISSION", String.valueOf(roleId), "permissions=" + request.ids());
        return ApiResponse.ok("分配成功", null);
    }

    @PostMapping("/admins/{adminId}/roles")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    public ApiResponse<Void> assignAdminRoles(@PathVariable Long adminId,
                                              @Valid @RequestBody AssignIdsRequest request,
                                              Authentication auth) {
        rolePermissionService.assignAdminRoles(adminId, request.ids());
        auditLogService.logDataChange(auth.getName(), "ASSIGN", "ADMIN_ROLE", String.valueOf(adminId), "roles=" + request.ids());
        return ApiResponse.ok("分配成功", null);
    }
}
