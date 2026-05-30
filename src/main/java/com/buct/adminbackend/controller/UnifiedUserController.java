package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.*;
import com.buct.adminbackend.entity.AdminUser;
import com.buct.adminbackend.entity.User;
import com.buct.adminbackend.entity.UserPermissionAudit;
import com.buct.adminbackend.enums.UserStatus;
import com.buct.adminbackend.repository.AdminUserRepository;
import com.buct.adminbackend.repository.CommentRepository;
import com.buct.adminbackend.repository.UserFavoriteRepository;
import com.buct.adminbackend.repository.UserLikeRepository;
import com.buct.adminbackend.repository.UserPermissionAuditRepository;
import com.buct.adminbackend.repository.UserRepository;
import com.buct.adminbackend.repository.UserUploadPhotoRepository;
import com.buct.adminbackend.service.AuditLogService;
import com.buct.adminbackend.service.OperationLogService;
import com.buct.adminbackend.service.UserActivityTraceService;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import com.buct.adminbackend.security.PermissionCodes;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    private static final String DEFAULT_USER_PASSWORD = "ChangeMe123";

    private final UserRepository userRepository;
    private final UserPermissionAuditRepository userPermissionAuditRepository;
    private final CommentRepository commentRepository;
    private final UserUploadPhotoRepository userUploadPhotoRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final UserLikeRepository userLikeRepository;
    private final AdminUserRepository adminUserRepository;
    private final OperationLogService operationLogService;
    private final AuditLogService auditLogService;
    private final UserActivityTraceService userActivityTraceService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.USER_VIEW + "')")
    public ApiResponse<Page<PlatformUserResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo) {
        Specification<User> spec = buildUserFilterSpec(username, sourceSystem, status, createdFrom, createdTo);
        Page<User> p = userRepository.findAll(spec,
                PageRequest.of(page, Math.min(Math.max(size, 1), 200), Sort.by(Sort.Direction.DESC, "registerTime")));
        return ApiResponse.ok(p.map(PlatformUserResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.USER_VIEW + "')")
    public ApiResponse<PlatformUserResponse> detail(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return ApiResponse.ok(PlatformUserResponse.from(user));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.USER_EDIT + "')")
    public ApiResponse<PlatformUserResponse> create(@Valid @RequestBody CreateUnifiedUserRequest request, Authentication authentication) {
        String userSource = normalizeUserSource(request.sourceSystem());
        String username = normalizeText(request.username());
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        validateUniqueEmailPhone(request.email(), request.phone(), null);
        User user = new User();
        user.setUsername(username);
        user.setNickname(normalizeNullable(request.displayName()));
        user.setEmail(normalizeNullable(request.email()));
        user.setPhone(normalizeNullable(request.phone()));
        user.setAvatarUrl(normalizeNullable(request.avatarUrl()));
        user.setSex(normalizeSex(request.sex()));
        user.setUserSource(userSource);
        String rawPassword = StringUtils.hasText(request.password()) ? request.password().trim() : DEFAULT_USER_PASSWORD;
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setStatus(UserStatus.ENABLED);
        user.setCanComment(true);
        user.setCanUpload(true);
        User saved = userRepository.save(user);
        operationLogService.log(authentication.getName(), "CREATE_USER", String.valueOf(saved.getId()), "创建前台用户");
        auditLogService.logDataChange(authentication.getName(), "CREATE", "USER", String.valueOf(saved.getId()), saved.getUsername());
        return ApiResponse.ok("创建成功", PlatformUserResponse.from(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.USER_EDIT + "')")
    public ApiResponse<PlatformUserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUnifiedUserRequest request,
            Authentication authentication) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        String userSource = normalizeUserSource(request.sourceSystem());
        String username = normalizeText(request.username());
        if (userRepository.existsByUsernameAndIdNot(username, id)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        validateUniqueEmailPhone(request.email(), request.phone(), id);
        UserStatus oldStatus = user.getStatus();
        Boolean oldComment = user.getCanComment();
        Boolean oldUpload = user.getCanUpload();

        user.setUsername(username);
        user.setNickname(normalizeNullable(request.displayName()));
        user.setEmail(normalizeNullable(request.email()));
        user.setPhone(normalizeNullable(request.phone()));
        user.setAvatarUrl(normalizeNullable(request.avatarUrl()));
        user.setSex(normalizeSex(request.sex()));
        user.setUserSource(userSource);
        user.setStatus(request.status());
        user.setCanComment(request.commentAllowed());
        user.setCanUpload(request.uploadAllowed());
        applyDisabledMeta(user, request.status(), normalizeNullable(request.disabledReason()), authentication);

        User saved = userRepository.save(user);
        if (!oldStatus.equals(saved.getStatus()) || !oldComment.equals(saved.getCanComment()) || !oldUpload.equals(saved.getCanUpload())) {
            savePermissionAudit(saved, authentication, oldStatus, saved.getStatus(),
                    oldComment, saved.getCanComment(), oldUpload, saved.getCanUpload(),
                    normalizeNullable(request.disabledReason()));
        }
        operationLogService.log(authentication.getName(), "UPDATE_USER", String.valueOf(saved.getId()), "更新前台用户");
        auditLogService.logDataChange(authentication.getName(), "UPDATE", "USER", String.valueOf(id), "full update");
        return ApiResponse.ok("更新成功", PlatformUserResponse.from(saved));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.USER_DELETE + "')")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        purgeUserRelatedData(id);
        userRepository.deleteById(id);
        operationLogService.log(authentication.getName(), "DELETE_USER", String.valueOf(id), "删除前台用户及关联内容");
        auditLogService.logDataChange(authentication.getName(), "DELETE", "USER", String.valueOf(id), user.getUsername());
        return ApiResponse.ok("删除成功", null);
    }

    @DeleteMapping("/batch")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.USER_DELETE + "')")
    @Transactional
    public ApiResponse<Void> deleteBatch(@Valid @RequestBody BatchUserIdsRequest request, Authentication authentication) {
        for (Long id : request.ids()) {
            userRepository.findById(id).ifPresent(u -> {
                purgeUserRelatedData(id);
                userRepository.deleteById(id);
            });
        }
        auditLogService.logDataChange(authentication.getName(), "BATCH_DELETE", "USER", request.ids().toString(), "batch");
        return ApiResponse.ok("批量删除成功", null);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.USER_BAN + "')")
    public ApiResponse<PlatformUserResponse> updateStatus(@PathVariable Long id,
                                          @RequestParam UserStatus status,
                                          @RequestParam(required = false) String reason,
                                          Authentication authentication) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        UserStatus old = user.getStatus();
        user.setStatus(status);
        applyDisabledMeta(user, status, normalizeNullable(reason), authentication);
        User saved = userRepository.save(user);
        savePermissionAudit(saved, authentication, old, status, saved.getCanComment(), saved.getCanComment(),
                saved.getCanUpload(), saved.getCanUpload(), normalizeNullable(reason));
        operationLogService.log(authentication.getName(), "UPDATE_USER_STATUS", String.valueOf(saved.getId()), "status=" + status);
        auditLogService.logDataChange(authentication.getName(), "UPDATE", "USER", String.valueOf(saved.getId()), "status=" + status);
        return ApiResponse.ok("状态更新成功", PlatformUserResponse.from(saved));
    }

    @PatchMapping("/batch/status")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.USER_BAN + "')")
    public ApiResponse<Void> batchStatus(@RequestParam List<Long> ids,
                                         @RequestParam UserStatus status,
                                         @RequestParam(required = false) String reason,
                                         Authentication authentication) {
        for (Long id : ids) {
            userRepository.findById(id).ifPresent(user -> {
                UserStatus old = user.getStatus();
                user.setStatus(status);
                applyDisabledMeta(user, status, normalizeNullable(reason), authentication);
                User saved = userRepository.save(user);
                savePermissionAudit(saved, authentication, old, status, saved.getCanComment(), saved.getCanComment(),
                        saved.getCanUpload(), saved.getCanUpload(), normalizeNullable(reason));
            });
        }
        auditLogService.logDataChange(authentication.getName(), "BATCH_UPDATE", "USER", ids.toString(), "status=" + status);
        return ApiResponse.ok("批量更新成功", null);
    }

    @PatchMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.USER_BAN + "')")
    public ApiResponse<PlatformUserResponse> updatePermissions(@PathVariable Long id,
                                               @Valid @RequestBody UpdateUnifiedUserPermissionsRequest request,
                                               Authentication authentication) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        Boolean oldComment = user.getCanComment();
        Boolean oldUpload = user.getCanUpload();
        user.setCanComment(request.commentAllowed());
        user.setCanUpload(request.uploadAllowed());
        User saved = userRepository.save(user);
        savePermissionAudit(saved, authentication, saved.getStatus(), saved.getStatus(),
                oldComment, saved.getCanComment(), oldUpload, saved.getCanUpload(), normalizeNullable(request.reason()));
        operationLogService.log(authentication.getName(), "UPDATE_USER_PERMISSION", String.valueOf(saved.getId()),
                "commentAllowed=" + request.commentAllowed() + ", uploadAllowed=" + request.uploadAllowed());
        auditLogService.logDataChange(authentication.getName(), "UPDATE", "USER", String.valueOf(saved.getId()),
                "commentAllowed=" + request.commentAllowed() + ", uploadAllowed=" + request.uploadAllowed());
        return ApiResponse.ok("权限更新成功", PlatformUserResponse.from(saved));
    }

    @GetMapping("/{id}/behaviors")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.USER_VIEW + "')")
    public ApiResponse<Page<UserActivityTraceItem>> behaviors(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        Page<UserActivityTraceItem> p = userActivityTraceService.list(
                id, type, from, to,
                PageRequest.of(page, Math.min(Math.max(size, 1), 200)));
        return ApiResponse.ok(p);
    }

    @GetMapping("/{id}/permission-audit")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.USER_VIEW + "')")
    public ApiResponse<List<UserPermissionAudit>> permissionAudit(@PathVariable Long id) {
        userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return ApiResponse.ok(userPermissionAuditRepository.findByUserIdOrderByOperatedAtDesc(id));
    }

    private void applyDisabledMeta(User user, UserStatus status, String reason, Authentication authentication) {
        if (status == UserStatus.DISABLED) {
            if (user.getDisabledAt() == null) {
                user.setDisabledAt(LocalDateTime.now());
            }
            user.setDisabledBy(resolveOperatorId(authentication));
            user.setDisabledReason(StringUtils.hasText(reason) ? reason.trim() : "管理员禁用");
        } else {
            user.setDisabledAt(null);
            user.setDisabledBy(null);
            user.setDisabledReason(null);
        }
    }

    private void validateUniqueEmailPhone(String email, String phone, Long excludeId) {
        String e = normalizeNullable(email);
        String p = normalizeNullable(phone);
        if (e != null) {
            boolean exists = excludeId == null
                    ? userRepository.existsByEmail(e)
                    : userRepository.existsByEmailAndIdNot(e, excludeId);
            if (exists) {
                throw new IllegalArgumentException("邮箱已被使用");
            }
        }
        if (p != null) {
            boolean exists = excludeId == null
                    ? userRepository.existsByPhone(p)
                    : userRepository.existsByPhoneAndIdNot(p, excludeId);
            if (exists) {
                throw new IllegalArgumentException("手机号已被使用");
            }
        }
    }

    private void savePermissionAudit(User user, Authentication authentication,
                                     UserStatus oldStatus, UserStatus newStatus,
                                     Boolean oldComment, Boolean newComment,
                                     Boolean oldUpload, Boolean newUpload,
                                     String reason) {
        UserPermissionAudit a = new UserPermissionAudit();
        a.setUserId(user.getId());
        Long operatorId = resolveOperatorId(authentication);
        a.setOperatorId(operatorId == null ? 0L : operatorId);
        a.setOperatorName(authentication.getName());
        a.setOldStatus(statusCode(oldStatus));
        a.setNewStatus(statusCode(newStatus));
        a.setOldCanComment(oldComment);
        a.setNewCanComment(newComment);
        a.setOldCanUpload(oldUpload);
        a.setNewCanUpload(newUpload);
        a.setReason(normalizeNullable(reason));
        userPermissionAuditRepository.save(a);
    }

    private Long resolveOperatorId(Authentication authentication) {
        return adminUserRepository.findByUsername(authentication.getName()).map(AdminUser::getId).orElse(null);
    }

    /** 删除用户前清理共用表中的评论、上传、收藏、点赞及子系统5侧记录 */
    private void purgeUserRelatedData(Long userId) {
        commentRepository.deleteByUserId(userId);
        userUploadPhotoRepository.deleteByUserId(userId);
        userFavoriteRepository.deleteByUserId(userId);
        userLikeRepository.deleteByUserId(userId);
    }

    private static Integer statusCode(UserStatus status) {
        if (status == null) return null;
        return status == UserStatus.ENABLED ? 1 : 0;
    }

    private static Specification<User> buildUserFilterSpec(
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
                preds.add(cb.equal(cb.lower(root.get("userSource")), normalizeUserSource(sourceSystem)));
            }
            if (status != null) {
                preds.add(cb.equal(root.get("status"), status));
            }
            if (createdFrom != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("registerTime"), createdFrom));
            }
            if (createdTo != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("registerTime"), createdTo));
            }
            if (preds.isEmpty()) return cb.conjunction();
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

    private static String normalizeUserSource(String sourceSystem) {
        if (!StringUtils.hasText(sourceSystem)) {
            throw new IllegalArgumentException("用户来源不能为空");
        }
        String normalized = sourceSystem.trim().toLowerCase();
        if (!"web".equals(normalized) && !"app".equals(normalized)) {
            throw new IllegalArgumentException("用户来源仅支持 web（知识服务）或 app（掌上博物馆）");
        }
        return normalized;
    }

    private static Byte normalizeSex(Byte sex) {
        if (sex == null) {
            return null;
        }
        if (sex < 0 || sex > 2) {
            throw new IllegalArgumentException("性别仅支持 0未知、1男、2女");
        }
        return sex;
    }
}
