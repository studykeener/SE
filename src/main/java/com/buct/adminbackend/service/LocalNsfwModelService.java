package com.buct.adminbackend.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import com.buct.adminbackend.config.ImageModerationProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.FloatBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 本地 NSFW ONNX 模型推理服务。
 * <p>
 * 使用 ONNX Runtime 加载预训练的 NSFW 图像分类模型，
 * 在本地 CPU 上进行推理，无需连接外部云服务。
 * <p>
 * 自动从 ONNX 模型元数据读取输入形状，支持任意尺寸（224x224, 384x384 等）。
 */
@Slf4j
@Component
public class LocalNsfwModelService {

    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    /** 模型实际输入尺寸（height=width），从 ONNX 模型动态读取 */
    private int modelInputSize = 224;
    /** 模型实际输入形状，如 [1, 3, 224, 224] */
    private long[] modelInputShape;

    private final ImageModerationProperties properties;
    private final HttpClient httpClient;

    private OrtEnvironment env;
    private OrtSession session;
    private String inputName;
    private volatile boolean modelLoaded = false;

    public LocalNsfwModelService(ImageModerationProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @PostConstruct
    public void init() {
        if (!properties.isLocalModelEnabled()) {
            log.info("本地 NSFW 模型未启用 (image-moderation.local-model-enabled=false)");
            return;
        }
        String modelPath = properties.getLocalModelPath();
        if (!StringUtils.hasText(modelPath)) {
            log.warn("本地 NSFW 模型路径未配置 (image-moderation.local-model-path)");
            return;
        }
        loadModel(modelPath);
    }

    private void loadModel(String modelPath) {
        try {
            // 优先从文件系统加载
            File modelFile = new File(modelPath);
            if (modelFile.exists()) {
                loadSession(modelFile.getAbsolutePath(), null);
                log.info("本地 NSFW 模型加载成功(文件系统): {}, 形状: {}",
                        modelFile.getAbsolutePath(), Arrays.toString(modelInputShape));
                return;
            }

            // 尝试从 classpath 加载
            var resource = getClass().getClassLoader().getResource(modelPath);
            if (resource != null) {
                byte[] modelBytes = resource.openStream().readAllBytes();
                loadSession(null, modelBytes);
                log.info("本地 NSFW 模型加载成功(classpath): {}, 形状: {}",
                        modelPath, Arrays.toString(modelInputShape));
                return;
            }

            log.warn("本地 NSFW 模型文件不存在: {}，尝试自动下载...", modelPath);
            if (tryDownloadModel(modelPath)) {
                loadModel(modelPath);
                return;
            }
            log.warn("本地 NSFW 模型自动下载失败。请手动下载模型文件并放置到: {}，" +
                    "或将 image-moderation.mode 设为 mock", new File(modelPath).getAbsolutePath());
        } catch (Exception e) {
            log.error("本地 NSFW 模型加载失败: {}", e.getMessage(), e);
            this.modelLoaded = false;
        }
    }

    /**
     * 创建 ONNX Session 并读取模型输入形状。
     */
    private void loadSession(String filePath, byte[] modelBytes) throws OrtException {
        this.env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        if (filePath != null) {
            this.session = env.createSession(filePath, opts);
        } else {
            this.session = env.createSession(modelBytes, opts);
        }
        this.inputName = session.getInputNames().iterator().next();

        // 动态读取模型输入形状
        var nodeInfo = session.getInputInfo().get(inputName);
        long[] rawShape;
        if (nodeInfo != null && nodeInfo.getInfo() instanceof TensorInfo tensorInfo) {
            rawShape = tensorInfo.getShape();
        } else {
            rawShape = new long[]{1, 3, 224, 224};
            log.warn("无法读取模型输入形状，使用默认值: [1,3,224,224]");
        }
        // ONNX Runtime 不接受 -1，动态维度用默认值填充：NCHW = [1,3,224,224]
        this.modelInputShape = resolveDynamicShape(rawShape);
        // H 维（NCHW 下标 2）作为正方形输入尺寸
        if (modelInputShape.length >= 4) {
            this.modelInputSize = (int) modelInputShape[2];
        } else if (modelInputShape.length >= 3) {
            this.modelInputSize = (int) modelInputShape[1];
        }
        log.info("ONNX 模型输入: name={}, rawShape={}, resolvedShape={}, size={}x{}",
                inputName, Arrays.toString(rawShape), Arrays.toString(modelInputShape),
                modelInputSize, modelInputSize);

        this.modelLoaded = true;
    }

    /**
     * 将 ONNX 模型中的动态维度(-1)解析为具体数值。
     * 按 NCHW 约定填充：batch=1, channel=3, height=224, width=224。
     */
    private static long[] resolveDynamicShape(long[] rawShape) {
        long[] defaults = {1, 3, 224, 224};
        long[] resolved = rawShape.clone();
        for (int i = 0; i < resolved.length; i++) {
            if (resolved[i] <= 0) {
                resolved[i] = (i < defaults.length) ? defaults[i] : 1L;
            }
        }
        return resolved;
    }

    /**
     * 若配置了 downloadUrl，自动下载模型到指定路径。
     */
    private boolean tryDownloadModel(String modelPath) {
        String downloadUrl = properties.getLocalModelDownloadUrl();
        if (!StringUtils.hasText(downloadUrl)) {
            log.info("未配置模型下载 URL (image-moderation.local-model-download-url)，跳过自动下载");
            return false;
        }
        try {
            File targetFile = new File(modelPath);
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            log.info("正在下载 NSFW 模型: {} -> {}", downloadUrl, targetFile.getAbsolutePath());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.error("模型下载失败，HTTP 状态码: {}", response.statusCode());
                return false;
            }
            java.nio.file.Files.write(targetFile.toPath(), response.body());
            log.info("NSFW 模型下载成功，大小: {} bytes", response.body().length);
            return true;
        } catch (Exception e) {
            log.error("模型下载异常: {}", e.getMessage(), e);
            return false;
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (session != null) {
                session.close();
            }
        } catch (Exception e) {
            log.warn("关闭 ONNX Session 失败: {}", e.getMessage());
        }
    }

