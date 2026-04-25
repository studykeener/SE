package com.buct.adminbackend.service;

import com.buct.adminbackend.entity.Artifact;
import com.buct.adminbackend.repository.ArtifactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ArtifactImportService {

    private final ArtifactRepository artifactRepository;

    public int importFromCsvString(String csvContent) {
        String[] lines = csvContent.split("\\r?\\n");
        if (lines.length < 1) {
            return 0;
        }
        int startRow;
        if (isHeaderLine(lines[0])) {
            startRow = 1;
        } else {
            startRow = 0;
        }
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
            Artifact a = new Artifact();
            if (isExportFormatRow(parts)) {
                a.setName(parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : "未命名");
                a.setPeriod(parts.length > 2 ? nullIfBlank(parts[2]) : null);
                a.setType(parts.length > 3 ? nullIfBlank(parts[3]) : null);
                a.setMaterial(parts.length > 4 ? nullIfBlank(parts[4]) : null);
                a.setSourceSystem(parts.length > 5 ? nullIfBlank(parts[5]) : null);
                a.setSourceId(parts.length > 6 ? nullIfBlank(parts[6]) : null);
                a.setKgSyncStatus(parts.length > 7 && !parts[7].isBlank() ? parts[7].trim() : "PENDING");
            } else {
                a.setName(parts.length > 0 && !parts[0].isBlank() ? parts[0].trim() : "未命名");
                a.setPeriod(parts.length > 1 ? nullIfBlank(parts[1]) : null);
                a.setType(parts.length > 2 ? nullIfBlank(parts[2]) : null);
                a.setMaterial(parts.length > 3 ? nullIfBlank(parts[3]) : null);
                a.setSourceSystem(parts.length > 4 ? nullIfBlank(parts[4]) : null);
                a.setSourceId(parts.length > 5 ? nullIfBlank(parts[5]) : null);
                a.setKgSyncStatus(parts.length > 6 && !parts[6].isBlank() ? parts[6].trim() : "PENDING");
            }
            a.setUpdatedAt(LocalDateTime.now());
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

    private static boolean isHeaderLine(String firstLine) {
        String s = firstLine.toLowerCase();
        return s.contains("name") && s.contains("period");
    }

    private static boolean isHeaderDataRow(String[] parts) {
        if (parts.length == 0) {
            return false;
        }
        String a = parts[0].trim();
        if ("name".equalsIgnoreCase(a)) {
            return true;
        }
        if ("id".equalsIgnoreCase(a) && parts.length > 1 && "name".equalsIgnoreCase(parts[1].trim())) {
            return true;
        }
        return false;
    }

    /** 与导出列一致: id,name,period,type,material,sourceSystem,sourceId,kgSyncStatus */
    private static boolean isExportFormatRow(String[] parts) {
        return parts.length >= 8 && parts[0].trim().matches("\\d+");
    }

    private static String nullIfBlank(String s) {
        s = s.trim();
        return s.isEmpty() ? null : s;
    }
}
