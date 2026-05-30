package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.config.KgNeo4jProperties;
import com.buct.adminbackend.security.PermissionCodes;
import com.buct.adminbackend.service.Neo4jKgService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/kg")
@RequiredArgsConstructor
@ConditionalOnBean(Neo4jKgService.class)
public class KgController {

    private final Neo4jKgService neo4jKgService;
    private final KgNeo4jProperties kgNeo4jProperties;

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_VIEW + "')")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of(
                "mode", "neo4j",
                "uri", kgNeo4jProperties.getUri(),
                "enabled", kgNeo4jProperties.isEnabled()
        ));
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_VIEW + "')")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(neo4jKgService.getOverview());
    }

    @GetMapping("/entity-labels")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_VIEW + "')")
    public ApiResponse<List<String>> entityLabels() {
        return ApiResponse.ok(neo4jKgService.listEntityLabels());
    }

    @GetMapping("/entities")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_VIEW + "')")
    public ApiResponse<Map<String, Object>> listEntities(
            @RequestParam(defaultValue = "Artifact") String label,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(neo4jKgService.listEntitiesPage(label, page, size, sort, keyword));
    }

    @GetMapping("/entities/options")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_VIEW + "')")
    public ApiResponse<List<Map<String, Object>>> entityOptions(
            @RequestParam(defaultValue = "Artifact") String label,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(neo4jKgService.listEntityOptions(label, keyword, limit));
    }

    @GetMapping("/entities/detail")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_VIEW + "')")
    public ApiResponse<Map<String, Object>> getEntity(@RequestParam String uri) {
        return ApiResponse.ok(neo4jKgService.getEntityByUri(uri));
    }

    @PostMapping("/entities")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_EDIT + "')")
    public ApiResponse<Map<String, Object>> createEntity(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok("创建成功", neo4jKgService.createEntity(body));
    }

    @PutMapping("/entities")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_EDIT + "')")
    public ApiResponse<Map<String, Object>> updateEntity(
            @RequestParam String uri,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok("更新成功", neo4jKgService.updateEntity(uri, body));
    }

    @DeleteMapping("/entities")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_DELETE + "')")
    public ApiResponse<Void> deleteEntity(@RequestParam String uri) {
        neo4jKgService.deleteEntity(uri);
        return ApiResponse.ok("删除成功", null);
    }

    @GetMapping("/relations")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_VIEW + "')")
    public ApiResponse<Map<String, Object>> listRelations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(neo4jKgService.listRelationsPage(page, size, keyword));
    }

    @GetMapping("/relation-types/detail")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_VIEW + "')")
    public ApiResponse<Map<String, Object>> getRelationType(@RequestParam String name) {
        return ApiResponse.ok(neo4jKgService.getRelationType(name));
    }

    @PostMapping("/relation-types")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_EDIT + "')")
    public ApiResponse<Map<String, Object>> createRelationType(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok("创建成功", neo4jKgService.createRelationType(body));
    }

    @DeleteMapping("/relation-types")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_DELETE + "')")
    public ApiResponse<Void> deleteRelationType(
            @RequestParam String name,
            @RequestParam(defaultValue = "false") boolean cascadeEdges) {
        neo4jKgService.deleteRelationType(name, cascadeEdges);
        return ApiResponse.ok("删除成功", null);
    }

    @GetMapping("/entity-label-dictionary")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_VIEW + "')")
    public ApiResponse<Map<String, String>> entityLabelDictionary() {
        return ApiResponse.ok(neo4jKgService.getEntityLabelDictionary());
    }

    @GetMapping("/relation-types")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_VIEW + "')")
    public ApiResponse<List<Map<String, Object>>> listRelationTypes() {
        return ApiResponse.ok(neo4jKgService.listRelationTypesAll());
    }

    @GetMapping("/triples")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_VIEW + "')")
    public ApiResponse<Map<String, Object>> listTriples(
            @RequestParam(required = false) String relationType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(neo4jKgService.listTriplesPage(relationType, page, size, sort, keyword));
    }

    @GetMapping("/triples/detail")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_VIEW + "')")
    public ApiResponse<Map<String, Object>> getTriple(@RequestParam String id) {
        return ApiResponse.ok(neo4jKgService.getTriple(id));
    }

    @PostMapping("/triples")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_EDIT + "')")
    public ApiResponse<Map<String, Object>> createTriple(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok("创建成功", neo4jKgService.createTriple(body));
    }

    @PutMapping("/triples")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_EDIT + "')")
    public ApiResponse<Map<String, Object>> updateTriple(
            @RequestParam String id,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok("更新成功", neo4jKgService.updateTriple(id, body));
    }

    @DeleteMapping("/triples")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_DELETE + "')")
    public ApiResponse<Void> deleteTriple(@RequestParam String id) {
        neo4jKgService.deleteTriple(id);
        return ApiResponse.ok("删除成功", null);
    }

    @GetMapping("/sync/jobs")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_VIEW + "')")
    public ApiResponse<List<Map<String, Object>>> listSyncJobs() {
        return ApiResponse.ok(neo4jKgService.listSyncJobs());
    }

    @PostMapping("/sync/jobs")
    @PreAuthorize("hasAuthority('" + PermissionCodes.AUTHORITY_PREFIX + PermissionCodes.ARTIFACT_EDIT + "')")
    public ApiResponse<Map<String, Object>> triggerSync(@RequestBody(required = false) Map<String, Object> body) {
        String triggerBy = body != null && body.get("triggerBy") != null
                ? String.valueOf(body.get("triggerBy")) : "admin-backend";
        return ApiResponse.ok("同步任务已完成", neo4jKgService.triggerSync(triggerBy));
    }
}
