package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.ArtifactUpsertRequest;
import com.buct.adminbackend.entity.Artifact;
import com.buct.adminbackend.repository.ArtifactRepository;
import com.buct.adminbackend.service.AuditLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/artifacts")
@RequiredArgsConstructor
public class ArtifactController {

    private final ArtifactRepository artifactRepository;
    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<List<Artifact>> list() {
        return ApiResponse.ok(artifactRepository.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<Artifact> detail(@PathVariable Long id) {
        Artifact data = artifactRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("文物不存在"));
        return ApiResponse.ok(data);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<Artifact> create(@Valid @RequestBody ArtifactUpsertRequest request, Authentication auth) {
        Artifact data = new Artifact();
        apply(request, data);
        Artifact saved = artifactRepository.save(data);
        auditLogService.logDataChange(auth.getName(), "CREATE", "ARTIFACT", String.valueOf(saved.getId()), saved.getName());
        return ApiResponse.ok("创建成功", saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<Artifact> update(@PathVariable Long id,
                                        @Valid @RequestBody ArtifactUpsertRequest request,
                                        Authentication auth) {
        Artifact data = artifactRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("文物不存在"));
        apply(request, data);
        Artifact saved = artifactRepository.save(data);
        auditLogService.logDataChange(auth.getName(), "UPDATE", "ARTIFACT", String.valueOf(saved.getId()), saved.getName());
        return ApiResponse.ok("更新成功", saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        artifactRepository.deleteById(id);
        auditLogService.logDataChange(auth.getName(), "DELETE", "ARTIFACT", String.valueOf(id), "");
        return ApiResponse.ok("删除成功", null);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ResponseEntity<byte[]> exportCsv() {
        StringBuilder sb = new StringBuilder("id,name,period,type,material,sourceSystem,sourceId,kgSyncStatus\n");
        for (Artifact a : artifactRepository.findAll()) {
            sb.append(a.getId()).append(",")
                    .append(escape(a.getName())).append(",")
                    .append(escape(a.getPeriod())).append(",")
                    .append(escape(a.getType())).append(",")
                    .append(escape(a.getMaterial())).append(",")
                    .append(escape(a.getSourceSystem())).append(",")
                    .append(escape(a.getSourceId())).append(",")
                    .append(escape(a.getKgSyncStatus())).append("\n");
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=artifacts.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<Integer> importCsv(@RequestBody String csvContent, Authentication auth) {
        String[] lines = csvContent.split("\\r?\\n");
        int count = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(",", -1);
            Artifact a = new Artifact();
            a.setName(parts.length > 0 ? parts[0] : "未命名");
            a.setPeriod(parts.length > 1 ? parts[1] : null);
            a.setType(parts.length > 2 ? parts[2] : null);
            a.setMaterial(parts.length > 3 ? parts[3] : null);
            a.setSourceSystem(parts.length > 4 ? parts[4] : null);
            a.setSourceId(parts.length > 5 ? parts[5] : null);
            a.setKgSyncStatus(parts.length > 6 ? parts[6] : "PENDING");
            a.setUpdatedAt(LocalDateTime.now());
            artifactRepository.save(a);
            count++;
        }
        auditLogService.logDataChange(auth.getName(), "IMPORT", "ARTIFACT", "-", "count=" + count);
        return ApiResponse.ok("导入成功", count);
    }

    private void apply(ArtifactUpsertRequest req, Artifact data) {
        data.setName(req.name());
        data.setPeriod(req.period());
        data.setType(req.type());
        data.setMaterial(req.material());
        data.setDescription(req.description());
        data.setImageUrl(req.imageUrl());
        data.setSourceSystem(req.sourceSystem());
        data.setSourceId(req.sourceId());
        data.setKgSyncStatus(req.kgSyncStatus() == null || req.kgSyncStatus().isBlank() ? "PENDING" : req.kgSyncStatus());
        data.setUpdatedAt(LocalDateTime.now());
    }

    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace(",", " ");
    }
}
