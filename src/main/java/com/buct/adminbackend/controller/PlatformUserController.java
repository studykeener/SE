package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.BatchUserIdsRequest;
import com.buct.adminbackend.dto.CreateBehaviorRequest;
import com.buct.adminbackend.dto.CreatePlatformUserRequest;
import com.buct.adminbackend.dto.UpdatePlatformUserRequest;
import com.buct.adminbackend.entity.PlatformUser;
import com.buct.adminbackend.entity.UserBehaviorRecord;
import com.buct.adminbackend.enums.UserSource;
import com.buct.adminbackend.enums.UserStatus;
import com.buct.adminbackend.repository.PlatformUserRepository;
import com.buct.adminbackend.repository.UserBehaviorRecordRepository;
import com.buct.adminbackend.service.AuditLogService;
import com.buct.adminbackend.service.OperationLogService;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    public ApiResponse<Page<PlatformUser>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) UserSource source,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo) {
        Specification<PlatformUser> spec = buildFilterSpec(username, source, status, createdFrom, createdTo);
        Page<PlatformUser> p = platformUserRepository.findAll(spec,
                PageRequest.of(page, Math.min(Math.max(size, 1), 200), Sort.by(Sort.Direction.DESC, "createdAt")));
        return ApiResponse.ok(p);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<PlatformUser> create(@Valid @RequestBody CreatePlatformUserRequest request, Authentication authentication) {
        if (platformUserRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (platformUserRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("邮箱已存在");
        }
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

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<PlatformUser> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePlatformUserRequest request,
            Authentication authentication) {
        PlatformUser user = platformUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("前台用户不存在"));
        if (platformUserRepository.existsByUsernameAndIdNot(request.username(), id)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (platformUserRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new IllegalArgumentException("邮箱已存在");
        }
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setSource(request.source());
        user.setStatus(request.status());
        user.setCommentAllowed(request.commentAllowed());
        user.setUploadAllowed(request.uploadAllowed());
        PlatformUser saved = platformUserRepository.save(user);
        operationLogService.log(authentication.getName(), "UPDATE_PLATFORM_USER", saved.getUsername(), "更新平台用户");
        auditLogService.logDataChange(authentication.getName(), "UPDATE", "PLATFORM_USER", String.valueOf(id), "full update");
        return ApiResponse.ok("更新成功", saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        PlatformUser user = platformUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("前台用户不存在"));
        userBehaviorRecordRepository.deleteByPlatformUserId(id);
        platformUserRepository.deleteById(id);
        operationLogService.log(authentication.getName(), "DELETE_PLATFORM_USER", user.getUsername(), "删除平台用户");
        auditLogService.logDataChange(authentication.getName(), "DELETE", "PLATFORM_USER", String.valueOf(id), user.getUsername());
        return ApiResponse.ok("已删除", null);
    }

    @DeleteMapping("/batch")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    @Transactional
    public ApiResponse<Void> deleteBatch(
            @Valid @RequestBody BatchUserIdsRequest request,
            Authentication authentication) {
        for (Long id : request.ids()) {
            platformUserRepository.findById(id).ifPresent(user -> {
                userBehaviorRecordRepository.deleteByPlatformUserId(id);
                platformUserRepository.deleteById(id);
            });
        }
        auditLogService.logDataChange(authentication.getName(), "BATCH_DELETE", "PLATFORM_USER", request.ids().toString(), "batch");
        return ApiResponse.ok("批量删除成功", null);
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

    private static Specification<PlatformUser> buildFilterSpec(
            String username,
            UserSource source,
            UserStatus status,
            LocalDateTime createdFrom,
            LocalDateTime createdTo) {
        return (root, q, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (StringUtils.hasText(username)) {
                String like = "%" + username.trim().toLowerCase() + "%";
                preds.add(cb.like(cb.lower(root.get("username")), like));
            }
            if (source != null) {
                preds.add(cb.equal(root.get("source"), source));
            }
            if (status != null) {
                preds.add(cb.equal(root.get("status"), status));
            }
            if (createdFrom != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }
            if (preds.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }
}
