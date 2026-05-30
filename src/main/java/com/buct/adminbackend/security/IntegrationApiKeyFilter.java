package com.buct.adminbackend.security;

import com.buct.adminbackend.config.IntegrationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class IntegrationApiKeyFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-Integration-Api-Key";

    private final IntegrationProperties integrationProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/integration/")) {
            filterChain.doFilter(request, response);
            return;
        }
        if ("/api/integration/health".equals(uri)) {
            filterChain.doFilter(request, response);
            return;
        }
        String provided = request.getHeader(API_KEY_HEADER);
        String expected = integrationProperties.getInboundApiKey();
        if (!StringUtils.hasText(expected) || !expected.equals(provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "success", false,
                    "message", "集成 API 密钥无效或缺失，请在请求头 " + API_KEY_HEADER + " 中携带正确密钥",
                    "data", null
            ));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
