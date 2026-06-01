package com.buct.adminbackend.service;

import com.buct.adminbackend.entity.Artifact;
import com.buct.adminbackend.repository.ArtifactRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArtifactImportService {

    private final ArtifactRepository artifactRepository;
    private final ObjectMapper objectMapper;

    public int importFromCsvString(String csvContent) {
        if (csvContent.startsWith("\uFEFF")) {
            csvContent = csvContent.substring(1);
        }
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
            String[] parts = parseCsvLine(line);
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

    public int importFromJsonFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的 JSON 文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!name.toLowerCase().endsWith(".json")) {
            throw new IllegalArgumentException("只支持 .json 文件");
        }
        List<Artifact> items = objectMapper.readValue(file.getBytes(), new TypeReference<>() {
        });
        if (items == null || items.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Artifact item : items) {
            if (item == null || !StringUtils.hasText(item.getTitle())) {
                continue;
            }
            if (item.getMuseumId() == null) {
                item.setMuseumId(1);
            }
            if (!StringUtils.hasText(item.getObjectId())) {
                item.setObjectId("IMPORT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            }
            fillRequiredDefaults(item);
            artifactRepository.save(item);
            count++;
        }
        return count;
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
            a.setDescription(value(parts, 7, a.getTitle()));
            a.setImageUrl(value(parts, 8, ""));
            a.setDetailUrl(value(parts, 9, a.getImageUrl()));
        } else if (isSampleArtifactsRow(parts)) {
            a.setTitle(value(parts, 1, "未命名"));
            a.setPeriod(value(parts, 2, "未知"));
            a.setType(value(parts, 3, "未知"));
            a.setMaterial(nullIfBlank(value(parts, 4, null)));
            a.setMuseumId(resolveMuseumFromSource(value(parts, 5, "museum")));
            a.setObjectId(resolveObjectId(value(parts, 6, null), a.getTitle()));
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
        String a = unquote(parts[0].trim());
        return "name".equalsIgnoreCase(a)
                || "artifactid".equalsIgnoreCase(a)
                || ("id".equalsIgnoreCase(a) && parts.length > 1 && "name".equalsIgnoreCase(unquote(parts[1].trim())));
    }

    private static boolean isNewExportFormatRow(String[] parts) {
        if (parts.length < 4) {
            return false;
        }
        return unquote(parts[0].trim()).startsWith("entity:artifact:");
    }

    private static boolean isLegacyExportFormatRow(String[] parts) {
        return parts.length >= 8 && parts[0].trim().matches("\\d+");
    }

    private static boolean isSampleArtifactsRow(String[] parts) {
        if (parts.length < 7) {
            return false;
        }
        String col5 = value(parts, 5, "").toLowerCase();
        return col5.equals("museum") || col5.equals("archive") || col5.equals("manual")
                || col5.equals("smithsonian") || col5.equals("harvard") || col5.equals("mfa");
    }

    private static int resolveMuseumFromSource(String sourceSystem) {
        if (!StringUtils.hasText(sourceSystem)) {
            return 1;
        }
        return switch (sourceSystem.trim().toLowerCase()) {
            case "harvard", "archive" -> 2;
            case "mfa" -> 3;
            default -> 1;
        };
    }

    private static String value(String[] parts, int index, String defaultValue) {
        if (index >= parts.length) {
            return defaultValue;
        }
        String s = unquote(parts[index].trim());
        return s.isEmpty() ? defaultValue : s;
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1).replace("\"\"", "\"");
        }
        return s;
    }

    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
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
