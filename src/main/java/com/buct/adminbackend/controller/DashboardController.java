package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.entity.LoginLog;
import com.buct.adminbackend.entity.PlatformUser;
import com.buct.adminbackend.entity.ReviewContent;
import com.buct.adminbackend.entity.Artifact;
import com.buct.adminbackend.repository.ArtifactRepository;
import com.buct.adminbackend.repository.LoginLogRepository;
import com.buct.adminbackend.repository.PlatformUserRepository;
import com.buct.adminbackend.enums.ReviewStatus;
import com.buct.adminbackend.repository.ReviewContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final PlatformUserRepository platformUserRepository;
    private final ReviewContentRepository reviewContentRepository;
    private final ArtifactRepository artifactRepository;
    private final LoginLogRepository loginLogRepository;

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();
        long totalUsers = platformUserRepository.count();
        long pendingReviews = reviewContentRepository.countByReviewStatus(ReviewStatus.PENDING);
        long recheckReviews = reviewContentRepository.countByReviewStatus(ReviewStatus.RECHECK);
        long totalArtifacts = artifactRepository.count();

        long todayNewUsers = platformUserRepository.findAll().stream()
                .filter(x -> x.getCreatedAt() != null && x.getCreatedAt().toLocalDate().equals(LocalDate.now()))
                .count();

        data.put("totalUsers", totalUsers);
        data.put("todayNewUsers", todayNewUsers);
        data.put("pendingReviews", pendingReviews);
        data.put("recheckReviews", recheckReviews);
        data.put("queueBacklog", pendingReviews + recheckReviews);
        data.put("totalArtifacts", totalArtifacts);
        data.put("onlineUsers", countDistinctRecentLoginUsers(15));
        data.put("todayContentSubmissions", countTodayContentSubmissions());
        data.put("loginTrend7d", buildLoginTrend());
        data.put("accessTrendDay", buildAccessTrend("DAY", 7));
        data.put("accessTrendWeek", buildAccessTrend("WEEK", 8));
        data.put("accessTrendMonth", buildAccessTrend("MONTH", 6));
        data.put("growthTrend", buildGrowthTrend(14));
        return ApiResponse.ok(data);
    }

    private long countDistinctRecentLoginUsers(int minutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(minutes);
        return loginLogRepository.findAll().stream()
                .filter(x -> x.getLoginTime() != null && !x.getLoginTime().isBefore(cutoff))
                .filter(x -> "SUCCESS".equalsIgnoreCase(x.getResult()))
                .map(LoginLog::getUsername)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    private long countTodayContentSubmissions() {
        LocalDate today = LocalDate.now();
        return reviewContentRepository.findAll().stream()
                .filter(x -> x.getSubmitTime() != null && x.getSubmitTime().toLocalDate().equals(today))
                .count();
    }

    private Map<String, Object> buildAccessTrend(String granularity, int periods) {
        List<ReviewContent> all = reviewContentRepository.findAll();
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
        Set<String> systems = all.stream().map(ReviewContent::getSourceSystem).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, List<Long>> series = new LinkedHashMap<>();
        for (String s : systems) {
            List<Long> vals = new ArrayList<>();
            for (int i = 0; i < periodStart.size(); i++) {
                LocalDate start = periodStart.get(i);
                LocalDate end = (i + 1 < periodStart.size()) ? periodStart.get(i + 1) : advance(start, granularity);
                long c = all.stream().filter(x -> s.equals(x.getSourceSystem()))
                        .filter(x -> x.getSubmitTime() != null)
                        .filter(x -> !x.getSubmitTime().toLocalDate().isBefore(start) && x.getSubmitTime().toLocalDate().isBefore(end))
                        .count();
                vals.add(c);
            }
            series.put(s, vals);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("labels", labels);
        out.put("series", series);
        return out;
    }

    private Map<String, Object> buildGrowthTrend(int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        List<PlatformUser> users = platformUserRepository.findAll();
        List<ReviewContent> contents = reviewContentRepository.findAll();
        List<Artifact> artifacts = artifactRepository.findAll();
        List<String> labels = new ArrayList<>();
        List<Long> userVals = new ArrayList<>();
        List<Long> contentVals = new ArrayList<>();
        List<Long> artifactVals = new ArrayList<>();
        long u = 0, c = 0, a = 0;
        for (int i = 0; i < days; i++) {
            LocalDate d = start.plusDays(i);
            labels.add(d.toString());
            u += users.stream().filter(x -> x.getCreatedAt() != null && x.getCreatedAt().toLocalDate().equals(d)).count();
            c += contents.stream().filter(x -> x.getSubmitTime() != null && x.getSubmitTime().toLocalDate().equals(d)).count();
            a += artifacts.stream().filter(x -> x.getUpdatedAt() != null && x.getUpdatedAt().toLocalDate().equals(d)).count();
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

    private List<Map<String, Object>> buildLoginTrend() {
        List<LoginLog> logs = loginLogRepository.findAll();
        Map<LocalDate, Long> counts = new HashMap<>();
        for (int i = 0; i < 7; i++) {
            counts.put(LocalDate.now().minusDays(i), 0L);
        }
        for (LoginLog log : logs) {
            if (log.getLoginTime() == null) {
                continue;
            }
            LocalDate day = log.getLoginTime().toLocalDate();
            if (counts.containsKey(day)) {
                counts.put(day, counts.get(day) + 1);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            Map<String, Object> item = new HashMap<>();
            item.put("date", d.toString());
            item.put("count", counts.getOrDefault(d, 0L));
            result.add(item);
        }
        return result;
    }
}