    public boolean isModelLoaded() {
        return modelLoaded;
    }

    /**
     * 对图片进行 NSFW 推理，返回 0.0~1.0 的 NSFW 概率。
     *
     * @param imageUrl 图片 URL（支持 http/https 和本地文件路径）
     * @return NSFW 概率 (0.0 = 安全, 1.0 = 高风险)
     */
    public float predict(String imageUrl) {
        if (!modelLoaded) {
            throw new IllegalStateException("本地 NSFW 模型未加载");
        }
        try {
            BufferedImage image = loadImage(imageUrl);
            if (image == null) {
                throw new IllegalStateException("无法加载图片: " + imageUrl);
            }
            float[] inputData = preprocessImage(image);
            return runInference(inputData);
        } catch (Exception e) {
            throw new IllegalStateException("本地图片审核失败: " + e.getMessage(), e);
        }
    }

    /**
     * 加载图片：支持 HTTP/HTTPS URL 和本地文件路径。
     */
    private BufferedImage loadImage(String imageUrl) throws IOException {
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return downloadImage(imageUrl);
        }
        File file = new File(imageUrl);
        if (file.exists()) {
            return ImageIO.read(file);
        }
        throw new IOException("图片文件不存在: " + imageUrl);
    }

    /**
     * 通过 HTTP 下载图片。
     */
    private BufferedImage downloadImage(String imageUrl) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP 请求失败，状态码: " + response.statusCode());
            }
            return ImageIO.read(new ByteArrayInputStream(response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("图片下载被中断", e);
        }
    }

    /**
     * 图片预处理：缩放到模型输入尺寸，转为 NCHW 格式并做 ImageNet 归一化。
     */
    private float[] preprocessImage(BufferedImage image) {
        int size = modelInputSize;
        BufferedImage resized = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, size, size, null);
        g.dispose();

        int pixelCount = size * size;
        float[] tensor = new float[3 * pixelCount];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = resized.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int gv = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                tensor[0 * pixelCount + y * size + x] = (r / 255.0f - MEAN[0]) / STD[0];
                tensor[1 * pixelCount + y * size + x] = (gv / 255.0f - MEAN[1]) / STD[1];
                tensor[2 * pixelCount + y * size + x] = (b / 255.0f - MEAN[2]) / STD[2];
            }
        }
        return tensor;
    }

    /**
     * 执行 ONNX 推理，返回 NSFW 概率。自动使用模型实际输入形状。
     */
    private float runInference(float[] inputData) throws OrtException {
        FloatBuffer buffer = FloatBuffer.wrap(inputData);
        try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, buffer, modelInputShape)) {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put(inputName, inputTensor);
            try (OrtSession.Result results = session.run(inputs)) {
                OnnxTensor outputTensor = (OnnxTensor) results.get(0);
                long[] outputShape = outputTensor.getInfo().getShape();
                int outputSize = 1;
                for (int i = 1; i < outputShape.length; i++) {
                    if (outputShape[i] > 0) {
                        outputSize *= (int) outputShape[i];
                    }
                }
                float[] output = new float[outputSize];
                outputTensor.getFloatBuffer().get(output);

                // 兼容多种输出格式:
                // 1. 单值输出 [1]: 可能是 logit，需要 sigmoid
                // 2. 双值输出 [2]: [SFW_prob, NSFW_prob] 取第二个
                // 3. 多值: 取最后一个
                if (output.length == 1) {
                    return sigmoid(output[0]);
                }
                if (output.length >= 2) {
                    return output[output.length - 1];
                }
                return output[0];
            }
        }
    }

    private static float sigmoid(float x) {
        return (float) (1.0 / (1.0 + Math.exp(-x)));
    }
}
