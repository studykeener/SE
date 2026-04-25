package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.config.AdminAccountInitializer;
import com.buct.adminbackend.dto.AdminUserResponse;
import com.buct.adminbackend.dto.CreateAdminUserRequest;
import com.buct.adminbackend.dto.UpdateAdminRequest;
import com.buct.adminbackend.entity.AdminUser;
import com.buct.adminbackend.enums.RoleType;
import com.buct.adminbackend.enums.UserStatus;
import com.buct.adminbackend.repository.AdminUserRepository;
import com.buct.adminbackend.service.AuditLogService;
import com.buct.adminbackend.service.OperationLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminUserController {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;
    private final AuditLogService auditLogService;

    @GetMapping
    public ApiResponse<List<AdminUserResponse>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) RoleType role,
            @RequestParam(required = false) UserStatus status) {
        List<AdminUserResponse> data = adminUserRepository.findAll()
                .stream()
                .map(this::toResponse)
                .filter(x -> !StringUtils.hasText(username)
                        || x.username().toLowerCase().contains(username.trim().toLowerCase()))
                .filter(x -> role == null || x.role() == role)
                .filter(x -> status == null || x.status() == status)
                .toList();
        return ApiResponse.ok(data);
    }

    @PostMapping
    public ApiResponse<AdminUserResponse> create(@Valid @RequestBody CreateAdminUserRequest request, Authentication authentication) {
        if (adminUserRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("管理员用户名已存在");
        }
        AdminUser adminUser = new AdminUser();
        adminUser.setUsername(request.username());
        adminUser.setPassword(passwordEncoder.encode(request.password()));
        adminUser.setRole(request.role());
        adminUser.setStatus(UserStatus.ENABLED);
        AdminUser saved = adminUserRepository.save(adminUser);
        operationLogService.log(authentication.getName(), "CREATE_ADMIN", saved.getUsername(), "创建管理员账号");
        return ApiResponse.ok("创建成功", toResponse(saved));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminUserResponse> updateStatus(@PathVariable Long id, @RequestParam UserStatus status, Authentication authentication) {
        AdminUser adminUser = adminUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("管理员不存在"));
        if (isProtectedAdmin(adminUser) && status == UserStatus.DISABLED) {
            throw new IllegalArgumentException("受保护的系统超级管理员(" + AdminAccountInitializer.DEFAULT_ADMIN_USERNAME + ")不能禁用");
        }
        adminUser.setStatus(status);
        AdminUser saved = adminUserRepository.save(adminUser);
        operationLogService.log(authentication.getName(), "UPDATE_ADMIN_STATUS", saved.getUsername(), "状态修改为: " + status);
        auditLogService.logDataChange(authentication.getName(), "UPDATE", "ADMIN_USER", String.valueOf(id), "status=" + status);
        return ApiResponse.ok("状态更新成功", toResponse(saved));
    }

    /**
     * 超级管理员：修改其他管理员角色、状态、密码（默认 admin 账号不能删除、不能降权、不能禁用）
     */
    @PatchMapping("/{id}")
    public ApiResponse<AdminUserResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid UpdateAdminRequest request,
            Authentication authentication) {
        AdminUser adminUser = adminUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("管理员不存在"));
        if (isProtectedAdmin(adminUser)) {
            if (request.role() != null && request.role() != RoleType.SUPER_ADMIN) {
                throw new IllegalArgumentException("受保护账号不能从超级管理员降级为其他角色");
            }
            if (request.status() != null && request.status() == UserStatus.DISABLED) {
                throw new IllegalArgumentException("受保护账号不能禁用");
            }
        }
        if (request.role() == null && request.status() == null && (request.newPassword() == null || request.newPassword().isBlank())) {
            throw new IllegalArgumentException("至少提供新角色、新状态或新密码中的一项");
        }
        if (request.role() != null) {
            adminUser.setRole(request.role());
        }
        if (request.status() != null) {
            adminUser.setStatus(request.status());
        }
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            adminUser.setPassword(passwordEncoder.encode(request.newPassword()));
        }
        AdminUser saved = adminUserRepository.save(adminUser);
        operationLogService.log(authentication.getName(), "UPDATE_ADMIN", saved.getUsername(), "更新管理员");
        auditLogService.logDataChange(authentication.getName(), "UPDATE", "ADMIN_USER", String.valueOf(id), "角色/状态/密码");
        return ApiResponse.ok("更新成功", toResponse(saved));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        AdminUser adminUser = adminUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("管理员不存在"));
        if (isProtectedAdmin(adminUser)) {
            throw new IllegalArgumentException("受保护的系统超级管理员账号(" + AdminAccountInitializer.DEFAULT_ADMIN_USERNAME + ")不能删除");
        }
        if (adminUser.getUsername().equals(authentication.getName())) {
            throw new IllegalArgumentException("不能删除当前登录账号");
        }
        adminUserRepository.deleteById(id);
        operationLogService.log(authentication.getName(), "DELETE_ADMIN", adminUser.getUsername(), "删除管理员");
        auditLogService.logDataChange(authentication.getName(), "DELETE", "ADMIN_USER", String.valueOf(id), adminUser.getUsername());
        return ApiResponse.ok("已删除", null);
    }

    private static boolean isProtectedAdmin(AdminUser u) {
        return AdminAccountInitializer.DEFAULT_ADMIN_USERNAME.equals(u.getUsername());
    }

    private AdminUserResponse toResponse(AdminUser adminUser) {
        return new AdminUserResponse(
                adminUser.getId(),
                adminUser.getUsername(),
                adminUser.getRole(),
                adminUser.getStatus(),
                adminUser.getCreatedAt()
        );
    }
}
