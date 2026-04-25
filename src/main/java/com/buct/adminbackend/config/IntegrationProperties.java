package com.buct.adminbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "integration")
public class IntegrationProperties {

    /**
     * mock: 返回本地模拟数据，不请求外网；real: 向 user-system / artifact-system 发起 HTTP 调用
     */
    private String mode = "mock";

    private String userSystemBaseUrl = "http://localhost:9001";
    private String artifactSystemBaseUrl = "http://localhost:9002";
    private String kgSystemBaseUrl = "http://localhost:9003";

    private Paths paths = new Paths();

    @Data
    public static class Paths {
        private String users = "/api/v1/users";
        private String artifacts = "/api/v1/artifacts";
        private String reviewCallback = "/api/v1/content/review-result";
        private String kgEntities = "/api/v1/kg/entities";
        private String kgRelations = "/api/v1/kg/relations";
        private String kgTriples = "/api/v1/kg/triples";
        private String kgSyncJobs = "/api/v1/kg/sync/jobs";
    }
}
