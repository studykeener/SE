package com.buct.adminbackend.service;

import com.buct.adminbackend.entity.Artifact;
import com.buct.adminbackend.repository.ArtifactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArtifactImportService {

    private final ArtifactRepository artifactRepository;

    public int importFromCsvString(String csvContent) {
        String[] lines = csvContent.split("\\r?\\n");
        if (lines.length < 1) {
            return 0;
        }
        int startRow = isHeaderLine(lines[0]) ? 1 : 0;
        int count = 0;
        for (int i = startRow; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(",", -1);
            if (isHeaderDataRow(parts)) {
                continue;
            }
            Artifact a = buildArtifactFromCsv(parts);
            artifactRepository.save(a);
            count++;
        }
        return count;
    }

    public int importFromMultipartFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的 CSV 文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!name.toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("只支持 .csv 文件");
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        if (content.isBlank()) {
            throw new IllegalArgumentException("文件内容为空");
        }
        return importFromCsvString(content);
    }

    private static Artifact buildArtifactFromCsv(String[] parts) {
        Artifact a = new Artifact();
        if (isNewExportFormatRow(parts)) {
            int museumId = parseInt(parts, 1, 1);
            String objectId = value(parts, 2, "");
            if (!StringUtils.hasText(objectId)) {
                objectId = "IMPORT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            }
            a.setMuseumId(museumId);
            a.setObjectId(objectId.trim());
            a.setTitle(value(parts, 3, "未命名"));
            a.setPeriod(value(parts, 4, "未知"));
            a.setType(value(parts, 5, "未知"));
            a.setMaterial(nullIfBlank(value(parts, 6, null)));
        } else if (isLegacyExportFormatRow(parts)) {
            a.setTitle(value(parts, 1, "未命名"));
            a.setPeriod(value(parts, 2, "未知"));
            a.setType(value(parts, 3, "未知"));
            a.setMaterial(nullIfBlank(value(parts, 4, null)));
            a.setMuseumId(1);
            a.setObjectId(resolveObjectId(value(parts, 6, null), a.getTitle()));
        } else {
            a.setTitle(value(parts, 0, "未命名"));
            a.setPeriod(value(parts, 1, "未知"));
            a.setType(value(parts, 2, "未知"));
            a.setMaterial(nullIfBlank(value(parts, 3, null)));
            a.setMuseumId(1);
            a.setObjectId(resolveObjectId(value(parts, 5, null), a.getTitle()));
        }
        fillRequiredDefaults(a);
        return a;
    }

    private static void fillRequiredDefaults(Artifact a) {
        if (!StringUtils.hasText(a.getDescription())) {
            a.setDescription(a.getTitle());
        }
        if (!StringUtils.hasText(a.getMuseum())) {
            a.setMuseum(museumLabel(a.getMuseumId()));
        }
        if (!StringUtils.hasText(a.getLocation())) {
            a.setLocation(a.getMuseum());
        }
        if (!StringUtils.hasText(a.getImageUrl())) {
            a.setImageUrl("");
        }
        if (!StringUtils.hasText(a.getImagePath())) {
            a.setImagePath(a.getImageUrl());
        }
        if (!StringUtils.hasText(a.getDetailUrl())) {
            a.setDetailUrl(a.getImageUrl());
        }
        if (a.getCrawlDate() == null) {
            a.setCrawlDate(LocalDate.now());
        }
        a.setArtifactId("entity:artifact:" + a.getMuseumId() + ":" + a.getObjectId());
    }

    private static boolean isHeaderLine(String firstLine) {
        String s = firstLine.toLowerCase();
        return s.contains("name") && (s.contains("period") || s.contains("objectid"));
    }

    private static boolean isHeaderDataRow(String[] parts) {
        if (parts.length == 0) {
            return false;
        }
        String a = parts[0].trim();
        return "name".equalsIgnoreCase(a)
                || "artifactid".equalsIgnoreCase(a)
                || ("id".equalsIgnoreCase(a) && parts.length > 1 && "name".equalsIgnoreCase(parts[1].trim()));
    }

    private static boolean isNewExportFormatRow(String[] parts) {
        return parts.length >= 4 && parts[0].trim().startsWith("entity:artifact:");
    }

    private static boolean isLegacyExportFormatRow(String[] parts) {
        return parts.length >= 8 && parts[0].trim().matches("\\d+");
    }

    private static String value(String[] parts, int index, String defaultValue) {
        if (index >= parts.length) {
            return defaultValue;
        }
        String s = parts[index].trim();
        return s.isEmpty() ? defaultValue : s;
    }

    private static int parseInt(String[] parts, int index, int defaultValue) {
        if (index >= parts.length) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String nullIfBlank(String s) {
        if (s == null) {
            return null;
        }
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static String resolveObjectId(String sourceId, String name) {
        if (StringUtils.hasText(sourceId)) {
            return sourceId.trim();
        }
        return "IMPORT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String museumLabel(Integer museumId) {
        if (museumId == null) {
            return "Smithsonian";
        }
        return switch (museumId) {
            case 2 -> "Harvard";
            case 3 -> "MFA";
            default -> "Smithsonian";
        };
    }
}
