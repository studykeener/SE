package com.buct.adminbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "integration")
public class IntegrationProperties {

    /**
     * 队友系统调用 /api/integration/** 时在请求头 X-Integration-Api-Key 中携带此密钥
     */
    private String inboundApiKey = "dev-integration-key-change-me";
}
