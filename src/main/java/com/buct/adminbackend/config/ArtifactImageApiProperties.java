package com.buct.adminbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 爬虫组图片 API（FastAPI :8000），用于哈佛 / MFA 等本地磁盘图展示。
 * 见项目外 README：/api/img/{museum_id}/{object_id}
 */
@Data
@Component
@ConfigurationProperties(prefix = "artifact.image-api")
public class ArtifactImageApiProperties {

    private boolean enabled = true;

    /** 例如 http://47.96.152.190:8000 */
    private String baseUrl = "http://47.96.152.190:8000";

    /** 走图片 API 的馆别：2=哈佛，3=波士顿 MFA */
    private List<Integer> proxyMuseumIds = List.of(2, 3);
}
