package com.buct.adminbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "artifact.storage")
public class ArtifactStorageProperties {

    /** 文物图片本地存储目录（相对项目根或绝对路径） */
    private String uploadDir = "uploads/artifacts";
}
