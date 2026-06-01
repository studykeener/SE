package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.ArtifactUpsertRequest;
import com.buct.adminbackend.entity.Artifact;
import com.buct.adminbackend.entity.ArtifactId;
import com.buct.adminbackend.repository.ArtifactRepository;
import com.buct.adminbackend.dto.BatchImageUploadResult;
import com.buct.adminbackend.service.ArtifactImageBatchService;
import com.buct.adminbackend.service.ArtifactImageUrlResolver;
import com.buct.adminbackend.service.ArtifactImportService;
import com.buct.adminbackend.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.buct.adminbackend.security.PermissionCodes;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/admin/artifacts")
@RequiredArgsConstructor
public class ArtifactController {

    private final ArtifactRepository artifactRepository;
    private final AuditLogService auditLogService;
    private final ArtifactImportService artifactImportService;
    private final ArtifactImageBatchService artifactImageBatchService;
    private final ArtifactImageUrlResolver artifactImageUrlResolver;
    private final ObjectMapper objectMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_VIEW + "')")
    public ApiResponse<List<Artifact>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String type) {
        Specification<Artifact> spec = buildFilterSpec(keyword, sourceSystem, period, type);
        Sort sort = Sort.by(Sort.Direction.ASC, "title");
        List<Artifact> data = spec == null
                ? artifactRepository.findAll(sort)
                : artifactRepository.findAll(spec, sort);
        data.forEach(artifactImageUrlResolver::enrichDisplayImageUrl);
        return ApiResponse.ok(data);
    }

    @GetMapping("/{artifactId}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_VIEW + "')")
    public ApiResponse<Artifact> detail(@PathVariable String artifactId) {
        Artifact data = findByArtifactId(artifactId);
        artifactImageUrlResolver.enrichDisplayImageUrl(data);
        return ApiResponse.ok(data);
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_EDIT + "')")
    public ApiResponse<Artifact> create(@Valid @RequestBody ArtifactUpsertRequest request, Authentication auth) {
        int museumId = resolveMuseumId(request);
        String objectId = resolveObjectId(request, null);
        ArtifactId pk = new ArtifactId(museumId, objectId);
        if (artifactRepository.existsById(pk)) {
            throw new IllegalArgumentException("该馆别下文物编号已存在：" + museumLabelZh(museumId) + " / " + objectId);
        }
        Artifact data = new Artifact();
        apply(request, data, museumId, objectId, true);
        Artifact saved = artifactRepository.save(data);
        artifactImageUrlResolver.enrichDisplayImageUrl(saved);
        auditLogService.logDataChange(auth.getName(), "CREATE", "ARTIFACT", saved.getArtifactId(), saved.getName());
        return ApiResponse.ok("创建成功", saved);
    }

    @PutMapping("/{artifactId}")
    @Transactional
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_EDIT + "')")
    public ApiResponse<Artifact> update(@PathVariable String artifactId,
                                        @Valid @RequestBody ArtifactUpsertRequest request,
                                        Authentication auth) {
        Artifact managed = findByArtifactId(artifactId);
        ArtifactId oldPk = new ArtifactId(managed.getMuseumId(), managed.getObjectId());
        int museumId = resolveMuseumId(request);
        String objectId = resolveObjectId(request, managed.getObjectId());
        ArtifactId newPk = new ArtifactId(museumId, objectId);

        if (!oldPk.equals(newPk)) {
            if (artifactRepository.existsById(newPk)) {
                throw new IllegalArgumentException("该馆别下文物编号已存在：" + museumLabelZh(museumId) + " / " + objectId);
            }
            Artifact replacement = cloneForRekey(managed);
            apply(request, replacement, museumId, objectId, false);
            artifactRepository.delete(managed);
            artifactRepository.flush();
            Artifact saved = artifactRepository.save(replacement);
            artifactImageUrlResolver.enrichDisplayImageUrl(saved);
            auditLogService.logDataChange(auth.getName(), "UPDATE", "ARTIFACT", saved.getArtifactId(), saved.getName());
            return ApiResponse.ok("更新成功", saved);
        }

        apply(request, managed, museumId, objectId, false);
        Artifact saved = artifactRepository.save(managed);
        artifactImageUrlResolver.enrichDisplayImageUrl(saved);
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
        StringBuilder sb = new StringBuilder();
        sb.append(csvRow("artifactId", "museumId", "objectId", "name", "period", "type", "material",
                "description", "imageUrl", "detailUrl", "sourceSystem"));
        for (Artifact a : artifactRepository.findAll()) {
            sb.append(csvRow(
                    a.getArtifactId(),
                    a.getMuseumId(),
                    a.getObjectId(),
                    a.getName(),
                    a.getPeriod(),
                    a.getType(),
                    a.getMaterial(),
                    a.getDescription(),
                    a.getImageUrl(),
                    a.getDetailUrl(),
                    a.getSourceSystem()
            ));
        }
        return csvDownload("artifacts.csv", sb.toString());
    }

    private static ResponseEntity<byte[]> csvDownload(String fileName, String csv) {
        byte[] utf8 = csv.getBytes(StandardCharsets.UTF_8);
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] payload = new byte[bom.length + utf8.length];
        System.arraycopy(bom, 0, payload, 0, bom.length);
        System.arraycopy(utf8, 0, payload, bom.length, utf8.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(payload);
    }

    private static String csvRow(Object... cells) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(csvCell(cells[i]));
        }
        return row.append('\n').toString();
    }

    private static String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        text = text.replace("\"", "\"\"");
        return "\"" + text + "\"";
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_IMPORT_EXPORT + "')")
    public ApiResponse<Integer> importCsvFile(@RequestParam("file") MultipartFile file, Authentication auth) throws IOException {
        int count = artifactImportService.importFromMultipartFile(file);
        auditLogService.logDataChange(auth.getName(), "IMPORT", "ARTIFACT", "-", "count=" + count);
        return ApiResponse.ok("导入成功", count);
    }

    @GetMapping("/export/json")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_IMPORT_EXPORT + "')")
    public ResponseEntity<byte[]> exportJson() throws IOException {
        List<Artifact> list = artifactRepository.findAll();
        byte[] payload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(list);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"artifacts.json\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8")
                .body(payload);
    }

    @PostMapping(value = "/import/json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_IMPORT_EXPORT + "')")
    public ApiResponse<Integer> importJsonFile(@RequestParam("file") MultipartFile file, Authentication auth) throws IOException {
        int count = artifactImportService.importFromJsonFile(file);
        auditLogService.logDataChange(auth.getName(), "IMPORT", "ARTIFACT", "-", "jsonCount=" + count);
        return ApiResponse.ok("JSON 导入成功", count);
    }

    @PostMapping(value = "/images/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_EDIT + "')")
    public ApiResponse<BatchImageUploadResult> batchUploadImages(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "museumId", required = false) Integer museumId,
            @RequestParam(value = "replaceOnly", defaultValue = "false") boolean replaceOnly,
            @RequestParam(value = "mappingCsv", required = false) MultipartFile mappingCsv,
            Authentication auth) throws IOException {
        BatchImageUploadResult result = artifactImageBatchService.batchUpload(files, museumId, replaceOnly, mappingCsv);
        auditLogService.logDataChange(auth.getName(), "BATCH_IMAGE", "ARTIFACT", "-",
                "uploaded=" + result.getUploaded() + ",replaced=" + result.getReplaced());
        return ApiResponse.ok("批量图片处理完成", result);
    }

    private Artifact findByArtifactId(String artifactId) {
        String decoded = URLDecoder.decode(artifactId, StandardCharsets.UTF_8);
        return artifactRepository.findByArtifactId(decoded)
                .orElseThrow(() -> new IllegalArgumentException("文物不存在"));
    }

    private static Specification<Artifact> buildFilterSpec(
            String keyword,
            String sourceSystem,
            String period,
            String type) {
        boolean hasFilter = StringUtils.hasText(keyword)
                || StringUtils.hasText(sourceSystem)
                || StringUtils.hasText(period)
                || StringUtils.hasText(type);
        if (!hasFilter) {
            return null;
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("id").get("objectId")), like),
                        cb.like(cb.lower(root.get("period")), like),
                        cb.like(cb.lower(root.get("type")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("material"), "")), like),
                        cb.like(cb.lower(root.get("description")), like)
                ));
            }
            if (StringUtils.hasText(sourceSystem)) {
                Integer museumId = resolveMuseumIdFromSource(sourceSystem.trim());
                if (museumId != null) {
                    predicates.add(cb.equal(root.get("id").get("museumId"), museumId));
                }
            }
            if (StringUtils.hasText(period)) {
                predicates.add(cb.like(cb.lower(root.get("period")),
                        "%" + period.trim().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(type)) {
                predicates.add(cb.like(cb.lower(root.get("type")),
                        "%" + type.trim().toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Integer resolveMuseumIdFromSource(String sourceSystem) {
        return switch (sourceSystem.toLowerCase()) {
            case "smithsonian", "1" -> 1;
            case "harvard", "2" -> 2;
            case "mfa", "3" -> 3;
            default -> null;
        };
    }

    private static int resolveMuseumId(ArtifactUpsertRequest req) {
        if (req.museumId() != null) {
            return req.museumId();
        }
        if (!StringUtils.hasText(req.sourceSystem())) {
            throw new IllegalArgumentException("请选择所属馆别");
        }
        return switch (req.sourceSystem().trim().toLowerCase()) {
            case "harvard" -> 2;
            case "mfa" -> 3;
            case "smithsonian" -> 1;
            default -> throw new IllegalArgumentException("未知馆别来源：" + req.sourceSystem());
        };
    }

    private static String resolveObjectId(ArtifactUpsertRequest req, String existingObjectId) {
        if (StringUtils.hasText(req.objectId())) {
            return req.objectId().trim();
        }
        if (StringUtils.hasText(req.sourceId())) {
            return req.sourceId().trim();
        }
        if (StringUtils.hasText(existingObjectId)) {
            return existingObjectId;
        }
        throw new IllegalArgumentException("请填写文物编号");
    }

    private void apply(ArtifactUpsertRequest req, Artifact data, int museumId, String objectId, boolean creating) {
        data.setMuseumId(museumId);
        data.setObjectId(objectId);
        data.setTitle(req.name().trim());
        data.setPeriod(req.period().trim());
        data.setType(req.type().trim());
        data.setMaterial(StringUtils.hasText(req.material()) ? req.material().trim() : null);
        data.setDescription(req.description().trim());
        data.setMuseum(StringUtils.hasText(req.museum()) ? req.museum().trim() : museumLabel(museumId));
        data.setLocation(StringUtils.hasText(req.location()) ? req.location().trim() : museumLabel(museumId));
        String imageUrl = req.imageUrl().trim();
        data.setImageUrl(imageUrl);
        data.setImagePath(imageUrl);
        data.setDetailUrl(StringUtils.hasText(req.detailUrl()) ? req.detailUrl().trim() : imageUrl);
        if (creating || data.getCrawlDate() == null) {
            data.setCrawlDate(LocalDate.now());
        }
        data.setArtifactId("entity:artifact:" + museumId + ":" + objectId);
    }

    private static Artifact cloneForRekey(Artifact from) {
        Artifact to = new Artifact();
        to.setArtist(from.getArtist());
        to.setArtistProvince(from.getArtistProvince());
        to.setDynasty(from.getDynasty());
        to.setArtistWikidataId(Objects.requireNonNullElse(from.getArtistWikidataId(), ""));
        to.setArtistBirth(Objects.requireNonNullElse(from.getArtistBirth(), ""));
        to.setArtistDeath(Objects.requireNonNullElse(from.getArtistDeath(), ""));
        to.setArtistBio(Objects.requireNonNullElse(from.getArtistBio(), ""));
        to.setArtistWikipediaSummary(Objects.requireNonNullElse(from.getArtistWikipediaSummary(), ""));
        to.setArtistEnrichedAt(Objects.requireNonNullElse(from.getArtistEnrichedAt(), ""));
        to.setPeriodStartYear(from.getPeriodStartYear());
        to.setPeriodEndYear(from.getPeriodEndYear());
        to.setCulture(from.getCulture());
        to.setProvenance(from.getProvenance());
        to.setBibliography(from.getBibliography());
        to.setDimensions(from.getDimensions());
        to.setImageUrls(from.getImageUrls());
        to.setIiifManifestUrl(from.getIiifManifestUrl());
        to.setImagePaths(from.getImagePaths());
        to.setImageCount(from.getImageCount() == null ? (short) 0 : from.getImageCount());
        to.setCreditLine(from.getCreditLine());
        to.setAccessionNumber(from.getAccessionNumber());
        to.setCrawlDate(from.getCrawlDate());
        return to;
    }

    private static String museumLabel(int museumId) {
        return switch (museumId) {
            case 1 -> "Smithsonian";
            case 2 -> "Harvard";
            case 3 -> "MFA";
            default -> "Museum " + museumId;
        };
    }

    private static String museumLabelZh(int museumId) {
        return switch (museumId) {
            case 1 -> "史密森尼";
            case 2 -> "哈佛";
            case 3 -> "波士顿美术博物馆";
            default -> "馆别" + museumId;
        };
    }
}
