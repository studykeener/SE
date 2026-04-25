package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.config.IntegrationProperties;
import com.buct.adminbackend.dto.ForwardRequest;
import com.buct.adminbackend.dto.IntegrationCallResult;
import com.buct.adminbackend.service.IntegrationProxyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/integrations")
@RequiredArgsConstructor
public class IntegrationController {

    private final IntegrationProperties integrationProperties;
    private final IntegrationProxyService integrationProxyService;

    @GetMapping("/endpoints")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<Map<String, Object>> endpoints() {
        Map<String, Object> data = new HashMap<>();
        data.put("description", "统一通过本后台代理调用其他子系统标准 API，不直接修改对方数据库");
        data.put("mode", integrationProperties.getMode());
        data.put("userSystemBaseUrl", integrationProperties.getUserSystemBaseUrl());
        data.put("artifactSystemBaseUrl", integrationProperties.getArtifactSystemBaseUrl());
        data.put("paths", integrationProperties.getPaths());
        data.put("templateApis", new String[]{
                "GET  /api/admin/integrations/proxy/users",
                "GET  /api/admin/integrations/proxy/artifacts",
                "POST /api/admin/integrations/proxy/review",
                "POST /api/admin/integrations/proxy/forward"
        });
        return ApiResponse.ok(data);
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> data = new HashMap<>();
        data.put("mode", integrationProperties.getMode());
        data.put("mock", integrationProxyService.isMock());
        data.put("userSystemBaseUrl", integrationProperties.getUserSystemBaseUrl());
        data.put("artifactSystemBaseUrl", integrationProperties.getArtifactSystemBaseUrl());
        data.put("paths", integrationProperties.getPaths());
        return ApiResponse.ok(data);
    }

    @GetMapping("/proxy/users")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<IntegrationCallResult> proxyUsers() {
        return ApiResponse.ok(integrationProxyService.proxyGetUsers());
    }

    @GetMapping("/proxy/artifacts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<IntegrationCallResult> proxyArtifacts() {
        return ApiResponse.ok(integrationProxyService.proxyGetArtifacts());
    }

    @PostMapping("/proxy/review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<IntegrationCallResult> proxyReview(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(integrationProxyService.proxyPostReviewResult(body));
    }

    @PostMapping("/proxy/forward")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<IntegrationCallResult> proxyForward(@Valid @RequestBody ForwardRequest request) {
        return ApiResponse.ok(integrationProxyService.forward(
                request.system(),
                request.subPath(),
                request.method() == null ? "GET" : request.method(),
                request.body()
        ));
    }
}
