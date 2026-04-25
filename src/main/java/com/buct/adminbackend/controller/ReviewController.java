package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.CreateReviewContentRequest;
import com.buct.adminbackend.dto.ReviewActionRequest;
import com.buct.adminbackend.entity.ReviewStrategyConfig;
import com.buct.adminbackend.entity.ReviewContent;
import com.buct.adminbackend.entity.SensitiveWord;
import com.buct.adminbackend.enums.AutoReviewAction;
import com.buct.adminbackend.enums.ContentType;
import com.buct.adminbackend.enums.ReviewStatus;
import com.buct.adminbackend.repository.ReviewContentRepository;
import com.buct.adminbackend.repository.ReviewStrategyConfigRepository;
import com.buct.adminbackend.repository.SensitiveWordRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewContentRepository reviewContentRepository;
    private final SensitiveWordRepository sensitiveWordRepository;
    private final ReviewStrategyConfigRepository reviewStrategyConfigRepository;
    private final OperationLogService operationLogService;

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
        validateSensitiveWords(request);
        ReviewContent content = new ReviewContent();
        content.setContentType(request.contentType());
        content.setSourceSystem(request.sourceSystem());
        content.setSubmitter(request.submitter());
        content.setContentText(request.contentText());
        content.setContentUrl(request.contentUrl());
        content.setRiskScore(request.riskScore() == null ? 0 : request.riskScore());
        applyAutoReview(content);
        ReviewContent saved = reviewContentRepository.save(content);
        operationLogService.log(authentication.getName(), "CREATE_REVIEW_CONTENT", String.valueOf(saved.getId()),
                "新增内容，自动审核结果: " + saved.getReviewStatus());
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
    public ApiResponse<List<SensitiveWord>> listSensitiveWords() {
        return ApiResponse.ok(sensitiveWordRepository.findAll(Sort.by(Sort.Direction.ASC, "word")));
    }

    @PostMapping("/sensitive-words")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<SensitiveWord> createSensitiveWord(@RequestParam String word, Authentication authentication) {
        String w = normalizeWord(word);
        if (sensitiveWordRepository.existsByWord(w)) {
            throw new IllegalArgumentException("敏感词已存在");
        }
        SensitiveWord sw = new SensitiveWord();
        sw.setWord(w);
        sw.setEnabled(true);
        SensitiveWord saved = sensitiveWordRepository.save(sw);
        operationLogService.log(authentication.getName(), "CREATE_SENSITIVE_WORD", w, "新增敏感词");
        return ApiResponse.ok("新增成功", saved);
    }

    @PatchMapping("/sensitive-words/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<SensitiveWord> updateSensitiveWordStatus(
            @PathVariable Long id,
            @RequestParam boolean enabled,
            Authentication authentication) {
        SensitiveWord sw = sensitiveWordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("敏感词不存在"));
        sw.setEnabled(enabled);
        SensitiveWord saved = sensitiveWordRepository.save(sw);
        operationLogService.log(authentication.getName(), "UPDATE_SENSITIVE_WORD", saved.getWord(), "enabled=" + enabled);
        return ApiResponse.ok("更新成功", saved);
    }

    @DeleteMapping("/sensitive-words/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> deleteSensitiveWord(@PathVariable Long id, Authentication authentication) {
        SensitiveWord sw = sensitiveWordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("敏感词不存在"));
        sensitiveWordRepository.deleteById(id);
        operationLogService.log(authentication.getName(), "DELETE_SENSITIVE_WORD", sw.getWord(), "删除敏感词");
        return ApiResponse.ok("删除成功", null);
    }

    @GetMapping("/strategy")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<ReviewStrategyConfig> getStrategy() {
        return ApiResponse.ok(getOrCreateStrategy());
    }

    @PutMapping("/strategy")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<ReviewStrategyConfig> updateStrategy(@RequestBody ReviewStrategyConfig request, Authentication authentication) {
        if (request.getLowRiskThreshold() == null || request.getHighRiskThreshold() == null) {
            throw new IllegalArgumentException("风险阈值不能为空");
        }
        if (request.getLowRiskThreshold() < 0 || request.getHighRiskThreshold() > 100
                || request.getLowRiskThreshold() > request.getHighRiskThreshold()) {
            throw new IllegalArgumentException("风险阈值配置不合法");
        }
        ReviewStrategyConfig cfg = getOrCreateStrategy();
        cfg.setLowRiskThreshold(request.getLowRiskThreshold());
        cfg.setHighRiskThreshold(request.getHighRiskThreshold());
        cfg.setLowRiskAction(request.getLowRiskAction() == null ? AutoReviewAction.AUTO_APPROVE : request.getLowRiskAction());
        cfg.setHighRiskAction(request.getHighRiskAction() == null ? AutoReviewAction.MANUAL_REVIEW : request.getHighRiskAction());
        cfg.setImageViolationAction(request.getImageViolationAction() == null ? AutoReviewAction.AUTO_REJECT : request.getImageViolationAction());
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
        List<ReviewContent> reviewed = reviewContentRepository.findByReviewTimeBetweenOrderByReviewTimeDesc(start, end);
        Map<LocalDate, int[]> daily = new HashMap<>();
        Map<String, Integer> reviewerWorkload = new HashMap<>();
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
                reviewerWorkload.put(x.getReviewer(), reviewerWorkload.getOrDefault(x.getReviewer(), 0) + 1);
            }
        }
        List<Map<String, Object>> dailyStats = daily.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
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

    private void validateSensitiveWords(CreateReviewContentRequest request) {
        if (request.contentType() != ContentType.COMMENT && request.contentType() != ContentType.AUDIO) {
            return;
        }
        String text = request.contentText() == null ? "" : request.contentText();
        List<SensitiveWord> words = sensitiveWordRepository.findByEnabledTrueOrderByWordAsc();
        for (SensitiveWord w : words) {
            if (StringUtils.hasText(w.getWord()) && text.toLowerCase().contains(w.getWord().toLowerCase())) {
                throw new IllegalArgumentException("内容命中敏感词【" + w.getWord() + "】，请修改后提交");
            }
        }
    }

    private void applyAutoReview(ReviewContent content) {
        ReviewStrategyConfig cfg = getOrCreateStrategy();
        content.setAutoReviewed(true);
        if (content.getContentType() == ContentType.IMAGE && isImageViolation(content)) {
            applyAutoAction(content, cfg.getImageViolationAction(), "图片识别命中违规特征");
            return;
        }
        int risk = content.getRiskScore() == null ? 0 : content.getRiskScore();
        if (risk <= cfg.getLowRiskThreshold()) {
            applyAutoAction(content, cfg.getLowRiskAction(), "低风险自动审核");
            return;
        }
        if (risk >= cfg.getHighRiskThreshold()) {
            applyAutoAction(content, cfg.getHighRiskAction(), "高风险自动审核");
            return;
        }
        content.setReviewStatus(ReviewStatus.PENDING);
        content.setAutoDecisionNote("中风险，转人工审核");
        content.setReviewTime(null);
        content.setReviewer(null);
        content.setRejectReason(null);
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
