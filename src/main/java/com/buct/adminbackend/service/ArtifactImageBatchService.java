package com.buct.adminbackend.service;

import com.buct.adminbackend.config.ArtifactStorageProperties;
import com.buct.adminbackend.dto.BatchImageUploadResult;
import com.buct.adminbackend.entity.Artifact;
import com.buct.adminbackend.entity.ArtifactId;
import com.buct.adminbackend.repository.ArtifactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ArtifactImageBatchService {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final ArtifactRepository artifactRepository;
    private final ArtifactStorageProperties storageProperties;

    @Transactional
    public BatchImageUploadResult batchUpload(
            MultipartFile[] files,
            Integer defaultMuseumId,
            boolean replaceOnly,
            MultipartFile mappingCsv) throws IOException {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("请至少选择一张图片");
        }
        int museumId = defaultMuseumId == null ? 1 : defaultMuseumId;
        Map<String, String> csvMap = parseMappingCsv(mappingCsv);
        BatchImageUploadResult result = new BatchImageUploadResult();
        Path root = ensureUploadRoot();

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                result.setSkipped(result.getSkipped() + 1);
                result.addDetail("跳过空文件");
                continue;
            }
            String original = Optional.ofNullable(file.getOriginalFilename()).orElse("").trim();
            if (!StringUtils.hasText(original)) {
                result.setSkipped(result.getSkipped() + 1);
                result.addDetail("跳过无文件名条目");
                continue;
            }
            String ext = extension(original);
            if (!ALLOWED_EXT.contains(ext)) {
                result.setSkipped(result.getSkipped() + 1);
                result.addDetail("跳过不支持的格式: " + original);
                continue;
            }

            MatchKey key = resolveKey(original, csvMap, museumId);
            if (key == null) {
                result.setSkipped(result.getSkipped() + 1);
                result.addDetail("无法匹配文物: " + original);
                continue;
            }

            Optional<Artifact> optional = artifactRepository.findById(new ArtifactId(key.museumId(), key.objectId()));
            if (optional.isEmpty()) {
                if (replaceOnly) {
                    result.setSkipped(result.getSkipped() + 1);
                    result.addDetail("未找到文物，已跳过: " + key.museumId() + "/" + key.objectId());
                    continue;
                }
                result.setSkipped(result.getSkipped() + 1);
                result.addDetail("文物不存在（仅替换模式可跳过）: " + key.museumId() + "/" + key.objectId());
                continue;
            }

            Artifact artifact = optional.get();
            boolean hadImage = StringUtils.hasText(artifact.getImageUrl());
            Path targetDir = root.resolve(String.valueOf(key.museumId()));
            Files.createDirectories(targetDir);
            String storedName = key.objectId().replaceAll("[^a-zA-Z0-9._-]", "_") + "." + ext;
            Path target = targetDir.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String publicUrl = "/uploads/" + key.museumId() + "/" + storedName;
            artifact.setImageUrl(publicUrl);
            artifact.setImagePath(publicUrl);
            if (!StringUtils.hasText(artifact.getDetailUrl()) || artifact.getDetailUrl().startsWith("/uploads/")) {
                artifact.setDetailUrl(publicUrl);
            }
            artifact.setImageCount((short) 1);
            artifactRepository.save(artifact);

            if (hadImage) {
                result.setReplaced(result.getReplaced() + 1);
                result.addDetail("已替换: " + artifact.getTitle() + " ← " + original);
            } else {
                result.setUploaded(result.getUploaded() + 1);
                result.addDetail("已上传: " + artifact.getTitle() + " ← " + original);
            }
        }
        return result;
    }

    private Map<String, String> parseMappingCsv(MultipartFile mappingCsv) throws IOException {
        Map<String, String> map = new HashMap<>();
        if (mappingCsv == null || mappingCsv.isEmpty()) {
            return map;
        }
        String content = new String(mappingCsv.getBytes(), StandardCharsets.UTF_8);
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        String[] lines = content.split("\\r?\\n");
        int start = lines.length > 0 && lines[0].toLowerCase().contains("filename") ? 1 : 0;
        for (int i = start; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(",", -1);
            if (parts.length >= 3) {
                String museum = parts[0].trim();
                String objectId = parts[1].trim();
                String filename = parts[2].trim();
                map.put(filename.toLowerCase(), museum + ":" + objectId);
            } else if (parts.length == 2) {
                map.put(parts[1].trim().toLowerCase(), parts[0].trim());
            }
        }
        return map;
    }

    private MatchKey resolveKey(String original, Map<String, String> csvMap, int defaultMuseumId) {
        String lower = original.toLowerCase();
        if (csvMap.containsKey(lower)) {
            return parseMapValue(csvMap.get(lower), defaultMuseumId);
        }
        String base = baseName(original);
        if (csvMap.containsKey(base.toLowerCase())) {
            return parseMapValue(csvMap.get(base.toLowerCase()), defaultMuseumId);
        }
        if (base.startsWith("entity:artifact:")) {
            String[] segs = base.split(":");
            if (segs.length >= 4) {
                try {
                    return new MatchKey(Integer.parseInt(segs[2]), segs[3]);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return new MatchKey(defaultMuseumId, base);
    }

    private MatchKey parseMapValue(String value, int defaultMuseumId) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.contains(":")) {
            String[] parts = value.split(":", 2);
            if (parts.length == 2 && parts[0].matches("\\d+")) {
                return new MatchKey(Integer.parseInt(parts[0]), parts[1]);
            }
            if (value.startsWith("entity:artifact:")) {
                String[] segs = value.split(":");
                if (segs.length >= 4) {
                    return new MatchKey(Integer.parseInt(segs[2]), segs[3]);
                }
            }
        }
        return new MatchKey(defaultMuseumId, value.trim());
    }

    private Path ensureUploadRoot() throws IOException {
        Path root = Paths.get(storageProperties.getUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String baseName(String filename) {
        String name = filename;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private record MatchKey(int museumId, String objectId) {
    }
}
