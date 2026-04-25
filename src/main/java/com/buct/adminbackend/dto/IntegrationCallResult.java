package com.buct.adminbackend.dto;

/**
 * 子系统 API 代理一次调用的可展示结果（供管理端与对接联调使用）
 */
public record IntegrationCallResult(
        String mode,
        String requestMethod,
        String requestUrl,
        boolean mock,
        Object body
) {
}
