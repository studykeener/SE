package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.*;
import com.buct.adminbackend.entity.UnifiedUser;
import com.buct.adminbackend.entity.UnifiedUserBehavior;
import com.buct.adminbackend.entity.UnifiedUserPermissionAudit;
import com.buct.adminbackend.enums.UserStatus;
import com.buct.adminbackend.repository.UnifiedUserBehaviorRepository;
import com.buct.adminbackend.repository.UnifiedUserPermissionAuditRepository;
import com.buct.adminbackend.repository.UnifiedUserRepository;
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
@RequestMapping("/api/admin/unified-users")
@RequiredArgsConstructor
public class UnifiedUserController {

    private final UnifiedUserRepository unifiedUserRepository;
    private final UnifiedUserBehaviorRepository unifiedUserBehaviorRepository;
    private final UnifiedUserPermissionAuditRepository unifiedUserPermissionAuditRepository;
    private final OperationLogService operationLogService;
    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<Page<UnifiedUser>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo) {
        Specification<UnifiedUser> spec = buildUserFilterSpec(username, sourceSystem, status, createdFrom, createdTo);
        Page<UnifiedUser> p = unifiedUserRepository.findAll(spec,
                PageRequest.of(page, Math.min(Math.max(size, 1), 200), Sort.by(Sort.Direction.DESC, "createdAt")));
        return ApiResponse.ok(p);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<UnifiedUser> detail(@PathVariable Long id) {
        UnifiedUser user = unifiedUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("统一用户不存在"));
        return ApiResponse.ok(user);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<UnifiedUser> create(@Valid @RequestBody CreateUnifiedUserRequest request, Authentication authentication) {
        String sourceSystem = normalizeText(request.sourceSystem());
        validateSourceSystem(sourceSystem);
        String sourceUserId = normalizeText(request.sourceUserId());
        if (unifiedUserRepository.existsBySourceSystemAndSourceUserId(sourceSystem, sourceUserId)) {
            throw new IllegalArgumentException("来源系统用户映射已存在");
        }
        UnifiedUser user = new UnifiedUser();
        user.setUsername(normalizeText(request.username()));
        user.setDisplayName(normalizeNullable(request.displayName()));
        user.setEmail(normalizeNullable(request.email()));
        user.setPhone(normalizeNullable(request.phone()));
        user.setSourceSystem(sourceSystem);
        user.setSourceUserId(sourceUserId);
        user.setStatus(UserStatus.ENABLED);
        UnifiedUser saved = unifiedUserRepository.save(user);
        operationLogService.log(authentication.getName(), "CREATE_UNIFIED_USER", String.valueOf(saved.getId()), "创建统一用户");
        auditLogService.logDataChange(authentication.getName(), "CREATE", "UNIFIED_USER", String.valueOf(saved.getId()), saved.getUsername());
        return ApiResponse.ok("创建成功", saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<UnifiedUser> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUnifiedUserRequest request,
            Authentication authentication) {
        UnifiedUser user = unifiedUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("统一用户不存在"));
        String sourceSystem = normalizeText(request.sourceSystem());
        validateSourceSystem(sourceSystem);
        String sourceUserId = normalizeText(request.sourceUserId());
        if (unifiedUserRepository.existsBySourceSystemAndSourceUserIdAndIdNot(sourceSystem, sourceUserId, id)) {
            throw new IllegalArgumentException("来源系统用户映射已存在");
        }
        user.setUsername(normalizeText(request.username()));
        user.setDisplayName(normalizeNullable(request.displayName()));
        user.setEmail(normalizeNullable(request.email()));
        user.setPhone(normalizeNullable(request.phone()));
        user.setSourceSystem(sourceSystem);
        user.setSourceUserId(sourceUserId);
        user.setStatus(request.status());
        user.setCommentAllowed(request.commentAllowed());
        user.setUploadAllowed(request.uploadAllowed());
        UnifiedUser saved = unifiedUserRepository.save(user);
        operationLogService.log(authentication.getName(), "UPDATE_UNIFIED_USER", String.valueOf(saved.getId()), "更新统一用户");
        auditLogService.logDataChange(authentication.getName(), "UPDATE", "UNIFIED_USER", String.valueOf(id), "full update");
        return ApiResponse.ok("更新成功", saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        UnifiedUser user = unifiedUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("统一用户不存在"));
        unifiedUserBehaviorRepository.deleteByUserId(id);
        unifiedUserRepository.deleteById(id);
        operationLogService.log(authentication.getName(), "DELETE_UNIFIED_USER", String.valueOf(id), "删除统一用户");
        auditLogService.logDataChange(authentication.getName(), "DELETE", "UNIFIED_USER", String.valueOf(id), user.getUsername());
        return ApiResponse.ok("删除成功", null);
    }

    @DeleteMapping("/batch")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    @Transactional
    public ApiResponse<Void> deleteBatch(@Valid @RequestBody BatchUserIdsRequest request, Authentication authentication) {
        for (Long id : request.ids()) {
            unifiedUserRepository.findById(id).ifPresent(u -> {
                unifiedUserBehaviorRepository.deleteByUserId(id);
                unifiedUserRepository.deleteById(id);
            });
        }
        auditLogService.logDataChange(authentication.getName(), "BATCH_DELETE", "UNIFIED_USER", request.ids().toString(), "batch");
        return ApiResponse.ok("批量删除成功", null);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<UnifiedUser> updateStatus(@PathVariable Long id, @RequestParam UserStatus status, Authentication authentication) {
        UnifiedUser user = unifiedUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("统一用户不存在"));
        UserStatus old = user.getStatus();
        user.setStatus(status);
        UnifiedUser saved = unifiedUserRepository.save(user);
        savePermissionAudit(saved.getId(), authentication.getName(), old, status, saved.getCommentAllowed(), saved.getCommentAllowed(), saved.getUploadAllowed(), saved.getUploadAllowed(), "状态更新");
        operationLogService.log(authentication.getName(), "UPDATE_UNIFIED_USER_STATUS", String.valueOf(saved.getId()), "status=" + status);
        auditLogService.logDataChange(authentication.getName(), "UPDATE", "UNIFIED_USER", String.valueOf(saved.getId()), "status=" + status);
        return ApiResponse.ok("状态更新成功", saved);
    }

    @PatchMapping("/batch/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<Void> batchStatus(@RequestParam List<Long> ids,
                                         @RequestParam UserStatus status,
                                         Authentication authentication) {
        for (Long id : ids) {
            unifiedUserRepository.findById(id).ifPresent(user -> {
                UserStatus old = user.getStatus();
                user.setStatus(status);
                UnifiedUser saved = unifiedUserRepository.save(user);
                savePermissionAudit(saved.getId(), authentication.getName(), old, status, saved.getCommentAllowed(), saved.getCommentAllowed(), saved.getUploadAllowed(), saved.getUploadAllowed(), "批量状态更新");
            });
        }
        auditLogService.logDataChange(authentication.getName(), "BATCH_UPDATE", "UNIFIED_USER", ids.toString(), "status=" + status);
        return ApiResponse.ok("批量更新成功", null);
    }

    @PatchMapping("/{id}/permissions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<UnifiedUser> updatePermissions(@PathVariable Long id,
                                                      @Valid @RequestBody UpdateUnifiedUserPermissionsRequest request,
                                                      Authentication authentication) {
        UnifiedUser user = unifiedUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("统一用户不存在"));
        Boolean oldComment = user.getCommentAllowed();
        Boolean oldUpload = user.getUploadAllowed();
        user.setCommentAllowed(request.commentAllowed());
        user.setUploadAllowed(request.uploadAllowed());
        UnifiedUser saved = unifiedUserRepository.save(user);
        savePermissionAudit(saved.getId(), authentication.getName(), saved.getStatus(), saved.getStatus(), oldComment, saved.getCommentAllowed(), oldUpload, saved.getUploadAllowed(), normalizeNullable(request.reason()));
        operationLogService.log(authentication.getName(), "UPDATE_UNIFIED_USER_PERMISSION", String.valueOf(saved.getId()),
                "commentAllowed=" + request.commentAllowed() + ", uploadAllowed=" + request.uploadAllowed());
        auditLogService.logDataChange(authentication.getName(), "UPDATE", "UNIFIED_USER", String.valueOf(saved.getId()),
                "commentAllowed=" + request.commentAllowed() + ", uploadAllowed=" + request.uploadAllowed());
        return ApiResponse.ok("权限更新成功", saved);
    }

    @GetMapping("/{id}/behaviors")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<Page<UnifiedUserBehavior>> behaviors(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        unifiedUserRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("统一用户不存在"));
        Specification<UnifiedUserBehavior> spec = buildBehaviorFilterSpec(id, type, from, to);
        Page<UnifiedUserBehavior> p = unifiedUserBehaviorRepository.findAll(spec,
                PageRequest.of(page, Math.min(Math.max(size, 1), 200), Sort.by(Sort.Direction.DESC, "behaviorTime")));
        return ApiResponse.ok(p);
    }

    @PostMapping("/{id}/behaviors")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<UnifiedUserBehavior> createBehavior(@PathVariable Long id,
                                                           @Valid @RequestBody CreateUnifiedUserBehaviorRequest request,
                                                           Authentication authentication) {
        unifiedUserRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("统一用户不存在"));
        UnifiedUserBehavior b = new UnifiedUserBehavior();
        b.setUserId(id);
        b.setBehaviorType(normalizeText(request.behaviorType()));
        b.setBehaviorContent(normalizeNullable(request.behaviorContent()));
        b.setSourceSystem(normalizeText(request.sourceSystem()));
        b.setSourceRecordId(normalizeNullable(request.sourceRecordId()));
        b.setBehaviorTime(request.behaviorTime() == null ? LocalDateTime.now() : request.behaviorTime());
        UnifiedUserBehavior saved = unifiedUserBehaviorRepository.save(b);
        operationLogService.log(authentication.getName(), "CREATE_UNIFIED_USER_BEHAVIOR", String.valueOf(saved.getId()), "userId=" + id);
        auditLogService.logDataChange(authentication.getName(), "CREATE", "UNIFIED_USER_BEHAVIOR", String.valueOf(saved.getId()), "userId=" + id);
        return ApiResponse.ok("创建成功", saved);
    }

    private void savePermissionAudit(Long userId, String operator,
                                     UserStatus oldStatus, UserStatus newStatus,
                                     Boolean oldCommentAllowed, Boolean newCommentAllowed,
                                     Boolean oldUploadAllowed, Boolean newUploadAllowed,
                                     String reason) {
        UnifiedUserPermissionAudit a = new UnifiedUserPermissionAudit();
        a.setUserId(userId);
        a.setOperator(operator);
        a.setOldStatus(oldStatus);
        a.setNewStatus(newStatus);
        a.setOldCommentAllowed(oldCommentAllowed);
        a.setNewCommentAllowed(newCommentAllowed);
        a.setOldUploadAllowed(oldUploadAllowed);
        a.setNewUploadAllowed(newUploadAllowed);
        a.setReason(normalizeNullable(reason));
        unifiedUserPermissionAuditRepository.save(a);
    }

    private static Specification<UnifiedUser> buildUserFilterSpec(
            String username,
            String sourceSystem,
            UserStatus status,
            LocalDateTime createdFrom,
            LocalDateTime createdTo) {
        return (root, q, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (StringUtils.hasText(username)) {
                preds.add(cb.like(cb.lower(root.get("username")), "%" + username.trim().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(sourceSystem)) {
                preds.add(cb.equal(cb.upper(root.get("sourceSystem")), sourceSystem.trim().toUpperCase()));
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
            if (preds.isEmpty()) return cb.conjunction();
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    private static Specification<UnifiedUserBehavior> buildBehaviorFilterSpec(
            Long userId,
            String type,
            LocalDateTime from,
            LocalDateTime to) {
        return (root, q, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.equal(root.get("userId"), userId));
            if (StringUtils.hasText(type)) {
                preds.add(cb.equal(cb.upper(root.get("behaviorType")), type.trim().toUpperCase()));
            }
            if (from != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("behaviorTime"), from));
            }
            if (to != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("behaviorTime"), to));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    private static String normalizeText(String s) {
        return s == null ? null : s.trim();
    }

    private static String normalizeNullable(String s) {
        if (!StringUtils.hasText(s)) return null;
        return s.trim();
    }

    private static void validateSourceSystem(String sourceSystem) {
        if (!StringUtils.hasText(sourceSystem)) {
            throw new IllegalArgumentException("sourceSystem 不能为空");
        }
        String v = sourceSystem.trim().toUpperCase();
        if (!"WEB".equals(v) && !"APP".equals(v)) {
            throw new IllegalArgumentException("sourceSystem 仅支持 WEB 或 APP");
        }
    }
}

