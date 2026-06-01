package contentreview;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 与子系统5 admin-backend {@code ReviewQueueService#computeRisk} /
 * {@code applyAutoReviewToComment} / {@code applyAutoReviewToPhoto} 保持一致。
 * <p>
 * 无 Spring 依赖，Web/App 子系统可直接复制本文件（改 package 即可）。
 */
public final class ContentReviewEngine {

    private ContentReviewEngine() {
    }

    public enum SensitiveLevel {
        LIGHT,
        SEVERE
    }

    public enum AutoReviewAction {
        AUTO_APPROVE,
        AUTO_REJECT,
        MANUAL_REVIEW
    }

    public enum ReviewDecision {
        INSERT_APPROVED,
        INSERT_PENDING,
        REJECT
    }

    public record SensitiveWord(String word, SensitiveLevel level) {
    }

    public record ReviewStrategy(
            int lowRiskMaxScore,
            int mediumRiskMaxScore,
            AutoReviewAction lowRiskAction,
            AutoReviewAction mediumRiskAction,
            AutoReviewAction highRiskAction
    ) {
        public static ReviewStrategy defaults() {
            return new ReviewStrategy(
                    20, 60,
                    AutoReviewAction.AUTO_APPROVE,
                    AutoReviewAction.MANUAL_REVIEW,
                    AutoReviewAction.AUTO_REJECT
            );
        }
    }

    public record RiskResult(int score, String hits) {
    }

    public record CommentReviewResult(
            ReviewDecision decision,
            int auditStatus,
            int auditMethod,
            int autoAuditStatus,
            String sensitiveWordsHit,
            String userMessage
    ) {
    }

    public record PhotoReviewResult(
            int status,
            int auditMethod,
            int autoAuditStatus,
            BigDecimal autoAuditScore,
            String sensitiveWordsHit,
            String userMessage
    ) {
    }

    /**
     * 与 {@code ReviewQueueService#normalizeSource} 一致。
     */
    public static String normalizeSource(String sourceSystem) {
        if (sourceSystem == null || sourceSystem.isBlank()) {
            return "web";
        }
        return "app".equalsIgnoreCase(sourceSystem.trim()) ? "app" : "web";
    }

    /**
     * 与 {@code ReviewQueueService#computeRisk} 一致。
     *
     * @param externalImageScore 可选；admin-backend 本地 ONNX 打分时传入，Web 无模型可传 null
     */
    public static RiskResult computeRisk(
            String text,
            String url,
            boolean image,
            List<SensitiveWord> enabledWords,
            Integer externalImageScore
    ) {
        List<SensitiveWord> words = enabledWords == null ? List.of() : enabledWords.stream()
                .filter(w -> w.word() != null && !w.word().isBlank())
                .sorted(Comparator.comparing(SensitiveWord::word))
                .toList();

        String allText = ((text == null ? "" : text) + " " + (url == null ? "" : url)).toLowerCase(Locale.ROOT);
        List<String> hitWords = new ArrayList<>();
        int lightHits = 0;

        for (SensitiveWord w : words) {
            int hits = countOccurrences(allText, w.word().toLowerCase(Locale.ROOT));
            if (hits <= 0) {
                continue;
            }
            hitWords.add(w.word());
            if (w.level() == SensitiveLevel.SEVERE) {
                return new RiskResult(100, joinHits(hitWords));
            }
            lightHits += hits;
        }

        int score = lightHits * 10;

        if (image && externalImageScore != null) {
            score = Math.max(score, externalImageScore);
        }

        if (image && containsAny(allText,
                "childporn", "terror", "爆炸物", "恋童", "极端暴力", "严重违规")) {
            return new RiskResult(100, joinHits(hitWords));
        }
        if (image && containsAny(allText, "violence", "porn", "bloody", "涉黄", "暴力", "违规")) {
            score += 10;
        }

        return new RiskResult(clamp(score), joinHits(hitWords));
    }

    /**
     * 评论：与 {@code applyAutoReviewToComment} + 拒绝不入库 一致。
     */
    public static CommentReviewResult reviewComment(int riskScore, ReviewStrategy strategy) {
        ReviewStrategy cfg = strategy == null ? ReviewStrategy.defaults() : strategy;
        AutoReviewAction action = resolveAction(riskScore, cfg);

        if (action == AutoReviewAction.AUTO_REJECT) {
            return new CommentReviewResult(
                    ReviewDecision.REJECT,
                    2, 1, clamp(riskScore), null,
                    "内容违规无法发布，请修改后重试"
            );
        }
        if (action == AutoReviewAction.AUTO_APPROVE) {
            return new CommentReviewResult(
                    ReviewDecision.INSERT_APPROVED,
                    1, 3, clamp(riskScore), null,
                    "审核通过，可以展示"
            );
        }
        return new CommentReviewResult(
                ReviewDecision.INSERT_PENDING,
                0, 1, clamp(riskScore), null,
                "已提交，等待人工审核"
        );
    }

    /**
     * 评论完整流程（含敏感词命中文案）。
     */
    public static CommentReviewResult submitComment(
            String content,
            List<SensitiveWord> enabledWords,
            ReviewStrategy strategy
    ) {
        RiskResult risk = computeRisk(content, null, false, enabledWords, null);
        CommentReviewResult base = reviewComment(risk.score(), strategy);
        String hits = risk.hits();
        String message = base.userMessage();
        if (base.decision() == ReviewDecision.REJECT && hits != null && !hits.isBlank()) {
            message = "内容违规无法发布，请修改后重试（命中：" + hits + "）";
        } else if (base.decision() == ReviewDecision.INSERT_PENDING) {
            message = "已提交，等待人工审核";
        } else if (base.decision() == ReviewDecision.INSERT_APPROVED) {
            message = "审核通过，可以展示";
        }
        return new CommentReviewResult(
                base.decision(),
                base.auditStatus(),
                base.auditMethod(),
                clamp(risk.score()),
                hits,
                message
        );
    }

    /**
     * 图片：与 {@code applyAutoReviewToPhoto} 一致，一律待人工审核。
     */
    public static PhotoReviewResult submitPhoto(
            String description,
            String photoUrl,
            List<SensitiveWord> enabledWords,
            Integer externalImageScore
    ) {
        String text = (description != null && !description.isBlank()) ? description : photoUrl;
        RiskResult risk = computeRisk(text, photoUrl, true, enabledWords, externalImageScore);
        int score = clamp(risk.score());
        int autoAuditStatus = score >= 60 ? 2 : 1;
        BigDecimal autoAuditScore = BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);

        return new PhotoReviewResult(
                0,
                2,
                autoAuditStatus,
                autoAuditScore,
                risk.hits(),
                "已提交，等待人工审核"
        );
    }

    private static AutoReviewAction resolveAction(int riskScore, ReviewStrategy cfg) {
        if (riskScore <= cfg.lowRiskMaxScore()) {
            return cfg.lowRiskAction();
        }
        if (riskScore <= cfg.mediumRiskMaxScore()) {
            return cfg.mediumRiskAction();
        }
        return cfg.highRiskAction();
    }

    private static ReviewStrategy parseStrategy(
            Integer lowMax,
            Integer mediumMax,
            String lowAction,
            String mediumAction,
            String highAction
    ) {
        ReviewStrategy defaults = ReviewStrategy.defaults();
        return new ReviewStrategy(
                Optional.ofNullable(lowMax).orElse(defaults.lowRiskMaxScore()),
                Optional.ofNullable(mediumMax).orElse(defaults.mediumRiskMaxScore()),
                parseAction(lowAction, defaults.lowRiskAction()),
                parseAction(mediumAction, defaults.mediumRiskAction()),
                parseAction(highAction, defaults.highRiskAction())
        );
    }

    /** 从 JDBC 查询结果构造策略。 */
    public static ReviewStrategy strategyFromDb(
            Integer lowMax,
            Integer mediumMax,
            String lowAction,
            String mediumAction,
            String highAction
    ) {
        return parseStrategy(lowMax, mediumMax, lowAction, mediumAction, highAction);
    }

    private static AutoReviewAction parseAction(String raw, AutoReviewAction fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return AutoReviewAction.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
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
        if (text == null || text.isEmpty() || keyword == null || keyword.isEmpty()) {
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

    private static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static String joinHits(List<String> hitWords) {
        if (hitWords == null || hitWords.isEmpty()) {
            return null;
        }
        return String.join(",", hitWords);
    }
}
