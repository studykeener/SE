package com.buct.adminbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "image-moderation")
public class ImageModerationProperties {

    /**
     * mock: 本地模拟打分; real: 调用外部图片审核服务; local: 使用本地 NSFW ONNX 模型。
     */
    private String mode = "mock";

    /**
     * 图片审核服务商。当前支持 aliyun。
     */
    private String provider = "aliyun";

    /**
     * 阿里云 Green 接入点（示例: green-cip.cn-shanghai.aliyuncs.com）。
     */
    private String aliyunEndpoint = "green-cip.cn-shanghai.aliyuncs.com";

    /**
     * 阿里云 Green 图片审核 serviceCode（示例: baselineCheck_global）。
     */
    private String aliyunService = "baselineCheck_global";

    /**
     * 阿里云 AccessKey ID。
     */
    private String aliyunAccessKeyId;

    /**
     * 阿里云 AccessKey Secret。
     */
    private String aliyunAccessKeySecret;

    /**
     * 是否启用本地 NSFW ONNX 模型。
     */
    private boolean localModelEnabled = true;

    /**
     * 本地 NSFW ONNX 模型文件路径（支持文件系统绝对/相对路径，以及 classpath 资源路径）。
     */
    private String localModelPath = "models/nsfw_model.onnx";

    /**
     * 模型下载 URL。若本地模型文件不存在且此值非空，启动时自动下载。
     * 推荐模型: https://huggingface.co/onnx-community/nsfw-classifier-ONNX/resolve/main/onnx/model.onnx
     */
    private String localModelDownloadUrl;
}
