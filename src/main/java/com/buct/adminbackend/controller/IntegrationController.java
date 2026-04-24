package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/integrations")
@RequiredArgsConstructor
public class IntegrationController {

    @Value("${integration.user-system-base-url:http://localhost:9001}")
    private String userSystemBaseUrl;

    @Value("${integration.artifact-system-base-url:http://localhost:9002}")
    private String artifactSystemBaseUrl;

    @GetMapping("/endpoints")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<Map<String, Object>> endpoints() {
        Map<String, Object> data = new HashMap<>();
        data.put("description", "统一通过 API 对接其他子系统，不直接改对方数据库");
        data.put("userSystemBaseUrl", userSystemBaseUrl);
        data.put("artifactSystemBaseUrl", artifactSystemBaseUrl);
        data.put("standardApis", new String[]{
                "GET /api/external/users",
                "GET /api/external/artifacts",
                "POST /api/external/content/review-result"
        });
        return ApiResponse.ok(data);
    }
}
