package com.buct.adminbackend.service;

import com.buct.adminbackend.config.IntegrationProperties;
import com.buct.adminbackend.dto.IntegrationCallResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IntegrationProxyService {

    private final IntegrationProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public boolean isMock() {
        return "mock".equalsIgnoreCase(properties.getMode());
    }

    public IntegrationCallResult proxyGetUsers() {
        String method = "GET";
        if (isMock()) {
            Object body = mockUsersPayload();
            return new IntegrationCallResult("mock", method, "mock://user-system" + properties.getPaths().getUsers(), true, body);
        }
        String url = joinBaseAndPath(properties.getUserSystemBaseUrl(), properties.getPaths().getUsers());
        Object body = doGetJson(url);
        return new IntegrationCallResult("real", method, url, false, body);
    }

    public IntegrationCallResult proxyGetArtifacts() {
        String method = "GET";
        if (isMock()) {
            Object body = mockArtifactsPayload();
            return new IntegrationCallResult("mock", method, "mock://artifact-system" + properties.getPaths().getArtifacts(), true, body);
        }
        String url = joinBaseAndPath(properties.getArtifactSystemBaseUrl(), properties.getPaths().getArtifacts());
        Object body = doGetJson(url);
        return new IntegrationCallResult("real", method, url, false, body);
    }

    public IntegrationCallResult proxyPostReviewResult(Map<String, Object> requestBody) {
        String method = "POST";
        if (isMock()) {
            Map<String, Object> body = Map.of(
                    "acknowledged", true,
                    "originalRequest", requestBody,
                    "message", "Mock：未调用外部系统，仅作联调模板"
            );
            return new IntegrationCallResult("mock", method, "mock://user-system" + properties.getPaths().getReviewCallback(), true, body);
        }
        String url = joinBaseAndPath(properties.getUserSystemBaseUrl(), properties.getPaths().getReviewCallback());
        String raw = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);
        Object body = parseJsonOrRaw(raw);
        return new IntegrationCallResult("real", method, url, false, body);
    }

    public IntegrationCallResult forward(String system, String subPath, String method, Object body) {
        String m = (method == null || method.isBlank()) ? "GET" : method.toUpperCase();
        if (isMock()) {
            return new IntegrationCallResult("mock", m, "mock://" + system + subPath, true,
                    Map.of("message", "Mock 通用转发", "system", system, "subPath", subPath, "input", body));
        }
        String base = "user".equalsIgnoreCase(system)
                ? properties.getUserSystemBaseUrl()
                : properties.getArtifactSystemBaseUrl();
        if (!"user".equalsIgnoreCase(system) && !"artifact".equalsIgnoreCase(system)) {
            throw new IllegalArgumentException("system 只支持 user 或 artifact");
        }
        if (subPath == null || !subPath.startsWith("/")) {
            throw new IllegalArgumentException("subPath 需以 / 开头");
        }
        String url = joinBaseAndPath(base, subPath);
        if ("GET".equals(m)) {
            Object res = doGetJson(url);
            return new IntegrationCallResult("real", m, url, false, res);
        }
        if ("POST".equals(m)) {
            Map<String, Object> mapBody;
            if (body == null) {
                mapBody = Map.of();
            } else if (body instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) body;
                mapBody = cast;
            } else {
                mapBody = objectMapper.convertValue(body, new TypeReference<>() { });
            }
            String raw = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapBody)
                    .retrieve()
                    .body(String.class);
            return new IntegrationCallResult("real", m, url, false, parseJsonOrRaw(raw));
        }
        throw new IllegalArgumentException("仅支持 GET / POST 转发");
    }

    private Object doGetJson(String url) {
        try {
            String raw = restClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            return parseJsonOrRaw(raw);
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("网络不可达: " + url + " -> " + e.getMessage());
        } catch (RestClientException e) {
            throw new IllegalStateException("子系统返回错误: " + url + " -> " + e.getMessage());
        }
    }

    private Object parseJsonOrRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, Object.class);
        } catch (Exception e) {
            return raw;
        }
    }

    private static String joinBaseAndPath(String base, String path) {
        if (path == null || path.isEmpty()) {
            return base;
        }
        if (path.startsWith("/")) {
            if (base.endsWith("/")) {
                return base.substring(0, base.length() - 1) + path;
            }
            return base + path;
        }
        if (base.endsWith("/")) {
            return base + path;
        }
        return base + "/" + path;
    }

    private List<Map<String, Object>> mockUsersPayload() {
        return List.of(
                Map.of("id", 1, "username", "demo1", "source", "WEB"),
                Map.of("id", 2, "username", "demo2", "source", "APP")
        );
    }

    private List<Map<String, Object>> mockArtifactsPayload() {
        return List.of(
                Map.of("objectId", "ext-1", "title", "样例瓷瓶", "museum", "Mock Museum", "kgStatus", "SYNCED")
        );
    }
}
