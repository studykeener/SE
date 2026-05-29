package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.AssignIdsRequest;
import com.buct.adminbackend.dto.CreateRoleRequest;
import com.buct.adminbackend.entity.PermissionDefinition;
import com.buct.adminbackend.entity.RoleDefinition;
import com.buct.adminbackend.repository.AdminUserRepository;
import com.buct.adminbackend.repository.PermissionDefinitionRepository;
import com.buct.adminbackend.repository.RoleDefinitionRepository;
import com.buct.adminbackend.security.PermissionCodes;
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
    private final AdminUserRepository adminUserRepository;

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ROLE_VIEW + "') or hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ADMIN_MANAGE + "')")
    public ApiResponse<List<RoleDefinition>> roles() {
        return ApiResponse.ok(roleDefinitionRepository.findAll());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ROLE_CREATE + "')")
    public ApiResponse<RoleDefinition> createRole(@Valid @RequestBody CreateRoleRequest request, Authentication auth) {
        if (roleDefinitionRepository.findByCode(request.code()).isPresent()) {
            throw new IllegalArgumentException("角色编码已存在");
        }
        RoleDefinition role = new RoleDefinition();
        role.setCode(request.code());
        role.setName(request.name());
        role.setDescription(request.description());
        role.setIsSystem(false);
        RoleDefinition saved = roleDefinitionRepository.save(role);
        Long operatorId = resolveOperatorId(auth);
        rolePermissionService.logRoleCreated(auth.getName(), operatorId, saved);
        if (request.permissionIds() != null && !request.permissionIds().isEmpty()) {
            rolePermissionService.assignRolePermissions(saved.getId(), request.permissionIds(), auth.getName(), operatorId);
        }
        return ApiResponse.ok("创建成功", saved);
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ROLE_VIEW + "')")
    public ApiResponse<List<PermissionDefinition>> permissions() {
        return ApiResponse.ok(permissionDefinitionRepository.findAll());
    }

    @GetMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ROLE_VIEW + "')")
    public ApiResponse<List<Long>> rolePermissions(@PathVariable Long roleId) {
        return ApiResponse.ok(rolePermissionService.getPermissionIdsByRoleId(roleId));
    }

    @PostMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.PERMISSION_ASSIGN + "')")
    public ApiResponse<Void> assignRolePermissions(@PathVariable Long roleId,
                                                   @Valid @RequestBody AssignIdsRequest request,
                                                   Authentication auth) {
        Long operatorId = resolveOperatorId(auth);
        rolePermissionService.assignRolePermissions(roleId, request.ids(), auth.getName(), operatorId);
        return ApiResponse.ok("分配成功", null);
    }

    @PostMapping("/admins/{adminId}/roles")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ROLE_ASSIGN + "')")
    public ApiResponse<Void> assignAdminRoles(@PathVariable Long adminId,
                                              @Valid @RequestBody AssignIdsRequest request,
                                              Authentication auth) {
        if (request.ids() == null || request.ids().isEmpty()) {
            throw new IllegalArgumentException("请指定一个角色");
        }
        Long operatorId = resolveOperatorId(auth);
        rolePermissionService.assignAdminRole(adminId, request.ids().get(0), auth.getName(), operatorId);
        return ApiResponse.ok("分配成功", null);
    }

    private Long resolveOperatorId(Authentication authentication) {
        return adminUserRepository.findByUsername(authentication.getName()).map(a -> a.getId()).orElse(null);
    }
}
