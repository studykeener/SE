package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.CreateReviewContentRequest;
import com.buct.adminbackend.dto.ReviewActionRequest;
import com.buct.adminbackend.entity.ReviewStrategyConfig;
import com.buct.adminbackend.entity.ReviewContent;
import com.buct.adminbackend.entity.AdminUser;
import com.buct.adminbackend.entity.SensitiveWord;
import com.buct.adminbackend.entity.OperationLog;
import com.buct.adminbackend.enums.AutoReviewAction;
import com.buct.adminbackend.enums.ContentType;
import com.buct.adminbackend.enums.ReviewStatus;
import com.buct.adminbackend.enums.RoleType;
import com.buct.adminbackend.enums.SensitiveWordLevel;
import com.buct.adminbackend.repository.AdminUserRepository;
import com.buct.adminbackend.repository.ReviewContentRepository;
import com.buct.adminbackend.repository.ReviewStrategyConfigRepository;
import com.buct.adminbackend.repository.SensitiveWordRepository;
import com.buct.adminbackend.repository.OperationLogRepository;
import com.buct.adminbackend.service.ImageModerationService;
import com.buct.adminbackend.service.OperationLogService;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private static final int IMAGE_LOW_RISK_MAX_SCORE = 30;
    private static final int IMAGE_MEDIUM_RISK_MAX_SCORE = 60;

    private final ReviewContentRepository reviewContentRepository;
    private final AdminUserRepository adminUserRepository;
    private final SensitiveWordRepository sensitiveWordRepository;
    private final ReviewStrategyConfigRepository reviewStrategyConfigRepository;
    private final OperationLogRepository operationLogRepository;
    private final OperationLogService operationLogService;
    private final ImageModerationService imageModerationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<List<ReviewContent>> list(
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
        Specification<ReviewContent> spec = buildReviewFilterSpec(
                status, finalType, finalSource, finalSubmitter, keyword, submitFrom, submitTo, riskMin, riskMax);
        List<ReviewContent> list = reviewContentRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "submitTime"));
        return ApiResponse.ok(list);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<ReviewContent> detail(@PathVariable Long id) {
        ReviewContent content = reviewContentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审核内容不存在"));
        return ApiResponse.ok(content);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER','DATA_ADMIN')")
    public ApiResponse<ReviewContent> create(@Valid @RequestBody CreateReviewContentRequest request, Authentication authentication) {
        ReviewContent content = new ReviewContent();
        content.setContentType(request.contentType());
        content.setSourceSystem(request.sourceSystem());
        content.setSubmitter(request.submitter());
        content.setContentText(request.contentText());
        content.setContentUrl(request.contentUrl());
        content.setRiskScore(computeRiskScore(content));
        boolean blocked = applyAutoReview(content);
        ReviewContent saved = reviewContentRepository.save(content);
        operationLogService.log(authentication.getName(), "CREATE_REVIEW_CONTENT", String.valueOf(saved.getId()),
                "新增内容，自动审核结果: " + saved.getReviewStatus());
        if (blocked) {
            throw new IllegalArgumentException("内容违规无法发布，请修改后重试");
        }
        return ApiResponse.ok("新增成功", saved);
    }

    @PatchMapping("/{id}/action")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<ReviewContent> review(@PathVariable Long id,
                                             @Valid @RequestBody ReviewActionRequest request,
                                             Authentication authentication) {
        if (request.reviewStatus() == ReviewStatus.PENDING) {
            throw new IllegalArgumentException("审核动作不能设置为 PENDING");
        }
        if (request.reviewStatus() == ReviewStatus.REJECTED
                && (request.rejectReason() == null || request.rejectReason().isBlank())) {
            throw new IllegalArgumentException("拒绝时必须填写拒绝原因");
        }
        ReviewContent content = reviewContentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审核内容不存在"));
        content.setReviewStatus(request.reviewStatus());
        content.setReviewTime(LocalDateTime.now());
        content.setReviewer(authentication.getName());
        if (request.reviewStatus() == ReviewStatus.REJECTED) {
            content.setRejectReason(request.rejectReason());
        } else if (request.reviewStatus() == ReviewStatus.RECHECK) {
            content.setRejectReason(request.rejectReason());
        } else {
            content.setRejectReason(null);
        }
        content.setAutoReviewed(false);
        ReviewContent saved = reviewContentRepository.save(content);
        operationLogService.log(authentication.getName(), "REVIEW_CONTENT", String.valueOf(saved.getId()),
                "审核结果: " + request.reviewStatus());
        return ApiResponse.ok("审核成功", saved);
    }

    @PatchMapping("/batch/action")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<Void> batchReview(@RequestParam List<Long> ids,
                                         @Valid @RequestBody ReviewActionRequest request,
                                         Authentication authentication) {
        if (request.reviewStatus() == ReviewStatus.PENDING) {
            throw new IllegalArgumentException("审核动作不能设置为 PENDING");
        }
        if (request.reviewStatus() == ReviewStatus.REJECTED
                && (request.rejectReason() == null || request.rejectReason().isBlank())) {
            throw new IllegalArgumentException("批量拒绝时必须填写拒绝原因");
        }
        for (Long id : ids) {
            reviewContentRepository.findById(id).ifPresent(content -> {
                content.setReviewStatus(request.reviewStatus());
                content.setReviewTime(LocalDateTime.now());
                content.setReviewer(authentication.getName());
                if (request.reviewStatus() == ReviewStatus.REJECTED) {
                    content.setRejectReason(request.rejectReason());
                } else if (request.reviewStatus() == ReviewStatus.RECHECK) {
                    content.setRejectReason(request.rejectReason());
                } else {
                    content.setRejectReason(null);
                }
                content.setAutoReviewed(false);
                reviewContentRepository.save(content);
            });
        }
        operationLogService.log(authentication.getName(), "BATCH_REVIEW_CONTENT", ids.toString(), "审核结果: " + request.reviewStatus());
        return ApiResponse.ok("批量审核成功", null);
    }

    @GetMapping("/sensitive-words")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
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

    @PostMapping("/sensitive-words")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<Void> deleteSensitiveWord(@PathVariable Long id, Authentication authentication) {
        SensitiveWord sw = sensitiveWordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("敏感词不存在"));
        sensitiveWordRepository.deleteById(id);
        operationLogService.log(authentication.getName(), "DELETE_SENSITIVE_WORD", sw.getWord(), "删除敏感词");
        return ApiResponse.ok("删除成功", null);
    }

    @GetMapping("/sensitive-words/logs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<List<OperationLog>> sensitiveWordLogs() {
        List<OperationLog> logs = operationLogRepository.findByOperationTypeIn(
                List.of("CREATE_SENSITIVE_WORD", "UPDATE_SENSITIVE_WORD", "DELETE_SENSITIVE_WORD"),
                Sort.by(Sort.Direction.DESC, "operationTime"));
        return ApiResponse.ok(logs);
    }

    @GetMapping("/strategy/logs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<List<OperationLog>> reviewStrategyLogs() {
        List<OperationLog> logs = operationLogRepository.findByOperationTypeIn(
                List.of("UPDATE_REVIEW_STRATEGY"),
                Sort.by(Sort.Direction.DESC, "operationTime"));
        return ApiResponse.ok(logs);
    }

    @GetMapping("/strategy")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<ReviewStrategyConfig> getStrategy() {
        return ApiResponse.ok(getOrCreateStrategy());
    }

    @PutMapping("/strategy")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<ReviewStrategyConfig> updateStrategy(@RequestBody ReviewStrategyConfig request, Authentication authentication) {
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

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<Map<String, Object>> stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime start = from == null ? end.minusDays(7) : from;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("统计开始时间不能晚于结束时间");
        }
        Set<String> contentReviewerUsernames = adminUserRepository.findAll().stream()
                .filter(u -> u.getRole() == RoleType.CONTENT_REVIEWER)
                .map(AdminUser::getUsername)
                .collect(Collectors.toSet());
        List<ReviewContent> reviewed = reviewContentRepository.findByReviewTimeBetweenOrderByReviewTimeDesc(start, end);
        Map<LocalDate, int[]> daily = new HashMap<>();
        Map<String, Integer> reviewerWorkload = new HashMap<>();
        Map<String, Integer> contentReviewerWorkload = new HashMap<>();
        int approved = 0;
        int rejected = 0;
        for (ReviewContent x : reviewed) {
            LocalDate d = x.getReviewTime().toLocalDate();
            int[] arr = daily.computeIfAbsent(d, k -> new int[]{0, 0, 0});
            arr[0] += 1;
            if (x.getReviewStatus() == ReviewStatus.APPROVED) {
                arr[1] += 1;
                approved++;
            } else if (x.getReviewStatus() == ReviewStatus.REJECTED) {
                arr[2] += 1;
                rejected++;
            }
            if (StringUtils.hasText(x.getReviewer())) {
                String name = x.getReviewer();
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

    private static Specification<ReviewContent> buildReviewFilterSpec(
            ReviewStatus status,
            ContentType contentType,
            String sourceSystem,
            String submitter,
            String keyword,
            LocalDateTime submitFrom,
            LocalDateTime submitTo,
            Integer riskMin,
            Integer riskMax) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (status != null) {
                preds.add(cb.equal(root.get("reviewStatus"), status));
            }
            if (contentType != null) {
                preds.add(cb.equal(root.get("contentType"), contentType));
            }
            if (StringUtils.hasText(sourceSystem)) {
                String like = "%" + sourceSystem.trim().toLowerCase() + "%";
                preds.add(cb.like(cb.lower(root.get("sourceSystem")), like));
            }
            if (StringUtils.hasText(submitter)) {
                String like = "%" + submitter.trim().toLowerCase() + "%";
                preds.add(cb.like(cb.lower(root.get("submitter")), like));
            }
            if (StringUtils.hasText(keyword)) {
                String kw = "%" + keyword.trim().toLowerCase() + "%";
                preds.add(cb.or(
                        cb.like(cb.lower(root.get("contentText")), kw),
                        cb.like(cb.lower(cb.coalesce(root.get("contentUrl"), cb.literal(""))), kw)
                ));
            }
            if (submitFrom != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("submitTime"), submitFrom));
            }
            if (submitTo != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("submitTime"), submitTo));
            }
            if (riskMin != null) {
                preds.add(cb.ge(root.get("riskScore"), riskMin));
            }
            if (riskMax != null) {
                preds.add(cb.le(root.get("riskScore"), riskMax));
            }
            if (preds.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    private boolean applyAutoReview(ReviewContent content) {
        if (content.getContentType() == ContentType.IMAGE) {
            return applyImageAutoReview(content);
        }
        ReviewStrategyConfig cfg = getOrCreateStrategy();
        content.setAutoReviewed(true);
        int risk = content.getRiskScore() == null ? 0 : content.getRiskScore();
        if (risk <= cfg.getLowRiskMaxScore()) {
            applyAutoAction(content, cfg.getLowRiskAction(), "低风险自动审核");
            return content.getReviewStatus() == ReviewStatus.REJECTED;
        }
        if (risk <= cfg.getMediumRiskMaxScore()) {
            applyAutoAction(content, cfg.getMediumRiskAction(), "中风险转人工审核");
            return content.getReviewStatus() == ReviewStatus.REJECTED;
        }
        if (risk > cfg.getMediumRiskMaxScore()) {
            applyAutoAction(content, cfg.getHighRiskAction(), "高风险自动审核");
            return content.getReviewStatus() == ReviewStatus.REJECTED;
        }
        return false;
    }

    private boolean applyImageAutoReview(ReviewContent content) {
        content.setAutoReviewed(true);
        int risk = content.getRiskScore() == null ? 0 : content.getRiskScore();
        if (risk < IMAGE_LOW_RISK_MAX_SCORE) {
            applyAutoAction(content, AutoReviewAction.AUTO_APPROVE, "图片低风险自动审核");
            return false;
        }
        if (risk < IMAGE_MEDIUM_RISK_MAX_SCORE) {
            applyAutoAction(content, AutoReviewAction.MANUAL_REVIEW, "图片中风险转人工审核");
            return false;
        }
        applyAutoAction(content, AutoReviewAction.AUTO_REJECT, "图片高风险自动审核");
        return true;
    }

    private void applyAutoAction(ReviewContent content, AutoReviewAction action, String reason) {
        if (action == null || action == AutoReviewAction.MANUAL_REVIEW) {
            content.setReviewStatus(ReviewStatus.PENDING);
            content.setAutoDecisionNote(reason + "，转人工审核");
            content.setReviewTime(null);
            content.setReviewer(null);
            content.setRejectReason(null);
            return;
        }
        content.setReviewTime(LocalDateTime.now());
        content.setReviewer("AUTO");
        if (action == AutoReviewAction.AUTO_APPROVE) {
            content.setReviewStatus(ReviewStatus.APPROVED);
            content.setRejectReason(null);
            content.setAutoDecisionNote(reason + "，自动通过");
        } else if (action == AutoReviewAction.AUTO_REJECT) {
            content.setReviewStatus(ReviewStatus.REJECTED);
            content.setRejectReason("自动审核拦截：" + reason);
            content.setAutoDecisionNote(reason + "，自动拒绝");
        }
    }

    private static boolean isImageViolation(ReviewContent content) {
        String text = ((content.getContentText() == null ? "" : content.getContentText()) + " "
                + (content.getContentUrl() == null ? "" : content.getContentUrl())).toLowerCase();
        Set<String> bad = Set.of("violence", "porn", "bloody", "涉黄", "暴力", "违规");
        return bad.stream().anyMatch(text::contains);
    }

    private int computeRiskScore(ReviewContent content) {
        List<SensitiveWord> words = sensitiveWordRepository.findByEnabledTrueOrderByWordAsc();
        String allText = ((content.getContentText() == null ? "" : content.getContentText()) + " "
                + (content.getContentUrl() == null ? "" : content.getContentUrl())).toLowerCase();
        int lightHits = 0;
        for (SensitiveWord w : words) {
            if (!StringUtils.hasText(w.getWord())) continue;
            int hits = countOccurrences(allText, w.getWord().toLowerCase());
            if (hits <= 0) continue;
            if (w.getLevel() == SensitiveWordLevel.SEVERE) return 100;
            lightHits += hits;
        }
        int score = lightHits * 10;
        if (content.getContentType() == ContentType.IMAGE) {
            int imageScore = imageModerationService.scoreImage(content.getContentUrl(), content.getContentText());
            score = Math.max(score, imageScore);
            if (isSeriousImageViolation(content)) return 100;
            if (isImageViolation(content)) score += 10;
        }
        return Math.min(100, Math.max(0, score));
    }

    private static boolean isSeriousImageViolation(ReviewContent content) {
        String text = ((content.getContentText() == null ? "" : content.getContentText()) + " "
                + (content.getContentUrl() == null ? "" : content.getContentUrl())).toLowerCase();
        Set<String> severe = Set.of("childporn", "terror", "爆炸物", "恋童", "极端暴力", "严重违规");
        return severe.stream().anyMatch(text::contains);
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

    private static int countOccurrences(String text, String keyword) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(keyword)) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while (true) {
            int found = text.indexOf(keyword, idx);
            if (found < 0) break;
            count++;
            idx = found + keyword.length();
        }
        return count;
    }
}
