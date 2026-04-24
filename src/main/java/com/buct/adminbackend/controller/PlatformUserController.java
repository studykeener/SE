package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.CreateBehaviorRequest;
import com.buct.adminbackend.dto.CreatePlatformUserRequest;
import com.buct.adminbackend.entity.PlatformUser;
import com.buct.adminbackend.entity.UserBehaviorRecord;
import com.buct.adminbackend.enums.UserStatus;
import com.buct.adminbackend.repository.PlatformUserRepository;
import com.buct.adminbackend.repository.UserBehaviorRecordRepository;
import com.buct.adminbackend.service.AuditLogService;
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
    private final UserBehaviorRecordRepository userBehaviorRecordRepository;
    private final OperationLogService operationLogService;
    private final AuditLogService auditLogService;

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
        auditLogService.logDataChange(authentication.getName(), "CREATE", "PLATFORM_USER", String.valueOf(saved.getId()), saved.getUsername());
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
        auditLogService.logDataChange(authentication.getName(), "UPDATE", "PLATFORM_USER", String.valueOf(saved.getId()), "status=" + status);
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
        auditLogService.logDataChange(authentication.getName(), "UPDATE", "PLATFORM_USER", String.valueOf(saved.getId()),
                "commentAllowed=" + commentAllowed + ", uploadAllowed=" + uploadAllowed);
        return ApiResponse.ok("权限更新成功", saved);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<PlatformUser> detail(@PathVariable Long id) {
        PlatformUser user = platformUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("前台用户不存在"));
        return ApiResponse.ok(user);
    }

    @GetMapping("/{id}/behaviors")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<List<UserBehaviorRecord>> behaviors(@PathVariable Long id) {
        return ApiResponse.ok(userBehaviorRecordRepository.findByPlatformUserIdOrderByBehaviorTimeDesc(id));
    }

    @PostMapping("/{id}/behaviors")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<UserBehaviorRecord> createBehavior(@PathVariable Long id,
                                                          @Valid @RequestBody CreateBehaviorRequest request,
                                                          Authentication authentication) {
        platformUserRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("前台用户不存在"));
        UserBehaviorRecord record = new UserBehaviorRecord();
        record.setPlatformUserId(id);
        record.setBehaviorType(request.behaviorType());
        record.setBehaviorDetail(request.behaviorDetail());
        UserBehaviorRecord saved = userBehaviorRecordRepository.save(record);
        auditLogService.logDataChange(authentication.getName(), "CREATE", "USER_BEHAVIOR", String.valueOf(saved.getId()),
                "platformUserId=" + id);
        return ApiResponse.ok("创建成功", saved);
    }

    @PatchMapping("/batch/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<Void> batchStatus(@RequestParam List<Long> ids,
                                         @RequestParam UserStatus status,
                                         Authentication authentication) {
        for (Long id : ids) {
            platformUserRepository.findById(id).ifPresent(user -> {
                user.setStatus(status);
                platformUserRepository.save(user);
            });
        }
        auditLogService.logDataChange(authentication.getName(), "BATCH_UPDATE", "PLATFORM_USER", ids.toString(), "status=" + status);
        return ApiResponse.ok("批量更新成功", null);
    }
}
