package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.BatchReviewRequest;
import com.buct.adminbackend.dto.CreateReviewContentRequest;
import com.buct.adminbackend.dto.ReviewActionRequest;
import com.buct.adminbackend.dto.ReviewQueueItemResponse;
import com.buct.adminbackend.dto.ReviewTargetRef;
import com.buct.adminbackend.entity.AdminUser;
import com.buct.adminbackend.entity.OperationLog;
import com.buct.adminbackend.entity.ReviewStrategyConfig;
import com.buct.adminbackend.entity.SensitiveWord;
import com.buct.adminbackend.enums.AutoReviewAction;
import com.buct.adminbackend.enums.ContentType;
import com.buct.adminbackend.enums.ReviewStatus;
import com.buct.adminbackend.enums.RoleType;
import com.buct.adminbackend.enums.SensitiveWordLevel;
import com.buct.adminbackend.repository.AdminUserRepository;
import com.buct.adminbackend.repository.OperationLogRepository;
import com.buct.adminbackend.repository.ReviewStrategyConfigRepository;
import com.buct.adminbackend.repository.RoleDefinitionRepository;
import com.buct.adminbackend.repository.SensitiveWordRepository;
import com.buct.adminbackend.security.PermissionCodes;
import com.buct.adminbackend.service.OperationLogService;
import com.buct.adminbackend.service.ReviewQueueService;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 内容审核管理控制器。
 * <p>
 * 提供以下功能的 REST API：
 * 1. 待审队列查询：GET /api/admin/reviews（支持状态/类型/来源/时间/风险分等筛选）
 * 2. 审核操作：PATCH /{sourceTable}/{id}/action（通过/拒绝/复审）
 * 3. 批量审核：PATCH /batch/action
 * 4. 敏感词管理：CRUD /sensitive-words
 * 5. 审核策略配置：GET/PUT /strategy
 * 6. 审核统计：GET /stats（每日审核量/通过率/工作量）
 */
