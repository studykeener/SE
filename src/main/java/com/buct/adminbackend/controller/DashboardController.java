package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.ReviewQueueItemResponse;
import com.buct.adminbackend.entity.Artifact;
import com.buct.adminbackend.entity.User;
import com.buct.adminbackend.entity.LoginLog;
import com.buct.adminbackend.repository.ArtifactRepository;
import com.buct.adminbackend.repository.CommentRepository;
import com.buct.adminbackend.repository.LoginLogRepository;
import com.buct.adminbackend.repository.UserRepository;
import com.buct.adminbackend.repository.UserUploadPhotoRepository;
import com.buct.adminbackend.security.PermissionCodes;
import com.buct.adminbackend.service.AuditLogService;
import com.buct.adminbackend.service.ReviewQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.*;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final UserRepository userRepository;
    private final ReviewQueueService reviewQueueService;
    private final ArtifactRepository artifactRepository;
    private final CommentRepository commentRepository;
    private final UserUploadPhotoRepository userUploadPhotoRepository;
    private final LoginLogRepository loginLogRepository;

    private static final List<String> ACCESS_SOURCES = List.of("web", "app");

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.STATS_VIEW + "')")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();
        long totalUsers = userRepository.count();
        long pendingReviews = reviewQueueService.countPending();
        long recheckReviews = reviewQueueService.countRecheck();
        long totalArtifacts = artifactRepository.count();

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long todayNewUsers = userRepository.countByRegisterTimeBetween(startOfDay, startOfDay.plusDays(1));

        data.put("totalUsers", totalUsers);
        data.put("todayNewUsers", todayNewUsers);
        data.put("pendingReviews", pendingReviews);
        data.put("recheckReviews", recheckReviews);
        data.put("queueBacklog", pendingReviews + recheckReviews);
        data.put("totalArtifacts", totalArtifacts);
        data.put("onlineUsers", countOnlinePlatformUsers(15));
        data.put("todayContentSubmissions", reviewQueueService.countTodaySubmissions());
        data.put("accessTrendDay", buildAccessTrend("DAY", 7));
        data.put("accessTrendWeek", buildAccessTrend("WEEK", 8));
        data.put("accessTrendMonth", buildAccessTrend("MONTH", 6));
        data.put("growthTrend", buildGrowthTrend(14));
        return ApiResponse.ok(data);
    }

    /** 近 N 分钟内登录过的前台用户（login_logs USER 成功登录，否则 user.last_login_at / 近期 UGC） */
    private long countOnlinePlatformUsers(int minutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(minutes);
        long byLoginLog = loginLogRepository.findAll().stream()
                .filter(x -> AuditLogService.USER_TYPE_PLATFORM.equalsIgnoreCase(x.getUserType()))
                .filter(x -> "SUCCESS".equalsIgnoreCase(x.getResult()))
                .filter(x -> x.getLoginTime() != null && !x.getLoginTime().isBefore(cutoff))
                .map(LoginLog::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        if (byLoginLog > 0) {
            return byLoginLog;
        }
        long byLastLogin = userRepository.countByLastLoginAtGreaterThanEqual(cutoff);
        if (byLastLogin > 0) {
            return byLastLogin;
        }
        return countDistinctRecentContentUsers(cutoff);
    }

    private long countDistinctRecentContentUsers(LocalDateTime cutoff) {
        Set<Long> userIds = new HashSet<>();
        commentRepository.findAll().stream()
                .filter(c -> c.getCreatedAt() != null && !c.getCreatedAt().isBefore(cutoff))
                .map(c -> c.getUserId())
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        userUploadPhotoRepository.findAll().stream()
                .filter(p -> p.getCreatedAt() != null && !p.getCreatedAt().isBefore(cutoff))
                .map(p -> p.getUserId())
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        return userIds.size();
    }

    /** 各子系统日/周/月登录人数（去重 user_id，来源 login_logs USER+SUCCESS） */
    private Map<String, Object> buildAccessTrend(String granularity, int periods) {
        List<LoginLog> platformLogins = loginLogRepository.findAll().stream()
                .filter(x -> AuditLogService.USER_TYPE_PLATFORM.equalsIgnoreCase(x.getUserType()))
                .filter(x -> "SUCCESS".equalsIgnoreCase(x.getResult()))
                .filter(x -> x.getUserId() != null)
                .toList();

        LocalDate now = LocalDate.now();
        List<String> labels = new ArrayList<>();
        List<LocalDate> periodStart = new ArrayList<>();
        if ("DAY".equals(granularity)) {
            for (int i = periods - 1; i >= 0; i--) {
                LocalDate d = now.minusDays(i);
                labels.add(d.toString());
                periodStart.add(d);
            }
        } else if ("WEEK".equals(granularity)) {
            WeekFields wf = WeekFields.ISO;
            for (int i = periods - 1; i >= 0; i--) {
                LocalDate d = now.minusWeeks(i).with(wf.dayOfWeek(), 1);
                labels.add(d.getYear() + "-W" + String.format("%02d", d.get(wf.weekOfWeekBasedYear())));
                periodStart.add(d);
            }
        } else {
            for (int i = periods - 1; i >= 0; i--) {
                LocalDate d = now.minusMonths(i).withDayOfMonth(1);
                labels.add(String.format("%04d-%02d", d.getYear(), d.getMonthValue()));
                periodStart.add(d);
            }
        }

        Map<String, List<Long>> series = new LinkedHashMap<>();
        for (String source : ACCESS_SOURCES) {
            List<Long> vals = new ArrayList<>();
            for (int i = 0; i < periodStart.size(); i++) {
                LocalDate start = periodStart.get(i);
                LocalDate end = (i + 1 < periodStart.size()) ? periodStart.get(i + 1) : advance(start, granularity);
                LocalDateTime startTime = start.atStartOfDay();
                LocalDateTime endTime = end.atStartOfDay();
                long count = platformLogins.stream()
                        .filter(x -> source.equalsIgnoreCase(resolveLoginSource(x)))
                        .filter(x -> x.getLoginTime() != null
                                && !x.getLoginTime().isBefore(startTime)
                                && x.getLoginTime().isBefore(endTime))
                        .map(LoginLog::getUserId)
                        .distinct()
                        .count();
                vals.add(count);
            }
            series.put(source, vals);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("labels", labels);
        out.put("series", series);
        return out;
    }

    private static String resolveLoginSource(LoginLog log) {
        return log.getSourceSystem();
    }

    private Map<String, Object> buildGrowthTrend(int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        List<User> users = userRepository.findAll();
        List<ReviewQueueItemResponse> contents = reviewQueueService.streamAllForTrend().toList();
        List<Artifact> artifacts = artifactRepository.findAll();
        List<String> labels = new ArrayList<>();
        List<Long> userVals = new ArrayList<>();
        List<Long> contentVals = new ArrayList<>();
        List<Long> artifactVals = new ArrayList<>();
        long u = 0, c = 0, a = 0;
        for (int i = 0; i < days; i++) {
            LocalDate d = start.plusDays(i);
            labels.add(d.toString());
            u += users.stream().filter(x -> x.getRegisterTime() != null && x.getRegisterTime().toLocalDate().equals(d)).count();
            c += contents.stream().filter(x -> x.submitTime() != null && x.submitTime().toLocalDate().equals(d)).count();
            a += artifacts.stream().filter(x -> x.getCrawlDate() != null && x.getCrawlDate().equals(d)).count();
            userVals.add(u);
            contentVals.add(c);
            artifactVals.add(a);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("labels", labels);
        out.put("users", userVals);
        out.put("contents", contentVals);
        out.put("artifacts", artifactVals);
        return out;
    }

    private LocalDate advance(LocalDate start, String granularity) {
        if ("DAY".equals(granularity)) return start.plusDays(1);
        if ("WEEK".equals(granularity)) return start.plusWeeks(1);
        return start.plusMonths(1);
    }
}
