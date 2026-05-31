package com.buct.adminbackend.service;

import com.buct.adminbackend.dto.CreateReviewContentRequest;
import com.buct.adminbackend.dto.ReviewQueueItemResponse;
import com.buct.adminbackend.entity.*;
import com.buct.adminbackend.enums.AutoReviewAction;
import com.buct.adminbackend.enums.ContentType;
import com.buct.adminbackend.enums.ReviewStatus;
import com.buct.adminbackend.enums.SensitiveWordLevel;
import com.buct.adminbackend.repository.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 内容审核队列核心服务。
 * <p>
 * 职责：
 * 1. 接收内容提交（评论/照片），计算风险分，执行自动审核策略
 * 2. 提供人工审核操作（通过/拒绝/复审）
 * 3. 待审队列查询（支持多条件筛选）
 * 4. 审核统计数据
 * <p>
 * 待审队列不使用独立表，直接读写共用表 comment 和 user_upload_photo。
 * 图片审核已调整为全人工审核通道，评论仍按策略自动审核。
 */
@Service
@RequiredArgsConstructor
public class ReviewQueueService {

    /** 评论来源表标识 */
    public static final String TABLE_COMMENT = "comment";
    /** 照片来源表标识 */
    public static final String TABLE_PHOTO = "user_upload_photo";

    private final CommentRepository commentRepository;
    private final UserUploadPhotoRepository userUploadPhotoRepository;
    private final UserRepository userRepository;
    private final AdminUserRepository adminUserRepository;
    private final SensitiveWordRepository sensitiveWordRepository;
    private final ReviewStrategyConfigRepository reviewStrategyConfigRepository;
    /** 图片审核服务（三级降级：阿里云→本地ONNX模型→关键词模拟） */
    private final ImageModerationService imageModerationService;

