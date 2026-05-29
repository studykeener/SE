package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.ArtifactUpsertRequest;
import com.buct.adminbackend.entity.Artifact;
import com.buct.adminbackend.repository.ArtifactRepository;
import com.buct.adminbackend.service.ArtifactImportService;
import com.buct.adminbackend.service.AuditLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.buct.adminbackend.security.PermissionCodes;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/artifacts")
@RequiredArgsConstructor
public class ArtifactController {

    private final ArtifactRepository artifactRepository;
    private final AuditLogService auditLogService;
    private final ArtifactImportService artifactImportService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_VIEW + "')")
    public ApiResponse<List<Artifact>> list() {
        return ApiResponse.ok(artifactRepository.findAll());
    }

    @GetMapping("/{artifactId}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_VIEW + "')")
    public ApiResponse<Artifact> detail(@PathVariable String artifactId) {
        Artifact data = findByArtifactId(artifactId);
        return ApiResponse.ok(data);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_EDIT + "')")
    public ApiResponse<Artifact> create(@Valid @RequestBody ArtifactUpsertRequest request, Authentication auth) {
        Artifact data = new Artifact();
        apply(request, data, true);
        Artifact saved = artifactRepository.save(data);
        auditLogService.logDataChange(auth.getName(), "CREATE", "ARTIFACT", saved.getArtifactId(), saved.getName());
        return ApiResponse.ok("创建成功", saved);
    }

    @PutMapping("/{artifactId}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_EDIT + "')")
    public ApiResponse<Artifact> update(@PathVariable String artifactId,
                                        @Valid @RequestBody ArtifactUpsertRequest request,
                                        Authentication auth) {
        Artifact data = findByArtifactId(artifactId);
        apply(request, data, false);
        Artifact saved = artifactRepository.save(data);
        auditLogService.logDataChange(auth.getName(), "UPDATE", "ARTIFACT", saved.getArtifactId(), saved.getName());
        return ApiResponse.ok("更新成功", saved);
    }

    @DeleteMapping("/{artifactId}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_DELETE + "')")
    public ApiResponse<Void> delete(@PathVariable String artifactId, Authentication auth) {
        Artifact data = findByArtifactId(artifactId);
        artifactRepository.delete(data);
        auditLogService.logDataChange(auth.getName(), "DELETE", "ARTIFACT", artifactId, "");
        return ApiResponse.ok("删除成功", null);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_IMPORT_EXPORT + "')")
    public ResponseEntity<byte[]> exportCsv() {
        StringBuilder sb = new StringBuilder("artifactId,museumId,objectId,name,period,type,material,sourceSystem,kgSyncStatus\n");
        for (Artifact a : artifactRepository.findAll()) {
            sb.append(escape(a.getArtifactId())).append(",")
                    .append(a.getMuseumId() == null ? "" : a.getMuseumId()).append(",")
                    .append(escape(a.getObjectId())).append(",")
                    .append(escape(a.getName())).append(",")
                    .append(escape(a.getPeriod())).append(",")
                    .append(escape(a.getType())).append(",")
                    .append(escape(a.getMaterial())).append(",")
                    .append(escape(a.getSourceSystem())).append(",")
                    .append(escape(a.getKgSyncStatus())).append("\n");
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=artifacts.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_IMPORT_EXPORT + "')")
    public ApiResponse<Integer> importCsvFile(@RequestParam("file") MultipartFile file, Authentication auth) throws IOException {
        int count = artifactImportService.importFromMultipartFile(file);
        auditLogService.logDataChange(auth.getName(), "IMPORT", "ARTIFACT", "-", "count=" + count);
        return ApiResponse.ok("导入成功", count);
    }

    private Artifact findByArtifactId(String artifactId) {
        String decoded = URLDecoder.decode(artifactId, StandardCharsets.UTF_8);
        return artifactRepository.findByArtifactId(decoded)
                .orElseThrow(() -> new IllegalArgumentException("文物不存在"));
    }

    private void apply(ArtifactUpsertRequest req, Artifact data, boolean creating) {
        int museumId = req.museumId() == null ? resolveMuseumId(req.sourceSystem()) : req.museumId();
        String objectId = StringUtils.hasText(req.objectId())
                ? req.objectId().trim()
                : (StringUtils.hasText(req.sourceId()) ? req.sourceId().trim()
                : (StringUtils.hasText(data.getObjectId()) ? data.getObjectId()
                : "MANUAL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)));

        if (creating || data.getMuseumId() == null || data.getObjectId() == null) {
            data.setMuseumId(museumId);
            data.setObjectId(objectId);
        }

        String title = StringUtils.hasText(req.name()) ? req.name().trim() : "未命名";
        data.setTitle(title);
        data.setPeriod(StringUtils.hasText(req.period()) ? req.period().trim() : "未知");
        data.setType(StringUtils.hasText(req.type()) ? req.type().trim() : "未知");
        data.setMaterial(req.material());
        data.setDescription(StringUtils.hasText(req.description()) ? req.description() : title);
        data.setMuseum(StringUtils.hasText(req.museum()) ? req.museum().trim() : museumLabel(museumId));
        data.setLocation(StringUtils.hasText(req.location()) ? req.location().trim() : museumLabel(museumId));
        String imageUrl = StringUtils.hasText(req.imageUrl()) ? req.imageUrl().trim() : "";
        data.setImageUrl(imageUrl);
        data.setImagePath(imageUrl);
        data.setDetailUrl(StringUtils.hasText(req.detailUrl()) ? req.detailUrl().trim() : imageUrl);
        data.setCrawlDate(LocalDate.now());
        data.setArtifactId("entity:artifact:" + data.getMuseumId() + ":" + data.getObjectId());
        if (creating && "SYNCED".equalsIgnoreCase(req.kgSyncStatus())) {
            data.setArtistEnrichedAt(LocalDate.now().toString());
        }
    }

    private static int resolveMuseumId(String sourceSystem) {
        if (!StringUtils.hasText(sourceSystem)) {
            return 1;
        }
        return switch (sourceSystem.trim().toLowerCase()) {
            case "harvard" -> 2;
            case "mfa" -> 3;
            default -> 1;
        };
    }

    private static String museumLabel(int museumId) {
        return switch (museumId) {
            case 1 -> "Smithsonian";
            case 2 -> "Harvard";
            case 3 -> "MFA";
            default -> "Museum " + museumId;
        };
    }

    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace(",", " ");
    }
}
