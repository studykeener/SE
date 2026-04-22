package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.CreatePlatformUserRequest;
import com.buct.adminbackend.entity.PlatformUser;
import com.buct.adminbackend.enums.UserStatus;
import com.buct.adminbackend.repository.PlatformUserRepository;
import com.buct.adminbackend.service.OperationLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/platform-users")
@RequiredArgsConstructor
public class PlatformUserController {

    private final PlatformUserRepository platformUserRepository;
    private final OperationLogService operationLogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<List<PlatformUser>> list() {
        return ApiResponse.ok(platformUserRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<PlatformUser> create(@Valid @RequestBody CreatePlatformUserRequest request, Authentication authentication) {
        PlatformUser user = new PlatformUser();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setSource(request.source());
        user.setStatus(UserStatus.ENABLED);
        PlatformUser saved = platformUserRepository.save(user);
        operationLogService.log(authentication.getName(), "CREATE_PLATFORM_USER", saved.getUsername(), "创建前台用户");
        return ApiResponse.ok("创建成功", saved);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<PlatformUser> updateStatus(@PathVariable Long id, @RequestParam UserStatus status, Authentication authentication) {
        PlatformUser user = platformUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("前台用户不存在"));
        user.setStatus(status);
        PlatformUser saved = platformUserRepository.save(user);
        operationLogService.log(authentication.getName(), "UPDATE_PLATFORM_USER_STATUS", saved.getUsername(), "状态修改为: " + status);
        return ApiResponse.ok("状态更新成功", saved);
    }

    @PatchMapping("/{id}/permissions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<PlatformUser> updateFineGrainedPermissions(@PathVariable Long id,
                                                                  @RequestParam boolean commentAllowed,
                                                                  @RequestParam boolean uploadAllowed,
                                                                  Authentication authentication) {
        PlatformUser user = platformUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("前台用户不存在"));
        user.setCommentAllowed(commentAllowed);
        user.setUploadAllowed(uploadAllowed);
        PlatformUser saved = platformUserRepository.save(user);
        operationLogService.log(authentication.getName(), "UPDATE_PLATFORM_USER_PERMISSION", saved.getUsername(),
                "commentAllowed=" + commentAllowed + ", uploadAllowed=" + uploadAllowed);
        return ApiResponse.ok("权限更新成功", saved);
    }
}
