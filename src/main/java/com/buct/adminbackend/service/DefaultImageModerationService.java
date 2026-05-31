package com.buct.adminbackend.service;

import com.buct.adminbackend.config.ImageModerationProperties;
import com.aliyun.green20220302.Client;
import com.aliyun.green20220302.models.ImageModerationRequest;
import com.aliyun.green20220302.models.ImageModerationResponse;
import com.aliyun.green20220302.models.ImageModerationResponseBody;
import com.aliyun.green20220302.models.ImageModerationResponseBody.ImageModerationResponseBodyDataResult;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 图片审核服务默认实现（三模式三级降级）。
 * <p>
 * 根据配置选择审核模式：
 * - real：调用阿里云 Green 图片审核 API，失败时自动降级为 local
 * - local：使用本地 NSFW ONNX 模型推理，模型未加载时降级为 mock
 * - mock（默认）：基于关键词进行模拟打分，无需外部依赖
 * <p>
 * 降级链路： real → local → mock，保证任何情况下都能返回风险分。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultImageModerationService implements ImageModerationService {

    private final ImageModerationProperties properties;
    private final ObjectMapper objectMapper;
    private final LocalNsfwModelService localNsfwModelService;

    /**
     * 图片审核主入口，返回 0~100 的风险分。
     * 根据配置的 mode 分派到不同实现，失败时自动降级。
     */
    @Override
    public int scoreImage(String contentUrl, String contentText) {
        if (!StringUtils.hasText(contentUrl) && !StringUtils.hasText(contentText)) {
            return 0;
        }
        String mode = properties.getMode() == null ? "mock" : properties.getMode().toLowerCase();
        switch (mode) {
            case "real":
                try {
                    return scoreByAliyun(contentUrl);
                } catch (Exception ex) {
                    log.warn("阿里云图片审核调用失败，尝试降级为本地模型: {}", ex.getMessage());
                    return scoreByLocalModel(contentUrl, contentText);
                }
            case "local":
                return scoreByLocalModel(contentUrl, contentText);
            default:
                return scoreByMock(contentUrl, contentText);
        }
    }

    /**
     * 本地 NSFW 模型审核。
     * 下载图片 → 预处理(224x224) → ONNX推理 → NSFW概率*100 = 风险分。
     * 模型未加载或推理失败时降级为 mock 打分。
     */
    private int scoreByLocalModel(String contentUrl, String contentText) {
        if (!localNsfwModelService.isModelLoaded()) {
            log.warn("本地 NSFW 模型未加载，降级为本地模拟打分");
            return scoreByMock(contentUrl, contentText);
        }
        if (!StringUtils.hasText(contentUrl)) {
            return 0;
        }
        try {
            float nsfwProb = localNsfwModelService.predict(contentUrl);
            int riskScore = Math.round(nsfwProb * 100);
            log.info("本地 NSFW 模型审核: url={}, nsfwProb={}, riskScore={}", contentUrl, nsfwProb, riskScore);
            return normalize(riskScore);
        } catch (Exception ex) {
            log.warn("本地 NSFW 模型审核失败，降级为本地模拟打分: {}", ex.getMessage());
            return scoreByMock(contentUrl, contentText);
        }
    }

    /**
     * 阿里云 Green 图片审核。
     * 需要配置 AccessKey，未配置时抛异常触发降级。
     */
    private int scoreByAliyun(String contentUrl) {
        if (!"aliyun".equalsIgnoreCase(properties.getProvider())) {
            throw new IllegalStateException("当前仅支持 image-moderation.provider=aliyun");
        }
        if (!StringUtils.hasText(contentUrl)) {
            return 0;
        }
        if (!StringUtils.hasText(properties.getAliyunAccessKeyId())
                || !StringUtils.hasText(properties.getAliyunAccessKeySecret())) {
            log.warn("阿里云 AccessKey 未配置 (aliyun-access-key-id / aliyun-access-key-secret)，"
                    + "请设置环境变量 ALIBABA_CLOUD_ACCESS_KEY_ID 和 ALIBABA_CLOUD_ACCESS_KEY_SECRET，"
                    + "或在 application.yml 中直接配置");
            throw new IllegalStateException("阿里云 AccessKey 未配置");
        }
        try {
            Client client = createAliyunClient();
            RuntimeOptions runtime = new RuntimeOptions();
            Map<String, String> serviceParameters = new HashMap<>();
            serviceParameters.put("imageUrl", contentUrl);
            serviceParameters.put("dataId", UUID.randomUUID().toString());

            ImageModerationRequest request = new ImageModerationRequest();
            request.setService(properties.getAliyunService());
            request.setServiceParameters(objectMapper.writeValueAsString(serviceParameters));

            log.info("调用阿里云图片审核: url={}, service={}", contentUrl, properties.getAliyunService());
            ImageModerationResponse response = client.imageModerationWithOptions(request, runtime);
            if (response == null || response.getBody() == null) {
                log.error("阿里云图片审核返回空响应");
                throw new IllegalStateException("阿里云图片审核返回空响应");
            }
            int score = parseAliyunRiskScore(response.getBody());
            log.info("阿里云图片审核完成: url={}, riskScore={}", contentUrl, score);
            return score;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("调用阿里云图片审核失败: {}", ex.getMessage(), ex);
            throw new IllegalStateException("调用阿里云图片审核失败: " + ex.getMessage(), ex);
        }
    }

    private Client createAliyunClient() throws Exception {
        Config config = new Config();
        config.setAccessKeyId(properties.getAliyunAccessKeyId());
        config.setAccessKeySecret(properties.getAliyunAccessKeySecret());
        config.setEndpoint(properties.getAliyunEndpoint());
        return new Client(config);
    }

    private int parseAliyunRiskScore(ImageModerationResponseBody body) {
        if (body.getCode() == null || body.getCode() != 200 || body.getData() == null) {
            throw new IllegalStateException("阿里云图片审核返回异常，code=" + body.getCode() + ", msg=" + body.getMsg());
        }
        List<ImageModerationResponseBodyDataResult> results = body.getData().getResult();
        if (results == null || results.isEmpty()) {
            return 0;
        }
        int maxRisk = 0;
        for (ImageModerationResponseBodyDataResult result : results) {
            int confidenceScore = toScore(result.getConfidence());
            String label = result.getLabel() == null ? "" : result.getLabel().toLowerCase();
            int mapped = mapLabelToRisk(label, confidenceScore);
            maxRisk = Math.max(maxRisk, mapped);
        }
        return normalize(maxRisk);
    }

    private static int mapLabelToRisk(String label, int confidenceScore) {
        if (label.contains("normal")) {
            return Math.min(20, confidenceScore / 2);
        }
        if (label.contains("porn") || label.contains("terror") || label.contains("violence")) {
            return Math.max(70, confidenceScore);
        }
        if (label.contains("ad") || label.contains("qrcode") || label.contains("logo")) {
            return Math.max(40, confidenceScore);
        }
        return confidenceScore;
    }

    private static int toScore(Number confidence) {
        if (confidence == null) {
            return 0;
        }
        double raw = confidence.doubleValue();
        if (raw <= 1.0) {
            raw = raw * 100.0;
        }
        return normalize((int) Math.round(raw));
    }

    /**
     * Mock 模拟打分（基于关键词）。
     * 高危关键词(porn/terror/涉黄等) → 85分
     * 中危关键词(knife/weapon/擦边等) → 45分
     * 无命中 → 10分（安全基线）
     */
    private int scoreByMock(String contentUrl, String contentText) {
        String text = ((contentText == null ? "" : contentText) + " " + (contentUrl == null ? "" : contentUrl)).toLowerCase();
        Set<String> highRisk = Set.of("porn", "terror", "blood", "violence", "涉黄", "暴恐", "极端");
        Set<String> mediumRisk = Set.of("knife", "weapon", "smoke", "bloody", "擦边", "危险", "违规");
        if (highRisk.stream().anyMatch(text::contains)) {
            return 85;
        }
        if (mediumRisk.stream().anyMatch(text::contains)) {
            return 45;
        }
        return 10;
    }

    private static int normalize(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
