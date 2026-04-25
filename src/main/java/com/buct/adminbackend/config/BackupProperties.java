package com.buct.adminbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "backup")
public class BackupProperties {
    /** 备份文件目录（相对路径按项目工作目录解析） */
    private String directory = "backups";
    /** 32 字节 base64 key（AES-256） */
    private String aesKeyBase64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
}