    /**
     * 查询待审队列，合并 comment + user_upload_photo 两张表，支持多条件筛选。
     * 筛选条件：审核状态、内容类型、来源系统、提交者、关键词、时间范围、风险分范围。
     */
    public List<ReviewQueueItemResponse> list(
            ReviewStatus status,
            ContentType contentType,
            String sourceSystem,
            String submitter,
            String keyword,
            LocalDateTime submitFrom,
            LocalDateTime submitTo,
            Integer riskMin,
            Integer riskMax) {
        List<ReviewQueueItemResponse> items = new ArrayList<>();
        if (contentType == null || contentType == ContentType.COMMENT) {
            items.addAll(commentRepository.findAll(buildCommentSpec(status, sourceSystem, submitter, keyword, submitFrom, submitTo))
                    .stream().map(this::toCommentItem).toList());
        }
        if (contentType == null || contentType == ContentType.IMAGE) {
            items.addAll(userUploadPhotoRepository.findAll(buildPhotoSpec(status, sourceSystem, submitter, keyword, submitFrom, submitTo))
                    .stream().map(this::toPhotoItem).toList());
        }
        return items.stream()
                .filter(x -> matchRisk(x.riskScore(), riskMin, riskMax))
                .sorted(Comparator.comparing(ReviewQueueItemResponse::submitTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional
    public ReviewQueueItemResponse createTestContent(CreateReviewContentRequest request) {
        if (request.contentType() == ContentType.COMMENT) {
            return createTestComment(request);
        }
        if (request.contentType() == ContentType.IMAGE) {
            return createTestPhoto(request);
        }
        throw new IllegalArgumentException("当前仅支持评论(comment)与上传照片(user_upload_photo)的审核");
    }

    /**
     * 队友系统提交评论（集成 API 入口）。
     * 流程：校验用户→计算风险分→应用自动审核策略→保存。
     * 若高风险且策略为AUTO_REJECT，直接抛异常拦截发布。
     */
    @Transactional
    public ReviewQueueItemResponse submitComment(Long userId, Integer museumId, String objectId, String content, String source) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
        CreateReviewContentRequest request = new CreateReviewContentRequest(
                ContentType.COMMENT,
                source,
                null,
                content,
                null,
                userId,
                museumId,
                objectId
        );
        return createTestComment(request);
    }

    /**
     * 队友系统提交照片（集成 API 入口）。
     * 流程：校验用户→计算风险分→全部进入人工审核队列。
     * 注意：图片审核已改为全人工通道，风险分仅供审核员参考。
     */
    @Transactional
    public ReviewQueueItemResponse submitPhoto(Long userId, String photoUrl, String description,
                                               Integer museumId, String objectId, String source) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
        String text = StringUtils.hasText(description) ? description : photoUrl;
        CreateReviewContentRequest request = new CreateReviewContentRequest(
                ContentType.IMAGE,
                source,
                null,
                text,
                photoUrl,
                userId,
                museumId,
                objectId
        );
        return createTestPhoto(request);
    }

    @Transactional
    public ReviewQueueItemResponse review(String sourceTable, Long id, ReviewStatus reviewStatus,
                                          String rejectReason, String operatorName, Long operatorId) {
        validateReviewAction(reviewStatus, rejectReason);
        if (TABLE_COMMENT.equals(sourceTable)) {
            Comment comment = commentRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("评论不存在"));
            comment.setAuditStatus(reviewStatus);
            comment.setAuditorId(operatorId);
            comment.setAuditMethod((byte) 2);
            if (reviewStatus == ReviewStatus.REJECTED || reviewStatus == ReviewStatus.RECHECK) {
                comment.setDeleteReason(rejectReason);
            } else {
                comment.setDeleteReason(null);
            }
            return toCommentItem(commentRepository.save(comment));
        }
        if (TABLE_PHOTO.equals(sourceTable)) {
            UserUploadPhoto photo = userUploadPhotoRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("上传照片不存在"));
            photo.setStatus(reviewStatus);
            photo.setAuditorId(operatorId);
            photo.setAuditMethod((byte) 2);
            if (reviewStatus == ReviewStatus.REJECTED || reviewStatus == ReviewStatus.RECHECK) {
                photo.setRejectReason(rejectReason);
            } else {
                photo.setRejectReason(null);
            }
            return toPhotoItem(userUploadPhotoRepository.save(photo));
        }
        throw new IllegalArgumentException("不支持的审核来源表: " + sourceTable);
    }

    public long countPending() {
        return commentRepository.countByAuditStatus(ReviewStatus.PENDING)
                + userUploadPhotoRepository.countByStatus(ReviewStatus.PENDING);
    }

    public long countRecheck() {
        return commentRepository.countByAuditStatus(ReviewStatus.RECHECK)
                + userUploadPhotoRepository.countByStatus(ReviewStatus.RECHECK);
    }

    public long countTodaySubmissions() {
        LocalDateTime start = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        long comments = commentRepository.findAll(buildCommentSpec(null, null, null, null, start, end)).size();
        long photos = userUploadPhotoRepository.findAll(buildPhotoSpec(null, null, null, null, start, end)).size();
        return comments + photos;
    }

    public ReviewQueueItemResponse getBySource(String sourceTable, Long id) {
        if (TABLE_COMMENT.equals(sourceTable)) {
            return commentRepository.findById(id).map(this::toCommentItem)
                    .orElseThrow(() -> new IllegalArgumentException("评论不存在"));
        }
        if (TABLE_PHOTO.equals(sourceTable)) {
            return userUploadPhotoRepository.findById(id).map(this::toPhotoItem)
                    .orElseThrow(() -> new IllegalArgumentException("上传照片不存在"));
        }
        throw new IllegalArgumentException("不支持的审核来源表: " + sourceTable);
    }

    public List<ReviewQueueItemResponse> listReviewedBetween(LocalDateTime from, LocalDateTime to) {
        List<ReviewStatus> done = List.of(ReviewStatus.APPROVED, ReviewStatus.REJECTED, ReviewStatus.RECHECK);
        List<ReviewQueueItemResponse> items = new ArrayList<>();
        items.addAll(commentRepository.findByUpdatedAtBetweenAndAuditStatusInOrderByUpdatedAtDesc(from, to, done)
                .stream().map(this::toCommentItem).toList());
        items.addAll(userUploadPhotoRepository.findByUpdatedAtBetweenAndStatusInOrderByUpdatedAtDesc(from, to, done)
                .stream().map(this::toPhotoItem).toList());
        return items.stream()
                .sorted(Comparator.comparing(ReviewQueueItemResponse::reviewTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public Stream<ReviewQueueItemResponse> streamAllForTrend() {
        return Stream.concat(
                commentRepository.findAll().stream().map(this::toCommentItem),
                userUploadPhotoRepository.findAll().stream().map(this::toPhotoItem)
        );
    }

    private ReviewQueueItemResponse createTestComment(CreateReviewContentRequest request) {
        Long userId = resolveUserId(request);
        if (request.museumId() == null) {
            throw new IllegalArgumentException("评论测试数据需填写馆别 museumId");
        }
        if (!StringUtils.hasText(request.objectId())) {
            throw new IllegalArgumentException("评论测试数据需填写文物编号 objectId");
        }
        Comment comment = new Comment();
        comment.setUserId(userId);
        comment.setMuseumId(request.museumId());
        comment.setObjectId(request.objectId().trim());
        comment.setContent(request.contentText().trim());
        comment.setSource(normalizeSource(request.sourceSystem()));
        RiskResult risk = computeRisk(request.contentText(), request.contentUrl(), false);
        comment.setSensitiveWordsHit(risk.hits());
        comment.setAutoAuditStatus((byte) Math.min(127, risk.score()));
        applyAutoReviewToComment(comment, risk.score());
        if (comment.getAuditStatus() == ReviewStatus.REJECTED) {
            throw blockedException(risk);
        }
        return toCommentItem(commentRepository.save(comment));
    }

    private ReviewQueueItemResponse createTestPhoto(CreateReviewContentRequest request) {
        Long userId = resolveUserId(request);
        String photoUrl = StringUtils.hasText(request.contentUrl()) ? request.contentUrl().trim() : request.contentText().trim();
        if (!StringUtils.hasText(photoUrl)) {
            throw new IllegalArgumentException("照片测试数据需填写图片 URL");
        }
        UserUploadPhoto photo = new UserUploadPhoto();
        photo.setUserId(userId);
        photo.setMuseumId(request.museumId());
        photo.setObjectId(StringUtils.hasText(request.objectId()) ? request.objectId().trim() : null);
        photo.setPhotoUrl(photoUrl);
        photo.setDescription(StringUtils.hasText(request.contentUrl()) ? request.contentText().trim() : null);
        photo.setSource(normalizeSource(request.sourceSystem()));
        RiskResult risk = computeRisk(request.contentText(), photoUrl, true);
        photo.setAutoAuditScore(BigDecimal.valueOf(risk.score()));
        photo.setAutoAuditStatus((byte) (risk.score() >= 60 ? 2 : 1));
        applyAutoReviewToPhoto(photo, risk.score());
        return toPhotoItem(userUploadPhotoRepository.save(photo));
    }

    /**
     * 对评论应用自动审核策略。
     * 根据风险分与策略阈值决定：
     * - 低风险(≤20分) → AUTO_APPROVE（自动通过，auditMethod=3）
     * - 中风险(21~60分) → MANUAL_REVIEW（转人工，状态PENDING）
     * - 高风险(>60分) → AUTO_REJECT（自动拒绝）
     */
    private void applyAutoReviewToComment(Comment comment, int riskScore) {
        ReviewStrategyConfig cfg = getOrCreateStrategy();
        comment.setAuditMethod((byte) 1); // 1 = 自动审核
        ReviewStatus result = resolveAutoStatus(riskScore, cfg);
        comment.setAuditStatus(result);
        if (result == ReviewStatus.REJECTED) {
            comment.setDeleteReason("自动审核拦截");
        }
        if (result == ReviewStatus.APPROVED) {
            comment.setAuditMethod((byte) 3); // 3 = 自动通过
        }
    }

    /**
     * 对照片应用审核策略 —— 全部走人工审核通道。
     * 不论风险分高低，图片一律进入人工审核队列（status=PENDING）。
     * 风险分保存在 auto_audit_score 字段供审核员参考。
     */
    private void applyAutoReviewToPhoto(UserUploadPhoto photo, int riskScore) {
        photo.setAuditMethod((byte) 2); // 2 = 人工审核
        photo.setStatus(ReviewStatus.PENDING);
    }

    private ReviewStatus resolveAutoStatus(int riskScore, ReviewStrategyConfig cfg) {
        AutoReviewAction action;
        if (riskScore <= cfg.getLowRiskMaxScore()) {
            action = cfg.getLowRiskAction();
        } else if (riskScore <= cfg.getMediumRiskMaxScore()) {
            action = cfg.getMediumRiskAction();
        } else {
            action = cfg.getHighRiskAction();
        }
        if (action == AutoReviewAction.AUTO_APPROVE) {
            return ReviewStatus.APPROVED;
        }
        if (action == AutoReviewAction.AUTO_REJECT) {
            return ReviewStatus.REJECTED;
        }
        return ReviewStatus.PENDING;
    }

    private ReviewQueueItemResponse toCommentItem(Comment comment) {
        int risk = comment.getAutoAuditStatus() == null ? computeRisk(comment.getContent(), null, false).score() : Byte.toUnsignedInt(comment.getAutoAuditStatus());
        boolean auto = comment.getAuditMethod() != null && (comment.getAuditMethod() == 1 || comment.getAuditMethod() == 3);
        LocalDateTime reviewTime = comment.getAuditStatus() == ReviewStatus.PENDING ? null : comment.getUpdatedAt();
        return new ReviewQueueItemResponse(
                comment.getId(),
                TABLE_COMMENT,
                ContentType.COMMENT,
                comment.getSource(),
                resolveUsername(comment.getUserId()),
                comment.getUserId(),
                comment.getMuseumId(),
                comment.getObjectId(),
                comment.getContent(),
                null,
                comment.getAuditStatus(),
                risk,
                comment.getCreatedAt(),
                reviewTime,
                resolveReviewerName(comment.getAuditorId()),
                comment.getDeleteReason(),
                auto,
                auto ? "自动审核" : "人工审核"
        );
    }

    private ReviewQueueItemResponse toPhotoItem(UserUploadPhoto photo) {
        int risk = photo.getAutoAuditScore() == null ? 0 : photo.getAutoAuditScore().intValue();
        boolean auto = photo.getAuditMethod() != null && (photo.getAuditMethod() == 1 || photo.getAuditMethod() == 3);
        LocalDateTime reviewTime = photo.getStatus() == ReviewStatus.PENDING ? null : photo.getUpdatedAt();
        return new ReviewQueueItemResponse(
                photo.getId(),
                TABLE_PHOTO,
                ContentType.IMAGE,
                photo.getSource(),
                resolveUsername(photo.getUserId()),
                photo.getUserId(),
                photo.getMuseumId(),
                photo.getObjectId(),
                photo.getDescription(),
                photo.getPhotoUrl(),
                photo.getStatus(),
                risk,
                photo.getCreatedAt(),
                reviewTime,
                resolveReviewerName(photo.getAuditorId()),
                photo.getRejectReason(),
                auto,
                auto ? "自动审核" : "人工审核"
        );
    }

    private Long resolveUserId(CreateReviewContentRequest request) {
        if (request.userId() != null) {
            if (!userRepository.existsById(request.userId())) {
                throw new IllegalArgumentException("用户不存在: " + request.userId());
            }
            return request.userId();
        }
        if (StringUtils.hasText(request.submitter())) {
            return userRepository.findByUsername(request.submitter().trim())
                    .map(User::getId)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + request.submitter()));
        }
        throw new IllegalArgumentException("请填写用户ID或提交人用户名");
    }

    private String resolveUsername(Long userId) {
        if (userId == null) {
            return "";
        }
        return userRepository.findById(userId).map(User::getUsername).orElse("用户#" + userId);
    }

    private String resolveReviewerName(Long auditorId) {
        if (auditorId == null) {
            return null;
        }
        return adminUserRepository.findById(auditorId).map(AdminUser::getUsername).orElse("管理员#" + auditorId);
    }

    private Specification<Comment> buildCommentSpec(
            ReviewStatus status, String sourceSystem, String submitter, String keyword,
            LocalDateTime submitFrom, LocalDateTime submitTo) {
        Set<Long> userIds = resolveUserIdsBySubmitter(submitter);
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (status != null) {
                preds.add(cb.equal(root.get("auditStatus"), status));
            }
            if (StringUtils.hasText(sourceSystem)) {
                preds.add(cb.equal(cb.lower(root.get("source")), normalizeSource(sourceSystem)));
            }
            if (userIds != null) {
                preds.add(root.get("userId").in(userIds));
            }
            if (StringUtils.hasText(keyword)) {
                String kw = "%" + keyword.trim().toLowerCase() + "%";
                preds.add(cb.like(cb.lower(root.get("content")), kw));
            }
            if (submitFrom != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), submitFrom));
            }
            if (submitTo != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), submitTo));
            }
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        };
    }

    private Specification<UserUploadPhoto> buildPhotoSpec(
            ReviewStatus status, String sourceSystem, String submitter, String keyword,
            LocalDateTime submitFrom, LocalDateTime submitTo) {
        Set<Long> userIds = resolveUserIdsBySubmitter(submitter);
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (status != null) {
                preds.add(cb.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(sourceSystem)) {
                preds.add(cb.equal(cb.lower(root.get("source")), normalizeSource(sourceSystem)));
            }
            if (userIds != null) {
                preds.add(root.get("userId").in(userIds));
            }
            if (StringUtils.hasText(keyword)) {
                String kw = "%" + keyword.trim().toLowerCase() + "%";
                preds.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("description"), cb.literal(""))), kw),
                        cb.like(cb.lower(root.get("photoUrl")), kw)
                ));
            }
            if (submitFrom != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), submitFrom));
            }
            if (submitTo != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), submitTo));
            }
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        };
    }

    private Set<Long> resolveUserIdsBySubmitter(String submitter) {
        if (!StringUtils.hasText(submitter)) {
            return null;
        }
        String kw = "%" + submitter.trim().toLowerCase() + "%";
        Set<Long> ids = userRepository.findAll().stream()
                .filter(u -> u.getUsername() != null && u.getUsername().toLowerCase().contains(submitter.trim().toLowerCase()))
                .map(User::getId)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? Set.of(-1L) : ids;
    }

    private static boolean matchRisk(Integer risk, Integer riskMin, Integer riskMax) {
        int value = risk == null ? 0 : risk;
        if (riskMin != null && value < riskMin) {
            return false;
        }
        if (riskMax != null && value > riskMax) {
            return false;
        }
        return true;
    }

    private static void validateReviewAction(ReviewStatus reviewStatus, String rejectReason) {
        if (reviewStatus == ReviewStatus.PENDING) {
            throw new IllegalArgumentException("审核动作不能设置为 PENDING");
        }
        if ((reviewStatus == ReviewStatus.REJECTED || reviewStatus == ReviewStatus.RECHECK)
                && !StringUtils.hasText(rejectReason)) {
            throw new IllegalArgumentException("拒绝或复审时必须填写原因");
        }
    }

    private static String normalizeSource(String sourceSystem) {
        if (!StringUtils.hasText(sourceSystem)) {
            return "web";
        }
        String s = sourceSystem.trim().toLowerCase();
        return "app".equals(s) ? "app" : "web";
    }

    /**
     * 计算内容风险分（核心算法）。
     * <p>
     * 算法流程：
     * 1. 遍历启用的敏感词库，匹配文本中的敏感词
     *    - 命中 SEVERE 级别 → 直接返回 100 分
     *    - 命中 LIGHT 级别 → 每次 +10 分
     * 2. 若为图片内容，调用 ImageModerationService 获取图片风险分，取最大值
     * 3. 检查高危关键词（childporn/terror/恋童等）→ 100分
     * 4. 检查中危关键词（porn/violence/涉黄等）→ +10分
     * 5. 最终分数范围 0~100
     *
     * @param text  文本内容
     * @param url   图片URL（仅图片类型有值）
     * @param image 是否为图片类型
     * @return RiskResult(score, hitWords)
     */
    private RiskResult computeRisk(String text, String url, boolean image) {
        List<SensitiveWord> words = sensitiveWordRepository.findByEnabledTrueOrderByWordAsc();
        String allText = ((text == null ? "" : text) + " " + (url == null ? "" : url)).toLowerCase();
        List<String> hitWords = new ArrayList<>();
        int lightHits = 0;
        for (SensitiveWord w : words) {
            if (!StringUtils.hasText(w.getWord())) {
                continue;
            }
            int hits = countOccurrences(allText, w.getWord().toLowerCase());
            if (hits <= 0) {
                continue;
            }
            hitWords.add(w.getWord());
            if (w.getLevel() == SensitiveWordLevel.SEVERE) {
                return new RiskResult(100, String.join(",", hitWords));
            }
            lightHits += hits;
        }
        int score = lightHits * 10;
        // 图片审核：调用本地 NSFW 模型评分
        if (image && StringUtils.hasText(url)) {
            try {
                int imageScore = imageModerationService.scoreImage(url, text);
                score = Math.max(score, imageScore);
            } catch (Exception e) {
                // 模型审核失败时不影响关键词审核结果
            }
        }
        if (image && containsAny(allText, "childporn", "terror", "爆炸物", "恋童", "极端暴力", "严重违规")) {
            return new RiskResult(100, String.join(",", hitWords));
        }
        if (image && containsAny(allText, "violence", "porn", "bloody", "涉黄", "暴力", "违规")) {
            score += 10;
        }
        return new RiskResult(Math.min(100, Math.max(0, score)), hitWords.isEmpty() ? null : String.join(",", hitWords));
    }

    private static boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static int countOccurrences(String text, String keyword) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(keyword)) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while (true) {
            int found = text.indexOf(keyword, idx);
            if (found < 0) {
                break;
            }
            count++;
            idx = found + keyword.length();
        }
        return count;
    }

    private static IllegalArgumentException blockedException(RiskResult risk) {
        if (StringUtils.hasText(risk.hits())) {
            return new IllegalArgumentException("内容违规无法发布，请修改后重试（命中：" + risk.hits() + "）");
        }
        return new IllegalArgumentException("内容违规无法发布，请修改后重试");
    }

    private ReviewStrategyConfig getOrCreateStrategy() {
        return reviewStrategyConfigRepository.findById(1L).orElseGet(() -> {
            ReviewStrategyConfig cfg = new ReviewStrategyConfig();
            cfg.setId(1L);
            return reviewStrategyConfigRepository.save(cfg);
        });
    }

    private record RiskResult(int score, String hits) {
    }
}
