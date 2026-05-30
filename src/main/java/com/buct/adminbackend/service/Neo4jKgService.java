package com.buct.adminbackend.service;

import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@ConditionalOnBean(Driver.class)
@RequiredArgsConstructor
public class Neo4jKgService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern RELATION_TYPE = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Set<String> ENTITY_LABELS = Set.of(
            "Artifact", "Museum", "Dynasty", "Artist", "Material", "ArtifactType", "Location", "Culture"
    );
    private static final Set<String> EXCLUDED_NODE_LABELS = Set.of(
            "KgEntity", "KgRelationDef", "KgCounter", "KgSyncJob", "EntityAlias", "EntitySource", "RelationType"
    );
    private static final Map<String, String> ENTITY_LABEL_ZH = Map.of(
            "Artifact", "文物",
            "Museum", "博物馆",
            "Dynasty", "朝代",
            "Artist", "艺术家",
            "Material", "材质",
            "ArtifactType", "文物类型",
            "Location", "地点",
            "Culture", "文化"
    );

    private final Driver driver;

    public Map<String, Object> getOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("entityCount", countGraphEntities());
        overview.put("tripleCount", countGraphTriples());
        overview.put("artifactCount", countByLabel("Artifact"));
        overview.put("museumCount", countByLabel("Museum"));
        overview.put("relationTypeCount", countRelationTypes());
        overview.put("dataSource", "graph-db 图谱");
        return overview;
    }

    public List<String> listEntityLabels() {
        return new ArrayList<>(ENTITY_LABELS);
    }

    public Map<String, String> getEntityLabelDictionary() {
        return new LinkedHashMap<>(ENTITY_LABEL_ZH);
    }

    public Map<String, Object> listEntitiesPage(String label, int page, int size, String sort, String keyword) {
        String safeLabel = requireEntityLabel(label);
        int safeSize = clampSize(size);
        int safePage = Math.max(page, 0);
        String orderBy = "updated".equalsIgnoreCase(sort)
                ? "n.updated_at DESC, coalesce(n.title, n.name, n.uri)"
                : "coalesce(n.title, n.name, n.uri)";
        String whereClause = entityKeywordWhere(keyword);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("skip", (long) safePage * safeSize);
        params.put("limit", safeSize);
        if (StringUtils.hasText(keyword)) {
            params.put("keyword", keyword.trim());
        }
        long total = readCount(
                "MATCH (n:Entity:%s) %s RETURN count(n) AS c".formatted(safeLabel, whereClause),
                params);
        List<Map<String, Object>> items = readList("""
                MATCH (n:Entity:%s) %s
                RETURN n.uri AS uri, coalesce(n.title, n.name, n.uri) AS name, labels(n) AS labels,
                       n.title AS title, n.name AS nodeName, n.museum_id AS museumId, n.object_id AS objectId,
                       n.updated_at AS updatedAt
                ORDER BY %s
                SKIP $skip LIMIT $limit
                """.formatted(safeLabel, whereClause, orderBy),
                params,
                this::mapGraphEntityRow);
        return pageResult(items, safePage, safeSize, total);
    }

    public List<Map<String, Object>> listEntityOptions(String label, String keyword, int limit) {
        String safeLabel = requireEntityLabel(label);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        String whereClause = entityKeywordWhere(keyword);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("limit", safeLimit);
        if (StringUtils.hasText(keyword)) {
            params.put("keyword", keyword.trim());
        }
        return readList("""
                MATCH (n:Entity:%s) %s
                RETURN n.uri AS uri, coalesce(n.title, n.name, n.uri) AS name, labels(n) AS labels
                ORDER BY coalesce(n.title, n.name, n.uri)
                LIMIT $limit
                """.formatted(safeLabel, whereClause), params, record -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uri", record.get("uri").asString(""));
            row.put("name", record.get("name").asString(""));
            List<Object> labels = new ArrayList<>();
            record.get("labels").asList(v -> labels.add(v.asString()));
            String lb = labels.stream().map(String::valueOf).filter(ENTITY_LABELS::contains).findFirst().orElse("Entity");
            row.put("label", lb);
            return row;
        });
    }

    public Map<String, Object> getEntityByUri(String uri) {
        String safeUri = requiredText(uri, "实体 uri");
        return findOne("""
                MATCH (n:Entity {uri: $uri})
                RETURN n.uri AS uri, coalesce(n.title, n.name, n.uri) AS name, labels(n) AS labels,
                       n.title AS title, n.name AS nodeName, n.museum_id AS museumId, n.object_id AS objectId,
                       properties(n) AS allProps
                """, Map.of("uri", safeUri), record -> {
            Map<String, Object> row = mapGraphEntityRow(record);
            row.put("properties", filterNodeProperties(record.get("allProps").asMap()));
            return row;
        }).orElseThrow(() -> new IllegalArgumentException("实体不存在：" + safeUri));
    }

    public Map<String, Object> createEntity(Map<String, Object> body) {
        String label = requireEntityLabel(textOrDefault(body, "label", "Artifact"));
        String uri = requiredText(body, "uri", "实体 uri");
        if (entityExists(uri)) {
            throw new IllegalArgumentException("uri 已存在：" + uri + "。请更换 uri 后新增，或在列表中点「编辑」修改已有实体");
        }
        Map<String, Object> props = extractNodeProperties(body);
        props.put("uri", uri);
        if ("Artifact".equals(label) && !props.containsKey("artifact_id")) {
            props.put("artifact_id", uri);
        }
        write("""
                CREATE (n:Entity:%s {uri: $uri})
                SET n += $props
                SET n.updated_at = datetime()
                """.formatted(label), Map.of("uri", uri, "props", props));
        return getEntityByUri(uri);
    }

    public Map<String, Object> updateEntity(String uri, Map<String, Object> body) {
        getEntityByUri(uri);
        Map<String, Object> props = extractNodeProperties(body);
        props.remove("uri");
        write("""
                MATCH (n:Entity {uri: $uri})
                SET n += $props
                SET n.updated_at = datetime()
                """, Map.of("uri", uri, "props", props));
        return getEntityByUri(uri);
    }

    public void deleteEntity(String uri) {
        getEntityByUri(uri);
        write("MATCH (n:Entity {uri: $uri}) DETACH DELETE n", Map.of("uri", uri));
    }

    /** 图数据库中的关系类型及数量（合并 RelationType 元数据与边上的实际类型） */
    public Map<String, Object> listRelationsPage(int page, int size, String keyword) {
        List<Map<String, Object>> all = filterRelationTypesByKeyword(mergeRelationTypes(), keyword);
        int safeSize = clampSize(size);
        int safePage = Math.max(page, 0);
        long total = all.size();
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return pageResult(all.subList(from, to), safePage, safeSize, total);
    }

    public List<Map<String, Object>> listRelationTypesAll() {
        return mergeRelationTypes();
    }

    public Map<String, Object> getRelationType(String name) {
        String safeName = sanitizeRelationType(requiredText(name, "关系类型"));
        return mergeRelationTypes().stream()
                .filter(row -> safeName.equals(row.get("name")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("关系类型不存在：" + safeName));
    }

    public Map<String, Object> createRelationType(Map<String, Object> body) {
        String name = sanitizeRelationType(requiredText(body, "name", "关系类型名称"));
        if (relationTypeMetaExists(name) || relationTypeEdgeCount(name) > 0) {
            throw new IllegalArgumentException("关系类型已存在：" + name);
        }
        write("""
                CREATE (rt:RelationType {name: $name, updated_at: datetime()})
                """, Map.of("name", name));
        return getRelationType(name);
    }

    public void deleteRelationType(String name, boolean cascadeEdges) {
        String safeName = sanitizeRelationType(requiredText(name, "关系类型"));
        long edgeCount = relationTypeEdgeCount(safeName);
        if (!cascadeEdges && edgeCount > 0) {
            throw new IllegalArgumentException("关系类型「" + safeName + "」仍有 " + edgeCount
                    + " 条三元组，请确认同时删除全部三元组，或先到「三元组」页逐条删除");
        }
        if (cascadeEdges && edgeCount > 0) {
            write("""
                    MATCH ()-[r]->()
                    WHERE type(r) = $name
                      AND NOT startNode(r):KgSyncJob AND NOT startNode(r):KgCounter
                    DELETE r
                    """, Map.of("name", safeName));
        }
        write("MATCH (rt:RelationType {name: $name}) DELETE rt", Map.of("name", safeName));
    }

    public Map<String, Object> listTriplesPage(String relationType, int page, int size, String sort, String keyword) {
        int safeSize = clampSize(size);
        int safePage = Math.max(page, 0);
        String orderBy = "updated".equalsIgnoreCase(sort)
                ? "r.updated_at DESC, type(r), subject"
                : "type(r), subject";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("skip", (long) safePage * safeSize);
        params.put("limit", safeSize);
        StringBuilder filters = new StringBuilder("""
                WHERE NOT type(r) IN ['KG_TRIPLE']
                  AND NOT s:KgSyncJob AND NOT s:KgCounter
                """);
        if (StringUtils.hasText(relationType)) {
            filters.append(" AND type(r) = $relationType ");
            params.put("relationType", sanitizeRelationType(relationType));
        }
        if (StringUtils.hasText(keyword)) {
            filters.append("""
                     AND (toLower(coalesce(s.title, s.name, s.uri, '')) CONTAINS toLower($keyword)
                       OR toLower(coalesce(o.title, o.name, o.uri, '')) CONTAINS toLower($keyword)
                       OR toLower(type(r)) CONTAINS toLower($keyword))
                    """);
            params.put("keyword", keyword.trim());
        }
        String filterBlock = filters.toString();
        long total = readCount("""
                MATCH (s:Entity)-[r]->(o:Entity)
                %s
                RETURN count(r) AS c
                """.formatted(filterBlock), params);
        List<Map<String, Object>> items = readList("""
                MATCH (s:Entity)-[r]->(o:Entity)
                %s
                RETURN elementId(r) AS id, type(r) AS relationType,
                       s.uri AS fromUri, coalesce(s.title, s.name, s.uri) AS subject,
                       o.uri AS toUri, coalesce(o.title, o.name, o.uri) AS object,
                       r.updated_at AS updatedAt
                ORDER BY %s
                SKIP $skip LIMIT $limit
                """.formatted(filterBlock, orderBy), params, this::mapGraphTripleRow);
        return pageResult(items, safePage, safeSize, total);
    }

    public Map<String, Object> getTriple(String elementId) {
        return findOne("""
                MATCH (s:Entity)-[r]->(o:Entity)
                WHERE elementId(r) = $id
                RETURN elementId(r) AS id, type(r) AS relationType,
                       s.uri AS fromUri, coalesce(s.title, s.name, s.uri) AS subject,
                       o.uri AS toUri, coalesce(o.title, o.name, o.uri) AS object
                """, Map.of("id", elementId), this::mapGraphTripleRow)
                .orElseThrow(() -> new IllegalArgumentException("三元组不存在：" + elementId));
    }

    public Map<String, Object> createTriple(Map<String, Object> body) {
        String relationType = sanitizeRelationType(requiredText(body, "relationType", "关系类型"));
        String fromUri = requiredText(body, "fromUri", "主语 uri");
        String toUri = requiredText(body, "toUri", "宾语 uri");
        getEntityByUri(fromUri);
        getEntityByUri(toUri);
        if (findTripleByEndpoints(fromUri, toUri, relationType).isPresent()) {
            throw new IllegalArgumentException("相同三元组已存在（" + relationType + "）");
        }
        write("""
                MATCH (s:Entity {uri: $fromUri}), (o:Entity {uri: $toUri})
                CREATE (s)-[r:%s]->(o)
                SET r.updated_at = datetime()
                """.formatted(relationType),
                Map.of("fromUri", fromUri, "toUri", toUri));
        return findTripleByEndpoints(fromUri, toUri, relationType)
                .orElseThrow(() -> new IllegalStateException("三元组创建失败"));
    }

    public Map<String, Object> updateTriple(String elementId, Map<String, Object> body) {
        getTriple(elementId);
        deleteTriple(elementId);
        return createTriple(body);
    }

    public void deleteTriple(String elementId) {
        getTriple(elementId);
        write("MATCH ()-[r]->() WHERE elementId(r) = $id DELETE r", Map.of("id", elementId));
    }

    public List<Map<String, Object>> listSyncJobs() {
        return readList("""
                MATCH (j:KgSyncJob)
                RETURN j.id AS id, j.status AS status, j.startedAt AS startedAt,
                       j.finishedAt AS finishedAt, j.message AS message
                ORDER BY j.id DESC
                """, this::mapSyncJobRow);
    }

    public Map<String, Object> triggerSync(String triggerBy) {
        long entityCount = countGraphEntities();
        long tripleCount = countGraphTriples();
        long jobId = nextId("syncJob");
        String startedAt = now();
        String message = "图数据库已同步：实体 " + entityCount + " 个、三元组 " + tripleCount
                + " 条（触发方：" + triggerBy + "）";
        write("""
                CREATE (j:KgSyncJob {
                    id: $id,
                    status: 'SUCCESS',
                    startedAt: $startedAt,
                    finishedAt: $finishedAt,
                    message: $message
                })
                """, Map.of(
                "id", jobId,
                "startedAt", startedAt,
                "finishedAt", now(),
                "message", message
        ));
        return mapSyncJobRow(readOne("""
                MATCH (j:KgSyncJob {id: $id})
                RETURN j.id AS id, j.status AS status, j.startedAt AS startedAt,
                       j.finishedAt AS finishedAt, j.message AS message
                """, Map.of("id", jobId)));
    }

    private Optional<Map<String, Object>> findTripleByEndpoints(String fromUri, String toUri, String relationType) {
        return findOne("""
                MATCH (s:Entity {uri: $fromUri})-[r:%s]->(o:Entity {uri: $toUri})
                RETURN elementId(r) AS id, type(r) AS relationType,
                       s.uri AS fromUri, coalesce(s.title, s.name, s.uri) AS subject,
                       o.uri AS toUri, coalesce(o.title, o.name, o.uri) AS object
                """.formatted(relationType),
                Map.of("fromUri", fromUri, "toUri", toUri),
                this::mapGraphTripleRow);
    }

    private long countGraphEntities() {
        return readCount("""
                MATCH (n:Entity)
                WHERE none(l IN labels(n) WHERE l IN $excluded)
                RETURN count(n) AS c
                """, Map.of("excluded", new ArrayList<>(EXCLUDED_NODE_LABELS)));
    }

    private long countGraphTriples() {
        return readCount("""
                MATCH (:Entity)-[r]->(:Entity)
                WHERE NOT type(r) IN ['KG_TRIPLE']
                RETURN count(r) AS c
                """);
    }

    private long countRelationTypes() {
        return mergeRelationTypes().size();
    }

    private List<Map<String, Object>> mergeRelationTypes() {
        Set<String> registered = new LinkedHashSet<>();
        readList("""
                MATCH (rt:RelationType)
                RETURN rt.name AS name
                ORDER BY rt.name
                """, record -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", record.get("name").asString(""));
            return row;
        }).forEach(row -> registered.add(String.valueOf(row.get("name"))));

        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        readList("""
                MATCH ()-[r]->()
                WHERE NOT type(r) IN ['KG_TRIPLE']
                  AND NOT startNode(r):KgSyncJob AND NOT startNode(r):KgCounter
                RETURN type(r) AS name, count(r) AS count
                ORDER BY count DESC, name ASC
                """, record -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", record.get("name").asString(""));
            row.put("count", record.get("count").asLong());
            return row;
        }).forEach(row -> {
            String name = String.valueOf(row.get("name"));
            row.put("registered", registered.contains(name));
            merged.put(name, row);
        });
        registered.forEach(name -> {
            if (!merged.containsKey(name)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", name);
                row.put("count", 0L);
                row.put("registered", true);
                merged.put(name, row);
            }
        });
        return new ArrayList<>(merged.values());
    }

    private List<Map<String, Object>> filterRelationTypesByKeyword(List<Map<String, Object>> items, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return items;
        }
        String kw = keyword.trim().toLowerCase();
        return items.stream()
                .filter(row -> String.valueOf(row.getOrDefault("name", "")).toLowerCase().contains(kw))
                .toList();
    }

    private boolean relationTypeMetaExists(String name) {
        return readCount("MATCH (rt:RelationType {name: $name}) RETURN count(rt) AS c", Map.of("name", name)) > 0;
    }

    private long relationTypeEdgeCount(String name) {
        return readCount("""
                MATCH ()-[r]->()
                WHERE type(r) = $name
                  AND NOT startNode(r):KgSyncJob AND NOT startNode(r):KgCounter
                RETURN count(r) AS c
                """, Map.of("name", name));
    }

    private long countByLabel(String label) {
        return readCount("MATCH (n:" + label + ") RETURN count(n) AS c");
    }

    private long readCount(String cypher) {
        return readCount(cypher, Map.of());
    }

    private long readCount(String cypher, Map<String, Object> params) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> tx.run(cypher, params).single().get("c").asLong());
        } catch (Neo4jException e) {
            throw wrapNeo4j(e);
        }
    }

    private long nextId(String counterName) {
        try (Session session = driver.session()) {
            return session.executeWrite(tx -> tx.run("""
                    MERGE (c:KgCounter {name: $name})
                    ON CREATE SET c.val = 1
                    ON MATCH SET c.val = c.val + 1
                    RETURN c.val AS val
                    """, Map.of("name", counterName)).single().get("val").asLong());
        } catch (Neo4jException e) {
            throw wrapNeo4j(e);
        }
    }

    private List<Map<String, Object>> readList(String cypher, RowMapper mapper) {
        return readList(cypher, Map.of(), mapper);
    }

    private List<Map<String, Object>> readList(String cypher, Map<String, Object> params, RowMapper mapper) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(cypher, params);
                List<Map<String, Object>> rows = new ArrayList<>();
                while (result.hasNext()) {
                    rows.add(mapper.map(result.next()));
                }
                return rows;
            });
        } catch (Neo4jException e) {
            throw wrapNeo4j(e);
        }
    }

    private Optional<Map<String, Object>> findOne(String cypher, Map<String, Object> params, RowMapper mapper) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(cypher, params);
                return result.hasNext() ? Optional.of(mapper.map(result.next())) : Optional.empty();
            });
        } catch (Neo4jException e) {
            throw wrapNeo4j(e);
        }
    }

    private Record readOne(String cypher, Map<String, Object> params) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> tx.run(cypher, params).single());
        } catch (Neo4jException e) {
            throw wrapNeo4j(e);
        }
    }

    private void write(String cypher, Map<String, Object> params) {
        try (Session session = driver.session()) {
            session.executeWrite((TransactionContext tx) -> {
                tx.run(cypher, params);
                return null;
            });
        } catch (Neo4jException e) {
            throw wrapNeo4j(e);
        }
    }

    private Map<String, Object> mapGraphEntityRow(Record record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uri", record.get("uri").asString(""));
        row.put("name", record.get("name").asString(""));
        List<Object> labels = new ArrayList<>();
        record.get("labels").asList(v -> labels.add(v.asString()));
        String label = labels.stream()
                .map(String::valueOf)
                .filter(l -> ENTITY_LABELS.contains(l))
                .findFirst()
                .orElse("Entity");
        row.put("label", label);
        if (record.containsKey("title") && !record.get("title").isNull()) {
            row.put("title", record.get("title").asString(""));
        }
        if (record.containsKey("nodeName") && !record.get("nodeName").isNull()) {
            row.put("nodeName", record.get("nodeName").asString(""));
        }
        if (record.containsKey("museumId") && !record.get("museumId").isNull()) {
            row.put("museum_id", flexibleScalar(record.get("museumId")));
        }
        if (record.containsKey("objectId") && !record.get("objectId").isNull()) {
            row.put("object_id", record.get("objectId").asString(""));
        }
        return row;
    }

    private Map<String, Object> mapGraphTripleRow(Record record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", record.get("id").asString(""));
        row.put("relationType", record.get("relationType").asString(""));
        row.put("fromUri", record.get("fromUri").asString(""));
        row.put("subject", record.get("subject").asString(""));
        row.put("toUri", record.get("toUri").asString(""));
        row.put("object", record.get("object").asString(""));
        return row;
    }

    private Map<String, Object> mapSyncJobRow(Record record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", record.get("id").asLong());
        row.put("status", record.get("status").asString(""));
        row.put("startedAt", record.get("startedAt").asString(""));
        row.put("finishedAt", record.get("finishedAt").asString(""));
        row.put("message", record.get("message").asString(""));
        return row;
    }

    private Map<String, Object> extractNodeProperties(Map<String, Object> body) {
        Map<String, Object> props = new LinkedHashMap<>();
        if (body.get("properties") instanceof Map<?, ?> map) {
            map.forEach((k, v) -> props.put(String.valueOf(k), v));
        }
        for (String key : List.of("title", "name", "museum_id", "object_id", "artifact_id")) {
            if (body.containsKey(key) && body.get(key) != null) {
                props.put(key, body.get(key));
            }
        }
        return props;
    }

    private Map<String, Object> filterNodeProperties(Map<String, Object> all) {
        Map<String, Object> props = new LinkedHashMap<>();
        all.forEach((k, v) -> {
            if (!Set.of("uri", "updated_at").contains(k)) {
                props.put(k, v);
            }
        });
        return props;
    }

    private String requireEntityLabel(String label) {
        if (!StringUtils.hasText(label) || !ENTITY_LABELS.contains(label)) {
            throw new IllegalArgumentException("不支持的实体类型：" + label + "，可选：" + ENTITY_LABELS);
        }
        return label;
    }

    private boolean entityExists(String uri) {
        return readCount("MATCH (n:Entity {uri: $uri}) RETURN count(n) AS c", Map.of("uri", uri)) > 0;
    }

    private String entityKeywordWhere(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return "";
        }
        return """
                WHERE (toLower(coalesce(n.title, n.name, '')) CONTAINS toLower($keyword)
                   OR toLower(n.uri) CONTAINS toLower($keyword))
                """;
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private static Map<String, Object> pageResult(List<Map<String, Object>> items, int page, int size, long total) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("page", page);
        result.put("size", size);
        result.put("total", total);
        result.put("totalPages", size > 0 ? (int) ((total + size - 1) / size) : 0);
        return result;
    }

    private String sanitizeRelationType(String name) {
        String cleaned = name == null ? "" : name.trim();
        if (!RELATION_TYPE.matcher(cleaned).matches()) {
            throw new IllegalArgumentException("关系类型不合法：" + name);
        }
        return cleaned;
    }

    private String requiredText(Map<String, Object> body, String key, String label) {
        String value = textOrDefault(body, key, null);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("请填写" + label);
        }
        return value.trim();
    }

    private String requiredText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("请填写" + label);
        }
        return value.trim();
    }

    private String textOrDefault(Map<String, Object> body, String key, String defaultValue) {
        Object value = body.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private static String now() {
        return LocalDateTime.now().format(TS);
    }

    private static Object flexibleScalar(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return switch (value.type().name()) {
            case "INTEGER" -> value.asLong();
            case "FLOAT", "DOUBLE" -> value.asDouble();
            case "BOOLEAN" -> value.asBoolean();
            case "STRING" -> value.asString();
            default -> value.asObject();
        };
    }

    private IllegalStateException wrapNeo4j(Neo4jException e) {
        return new IllegalStateException("Neo4j 查询失败: " + e.getMessage());
    }

    @FunctionalInterface
    private interface RowMapper {
        Map<String, Object> map(Record record);
    }
}
