package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.entity.LoginLog;
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
import java.util.*;

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
        data.put("totalArtifacts", totalArtifacts);
        data.put("loginTrend7d", buildLoginTrend());
        return ApiResponse.ok(data);
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
