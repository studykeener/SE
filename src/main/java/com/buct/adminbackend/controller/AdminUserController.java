package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.AdminUserResponse;
import com.buct.adminbackend.dto.CreateAdminUserRequest;
import com.buct.adminbackend.entity.AdminUser;
import com.buct.adminbackend.enums.UserStatus;
import com.buct.adminbackend.repository.AdminUserRepository;
import com.buct.adminbackend.service.OperationLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    @GetMapping
    public ApiResponse<List<AdminUserResponse>> list() {
        List<AdminUserResponse> data = adminUserRepository.findAll()
                .stream()
                .map(this::toResponse)
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
        adminUser.setStatus(status);
        AdminUser saved = adminUserRepository.save(adminUser);
        operationLogService.log(authentication.getName(), "UPDATE_ADMIN_STATUS", saved.getUsername(), "状态修改为: " + status);
        return ApiResponse.ok("状态更新成功", toResponse(saved));
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