@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewQueueService reviewQueueService;
    private final AdminUserRepository adminUserRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;
    private final SensitiveWordRepository sensitiveWordRepository;
    private final ReviewStrategyConfigRepository reviewStrategyConfigRepository;
    private final OperationLogRepository operationLogRepository;
    private final OperationLogService operationLogService;

    /** 查询待审队列，支持多条件筛选，合并评论+照片两张表的数据 */
    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.REVIEW_VIEW + "')")
    public ApiResponse<List<ReviewQueueItemResponse>> list(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) ContentType contentType,
            @RequestParam(required = false) ContentType type,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String submitter,
            @RequestParam(required = false) String submitterName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime submitFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime submitTo,
            @RequestParam(required = false) Integer riskMin,
            @RequestParam(required = false) Integer riskMax) {
        if (riskMin != null && riskMax != null && riskMin > riskMax) {
            throw new IllegalArgumentException("风险分最小值不能大于最大值");
        }
        ContentType finalType = contentType != null ? contentType : type;
        String finalSource = StringUtils.hasText(sourceSystem) ? sourceSystem : source;
        String finalSubmitter = StringUtils.hasText(submitter) ? submitter : submitterName;
        return ApiResponse.ok(reviewQueueService.list(
                status, finalType, finalSource, finalSubmitter, keyword, submitFrom, submitTo, riskMin, riskMax));
    }

    @GetMapping("/{sourceTable}/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.REVIEW_VIEW + "')")
    public ApiResponse<ReviewQueueItemResponse> detail(@PathVariable String sourceTable, @PathVariable Long id) {
        return ApiResponse.ok(reviewQueueService.getBySource(sourceTable, id));
    }

    /** 后台测试入口：手动新增审核内容，触发自动审核流程 */
    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.REVIEW_ACTION + "')")
    public ApiResponse<ReviewQueueItemResponse> create(@Valid @RequestBody CreateReviewContentRequest request,
                                                         Authentication authentication) {
        ReviewQueueItemResponse saved = reviewQueueService.createTestContent(request);
        operationLogService.log(authentication.getName(), "CREATE_REVIEW_CONTENT",
                saved.sourceTable() + ":" + saved.id(),
                "写入队友表 " + saved.sourceTable() + "，审核状态: " + saved.reviewStatus());
        return ApiResponse.ok("新增成功", saved);
    }

    /** 人工审核：单条通过/拒绝/复审，拒绝时必填原因 */
    @PatchMapping("/{sourceTable}/{id}/action")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.REVIEW_ACTION + "')")
    public ApiResponse<ReviewQueueItemResponse> review(@PathVariable String sourceTable,
                                                       @PathVariable Long id,
                                                       @Valid @RequestBody ReviewActionRequest request,
                                                       Authentication authentication) {
        Long operatorId = resolveAdminId(authentication);
        ReviewQueueItemResponse saved = reviewQueueService.review(
                sourceTable, id, request.reviewStatus(), request.rejectReason(),
                authentication.getName(), operatorId);
        operationLogService.log(authentication.getName(), "REVIEW_CONTENT",
                sourceTable + ":" + id, "审核结果: " + request.reviewStatus());
        return ApiResponse.ok("审核成功", saved);
    }

    /** 批量审核：对多条内容执行相同审核动作 */
    @PatchMapping("/batch/action")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.REVIEW_ACTION + "')")
    public ApiResponse<Void> batchReview(@Valid @RequestBody BatchReviewRequest request,
                                         Authentication authentication) {
        Long operatorId = resolveAdminId(authentication);
        for (var target : request.targets()) {
            reviewQueueService.review(target.sourceTable(), target.id(), request.reviewStatus(),
                    request.rejectReason(), authentication.getName(), operatorId);
        }
        operationLogService.log(authentication.getName(), "BATCH_REVIEW_CONTENT",
                summarizeBatchTargets(request.targets()),
                buildBatchReviewDetails(request));
        return ApiResponse.ok("批量审核成功", null);
    }

    /** operation_target 列最长 100，批量时只记摘要，明细放 details */
    private static String summarizeBatchTargets(List<ReviewTargetRef> targets) {
        if (targets == null || targets.isEmpty()) {
            return "batch:0";
        }
        if (targets.size() == 1) {
            ReviewTargetRef t = targets.get(0);
            String one = t.sourceTable() + ":" + t.id();
            return one.length() <= 100 ? one : one.substring(0, 100);
        }
        return "batch:" + targets.size() + "条";
    }

    private static String buildBatchReviewDetails(BatchReviewRequest request) {
        String ids = request.targets().stream()
                .map(t -> t.sourceTable() + ":" + t.id())
                .collect(Collectors.joining(","));
        String details = "审核结果: " + request.reviewStatus() + "; " + ids;
        return details.length() <= 1000 ? details : details.substring(0, 997) + "...";
    }

    /** 敏感词列表查询，支持按关键词和级别筛选 */
    @GetMapping("/sensitive-words")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.REVIEW_VIEW + "')")
    public ApiResponse<List<SensitiveWord>> listSensitiveWords(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) SensitiveWordLevel level) {
        if (!StringUtils.hasText(keyword) && level == null) {
            return ApiResponse.ok(sensitiveWordRepository.findAll(Sort.by(Sort.Direction.ASC, "word")));
        }
        Specification<SensitiveWord> spec = (root, q, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                String p = "%" + keyword.toLowerCase() + "%";
                preds.add(cb.like(cb.lower(root.get("word")), p));
            }
            if (level != null) {
                preds.add(cb.equal(root.get("level"), level));
            }
            if (preds.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
        return ApiResponse.ok(
                sensitiveWordRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "word")));
    }

    /** 新增敏感词，支持指定级别(LIGHT/SEVERE)，默认LIGHT */
    @PostMapping("/sensitive-words")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.REVIEW_ACTION + "')")
    public ApiResponse<SensitiveWord> createSensitiveWord(
            @RequestParam String word,
            @RequestParam(required = false) SensitiveWordLevel level,
            Authentication authentication) {
        String w = normalizeWord(word);
        if (sensitiveWordRepository.existsByWord(w)) {
            throw new IllegalArgumentException("敏感词已存在");
        }
        SensitiveWord sw = new SensitiveWord();
        sw.setWord(w);
        sw.setEnabled(true);
        sw.setLevel(level == null ? SensitiveWordLevel.LIGHT : level);
        SensitiveWord saved = sensitiveWordRepository.save(sw);
        operationLogService.log(authentication.getName(), "CREATE_SENSITIVE_WORD", w, "新增敏感词");
        return ApiResponse.ok("新增成功", saved);
    }

    @PatchMapping("/sensitive-words/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.REVIEW_ACTION + "')")
    public ApiResponse<SensitiveWord> updateSensitiveWordStatus(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) SensitiveWordLevel level,
            Authentication authentication) {
        SensitiveWord sw = sensitiveWordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("敏感词不存在"));
        if (enabled == null && level == null) {
            throw new IllegalArgumentException("至少提供 enabled 或 level 参数");
        }
        if (enabled != null) {
            sw.setEnabled(enabled);
        }
        if (level != null) {
            sw.setLevel(level);
        }
        SensitiveWord saved = sensitiveWordRepository.save(sw);
        operationLogService.log(authentication.getName(), "UPDATE_SENSITIVE_WORD", saved.getWord(),
                "enabled=" + saved.getEnabled() + ", level=" + saved.getLevel());
        return ApiResponse.ok("更新成功", saved);
    }

    @DeleteMapping("/sensitive-words/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.REVIEW_ACTION + "')")
    public ApiResponse<Void> deleteSensitiveWord(@PathVariable Long id, Authentication authentication) {
        SensitiveWord sw = sensitiveWordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("敏感词不存在"));
        sensitiveWordRepository.deleteById(id);
        operationLogService.log(authentication.getName(), "DELETE_SENSITIVE_WORD", sw.getWord(), "删除敏感词");
        return ApiResponse.ok("删除成功", null);
    }

    @GetMapping("/sensitive-words/logs")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.REVIEW_VIEW + "')")
    public ApiResponse<List<OperationLog>> sensitiveWordLogs() {
        List<OperationLog> logs = operationLogRepository.findByOperationTypeIn(
                List.of("CREATE_SENSITIVE_WORD", "UPDATE_SENSITIVE_WORD", "DELETE_SENSITIVE_WORD"),
                Sort.by(Sort.Direction.DESC, "operationTime"));
        return ApiResponse.ok(logs);
    }

    @GetMapping("/strategy/logs")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.REVIEW_VIEW + "')")
    public ApiResponse<List<OperationLog>> reviewStrategyLogs() {
        List<OperationLog> logs = operationLogRepository.findByOperationTypeIn(
                List.of("UPDATE_REVIEW_STRATEGY"),
                Sort.by(Sort.Direction.DESC, "operationTime"));
        return ApiResponse.ok(logs);
    }

    /** 获取自动审核策略配置（不存在则初始化默认值） */
    @GetMapping("/strategy")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.REVIEW_VIEW + "')")
    public ApiResponse<ReviewStrategyConfig> getStrategy() {
        return ApiResponse.ok(getOrCreateStrategy());
    }

    /** 更新自动审核策略（修改风险阈值和各档动作） */
    @PutMapping("/strategy")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.REVIEW_ACTION + "')")
    public ApiResponse<ReviewStrategyConfig> updateStrategy(@RequestBody ReviewStrategyConfig request,
                                                            Authentication authentication) {
        if (request.getLowRiskMaxScore() == null || request.getMediumRiskMaxScore() == null) {
            throw new IllegalArgumentException("风险阈值不能为空");
        }
        if (request.getLowRiskMaxScore() < 0 || request.getMediumRiskMaxScore() > 100
                || request.getLowRiskMaxScore() >= request.getMediumRiskMaxScore()) {
            throw new IllegalArgumentException("风险阈值配置不合法");
        }
        ReviewStrategyConfig cfg = getOrCreateStrategy();
        cfg.setLowRiskMaxScore(request.getLowRiskMaxScore());
        cfg.setMediumRiskMaxScore(request.getMediumRiskMaxScore());
        cfg.setLowRiskAction(request.getLowRiskAction() == null ? AutoReviewAction.AUTO_APPROVE : request.getLowRiskAction());
        cfg.setMediumRiskAction(request.getMediumRiskAction() == null ? AutoReviewAction.MANUAL_REVIEW : request.getMediumRiskAction());
        cfg.setHighRiskAction(request.getHighRiskAction() == null ? AutoReviewAction.AUTO_REJECT : request.getHighRiskAction());
        ReviewStrategyConfig saved = reviewStrategyConfigRepository.save(cfg);
        operationLogService.log(authentication.getName(), "UPDATE_REVIEW_STRATEGY", "review-strategy", "更新自动审核策略");
        return ApiResponse.ok("保存成功", saved);
    }

    /** 审核统计：每日审核量、通过/拒绝率、审核员工作量 */
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.REVIEW_VIEW + "')")
    public ApiResponse<Map<String, Object>> stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime start = from == null ? end.minusDays(7) : from;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("统计开始时间不能晚于结束时间");
        }
        Long contentReviewerRoleId = roleDefinitionRepository.findByCode(RoleType.CONTENT_REVIEWER.name())
                .map(r -> r.getId())
                .orElse(-1L);
        Set<String> contentReviewerUsernames = adminUserRepository.findAll().stream()
                .filter(u -> contentReviewerRoleId.equals(u.getRoleId()))
                .map(AdminUser::getUsername)
                .collect(java.util.stream.Collectors.toSet());
        List<ReviewQueueItemResponse> reviewed = reviewQueueService.listReviewedBetween(start, end);
        Map<LocalDate, int[]> daily = new HashMap<>();
        Map<String, Integer> reviewerWorkload = new HashMap<>();
        Map<String, Integer> contentReviewerWorkload = new HashMap<>();
        int approved = 0;
        int rejected = 0;
        for (ReviewQueueItemResponse x : reviewed) {
            if (x.reviewTime() == null) {
                continue;
            }
            LocalDate d = x.reviewTime().toLocalDate();
            int[] arr = daily.computeIfAbsent(d, k -> new int[]{0, 0, 0});
            arr[0] += 1;
            if (x.reviewStatus() == ReviewStatus.APPROVED) {
                arr[1] += 1;
                approved++;
            } else if (x.reviewStatus() == ReviewStatus.REJECTED) {
                arr[2] += 1;
                rejected++;
            }
            if (StringUtils.hasText(x.reviewer())) {
                String name = x.reviewer();
                reviewerWorkload.put(name, reviewerWorkload.getOrDefault(name, 0) + 1);
                if (contentReviewerUsernames.contains(name)) {
                    contentReviewerWorkload.put(name, contentReviewerWorkload.getOrDefault(name, 0) + 1);
                }
            }
        }
        List<Map<String, Object>> dailyStats = daily.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, int[]>comparingByKey(Comparator.reverseOrder()))
                .map(e -> {
                    int total = e.getValue()[0];
                    int pass = e.getValue()[1];
                    int reject = e.getValue()[2];
                    Map<String, Object> m = new HashMap<>();
                    m.put("date", e.getKey().toString());
                    m.put("total", total);
                    m.put("approved", pass);
                    m.put("rejected", reject);
                    m.put("approveRate", total == 0 ? 0D : round2(100D * pass / total));
                    m.put("rejectRate", total == 0 ? 0D : round2(100D * reject / total));
                    return m;
                }).toList();
        Map<String, Object> data = new HashMap<>();
        data.put("from", start);
        data.put("to", end);
        data.put("totalReviewed", reviewed.size());
        data.put("approved", approved);
        data.put("rejected", rejected);
        data.put("approveRate", reviewed.isEmpty() ? 0D : round2(100D * approved / reviewed.size()));
        data.put("rejectRate", reviewed.isEmpty() ? 0D : round2(100D * rejected / reviewed.size()));
        data.put("daily", dailyStats);
        data.put("reviewerWorkload", reviewerWorkload);
        data.put("contentReviewerWorkload", contentReviewerWorkload);
        return ApiResponse.ok(data);
    }

    private Long resolveAdminId(Authentication authentication) {
        return adminUserRepository.findByUsername(authentication.getName()).map(AdminUser::getId).orElse(null);
    }

    private ReviewStrategyConfig getOrCreateStrategy() {
        return reviewStrategyConfigRepository.findById(1L).orElseGet(() -> {
            ReviewStrategyConfig cfg = new ReviewStrategyConfig();
            cfg.setId(1L);
            return reviewStrategyConfigRepository.save(cfg);
        });
    }

    private static String normalizeWord(String word) {
        if (word == null) {
            throw new IllegalArgumentException("敏感词不能为空");
        }
        String w = word.trim();
        if (!StringUtils.hasText(w)) {
            throw new IllegalArgumentException("敏感词不能为空");
        }
        return w;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
