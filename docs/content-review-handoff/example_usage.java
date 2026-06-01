package contentreview;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Web/App 提交评论示例（JDBC）。
 * 复制 ContentReviewEngine.java 与本类到你们项目后改 package、DB 连接即可。
 */
public class ExampleUsage {

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://47.96.152.190:3306/overseas_chinese_artifacts?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
                "your_user", "your_password")) {

            long userId = 1001L;
            int museumId = 1;
            String objectId = "ld1-1643381040022-1643381041048-0";
            String content = "这个文物很精美！";
            String source = ContentReviewEngine.normalizeSource("web");

            if (!userExists(conn, userId)) {
                throw new IllegalArgumentException("用户不存在: " + userId);
            }

            List<ContentReviewEngine.SensitiveWord> words = loadSensitiveWords(conn);
            ContentReviewEngine.ReviewStrategy strategy = loadStrategy(conn);

            ContentReviewEngine.RiskResult risk = ContentReviewEngine.computeRisk(
                    content, null, false, words, null);
            ContentReviewEngine.CommentReviewResult result = ContentReviewEngine.submitComment(
                    content, words, strategy);

            if (result.decision() == ContentReviewEngine.ReviewDecision.REJECT) {
                // 与 integration API 一致：拒绝则不入库
                System.out.println(result.userMessage());
                return;
            }

            String sql = """
                    INSERT INTO comment (
                      user_id, museum_id, object_id, content, source,
                      audit_method, audit_status, auto_audit_status, sensitive_words_hit, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, userId);
                ps.setInt(2, museumId);
                ps.setString(3, objectId);
                ps.setString(4, content);
                ps.setString(5, source);
                ps.setInt(6, result.auditMethod());
                ps.setInt(7, result.auditStatus());
                ps.setInt(8, result.autoAuditStatus());
                ps.setString(9, result.sensitiveWordsHit());
                ps.executeUpdate();
            }

            System.out.println(result.userMessage());
        }
    }

    private static boolean userExists(Connection conn, long userId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM user WHERE user_id = ? LIMIT 1")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static List<ContentReviewEngine.SensitiveWord> loadSensitiveWords(Connection conn) throws Exception {
        List<ContentReviewEngine.SensitiveWord> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT word, level FROM sensitive_words WHERE enabled = 1 ORDER BY word ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String level = rs.getString("level");
                ContentReviewEngine.SensitiveLevel lv =
                        "SEVERE".equalsIgnoreCase(level)
                                ? ContentReviewEngine.SensitiveLevel.SEVERE
                                : ContentReviewEngine.SensitiveLevel.LIGHT;
                list.add(new ContentReviewEngine.SensitiveWord(rs.getString("word"), lv));
            }
        }
        return list;
    }

    private static ContentReviewEngine.ReviewStrategy loadStrategy(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT low_risk_max_score, medium_risk_max_score,
                       low_risk_action, medium_risk_action, high_risk_action
                FROM review_strategy_config WHERE id = 1
                """);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return ContentReviewEngine.ReviewStrategy.defaults();
            }
            return ContentReviewEngine.strategyFromDb(
                    rs.getInt("low_risk_max_score"),
                    rs.getInt("medium_risk_max_score"),
                    rs.getString("low_risk_action"),
                    rs.getString("medium_risk_action"),
                    rs.getString("high_risk_action")
            );
        }
    }
}
