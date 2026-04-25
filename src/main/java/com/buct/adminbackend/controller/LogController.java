package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.entity.DataChangeLog;
import com.buct.adminbackend.entity.LoginLog;
import com.buct.adminbackend.entity.OperationLog;
import com.buct.adminbackend.entity.SystemLog;
import com.buct.adminbackend.repository.DataChangeLogRepository;
import com.buct.adminbackend.repository.LoginLogRepository;
import com.buct.adminbackend.repository.OperationLogRepository;
import com.buct.adminbackend.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class LogController {

    /** 与待审核/审核操作相关的前台操作日志类型（内容审核员仅可查看这些） */
    public static final Set<String> REVIEW_RELATED_OPERATION_TYPES = Set.of(
            "CREATE_REVIEW_CONTENT",
            "REVIEW_CONTENT",
            "BATCH_REVIEW_CONTENT"
    );

    private final OperationLogRepository operationLogRepository;
    private final LoginLogRepository loginLogRepository;
    private final DataChangeLogRepository dataChangeLogRepository;
    private final SystemLogRepository systemLogRepository;

    private static final Set<String> SECURITY_OPERATION_TYPES = Set.of(
            "UPDATE_PLATFORM_USER_PERMISSION",
            "UPDATE_UNIFIED_USER_PERMISSION",
            "UPDATE_ADMIN",
            "UPDATE_ADMIN_STATUS",
            "CREATE_ADMIN",
            "DELETE_ADMIN"
    );

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN','CONTENT_REVIEWER')")
    public ApiResponse<List<OperationLog>> list(
            Authentication authentication,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        if (isSuperOrDataAdmin(authentication)) {
            return ApiResponse.ok(filterOperationLogs(
                    operationLogRepository.findAll(Sort.by(Sort.Direction.DESC, "operationTime")),
                    operator, operationType, keyword, from, to));
        }
        List<OperationLog> base = operationLogRepository.findByOperationTypeIn(
                REVIEW_RELATED_OPERATION_TYPES,
                Sort.by(Sort.Direction.DESC, "operationTime"));
        return ApiResponse.ok(filterOperationLogs(base, operator, operationType, keyword, from, to));
    }

    @GetMapping("/system")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<List<SystemLog>> systemLogs(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<SystemLog> all = systemLogRepository.findAll(Sort.by(Sort.Direction.DESC, "logTime"));
        List<SystemLog> filtered = all.stream()
                .filter(x -> !hasText(level) || equalsIgnoreCase(x.getLevel(), level))
                .filter(x -> !hasText(eventType) || equalsIgnoreCase(x.getEventType(), eventType))
                .filter(x -> !hasText(keyword) || containsAny(keyword, x.getSource(), x.getMessage(), x.getStackTrace()))
                .filter(x -> from == null || (x.getLogTime() != null && !x.getLogTime().isBefore(from)))
                .filter(x -> to == null || (x.getLogTime() != null && !x.getLogTime().isAfter(to)))
                .toList();
        return ApiResponse.ok(filtered);
    }

    @GetMapping("/security")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<List<Map<String, Object>>> securityLogs(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<Map<String, Object>> loginRows = loginLogRepository.findAll(Sort.by(Sort.Direction.DESC, "loginTime")).stream()
                .filter(x -> !hasText(operator) || contains(x.getUsername(), operator))
                .filter(x -> !hasText(keyword) || containsAny(keyword, x.getUsername(), x.getResult(), x.getIpAddress()))
                .filter(x -> from == null || (x.getLoginTime() != null && !x.getLoginTime().isBefore(from)))
                .filter(x -> to == null || (x.getLoginTime() != null && !x.getLoginTime().isAfter(to)))
                .map(x -> Map.<String, Object>of(
                        "category", "LOGIN",
                        "operator", nvl(x.getUsername()),
                        "type", nvl(x.getResult()),
                        "target", nvl(x.getIpAddress()),
                        "detail", "",
                        "time", x.getLoginTime() == null ? "" : x.getLoginTime().toString()
                )).toList();

        List<Map<String, Object>> permRows = operationLogRepository.findAll(Sort.by(Sort.Direction.DESC, "operationTime")).stream()
                .filter(x -> SECURITY_OPERATION_TYPES.contains(x.getOperationType()))
                .filter(x -> !hasText(operator) || contains(x.getOperator(), operator))
                .filter(x -> !hasText(keyword) || containsAny(keyword, x.getOperationType(), x.getOperationTarget(), x.getDetails()))
                .filter(x -> from == null || (x.getOperationTime() != null && !x.getOperationTime().isBefore(from)))
                .filter(x -> to == null || (x.getOperationTime() != null && !x.getOperationTime().isAfter(to)))
                .map(x -> Map.<String, Object>of(
                        "category", "PERMISSION",
                        "operator", nvl(x.getOperator()),
                        "type", nvl(x.getOperationType()),
                        "target", nvl(x.getOperationTarget()),
                        "detail", nvl(x.getDetails()),
                        "time", x.getOperationTime() == null ? "" : x.getOperationTime().toString()
                )).toList();

        List<Map<String, Object>> out = java.util.stream.Stream.concat(loginRows.stream(), permRows.stream())
                .sorted((a, b) -> String.valueOf(b.get("time")).compareTo(String.valueOf(a.get("time"))))
                .toList();
        return ApiResponse.ok(out);
    }

    @GetMapping("/login")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<List<LoginLog>> loginLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<LoginLog> all = loginLogRepository.findAll(Sort.by(Sort.Direction.DESC, "loginTime"));
        List<LoginLog> filtered = all.stream()
                .filter(x -> !hasText(username) || contains(x.getUsername(), username))
                .filter(x -> !hasText(result) || equalsIgnoreCase(x.getResult(), result))
                .filter(x -> !hasText(keyword) || containsAny(keyword, x.getUsername(), x.getResult(), x.getIpAddress()))
                .filter(x -> from == null || (x.getLoginTime() != null && !x.getLoginTime().isBefore(from)))
                .filter(x -> to == null || (x.getLoginTime() != null && !x.getLoginTime().isAfter(to)))
                .toList();
        return ApiResponse.ok(filtered);
    }

    @GetMapping("/data-change")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<List<DataChangeLog>> dataChangeLogs(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String changeType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<DataChangeLog> all = dataChangeLogRepository.findAll(Sort.by(Sort.Direction.DESC, "changeTime"));
        List<DataChangeLog> filtered = all.stream()
                .filter(x -> !hasText(operator) || contains(x.getOperator(), operator))
                .filter(x -> !hasText(changeType) || equalsIgnoreCase(x.getChangeType(), changeType))
                .filter(x -> !hasText(keyword) || containsAny(keyword, x.getTargetType(), x.getTargetId(), x.getDetail(), x.getOperator()))
                .filter(x -> from == null || (x.getChangeTime() != null && !x.getChangeTime().isBefore(from)))
                .filter(x -> to == null || (x.getChangeTime() != null && !x.getChangeTime().isAfter(to)))
                .toList();
        return ApiResponse.ok(filtered);
    }

    @GetMapping("/export/operation")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN','CONTENT_REVIEWER')")
    public ResponseEntity<byte[]> exportOperation(
            Authentication authentication,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<OperationLog> logs = list(authentication, operator, operationType, keyword, from, to).data();
        String csv = "id,operator,operationType,operationTarget,details,operationTime\n" +
                logs.stream().map(x -> csvRow(
                        x.getId(), x.getOperator(), x.getOperationType(), x.getOperationTarget(), x.getDetails(), x.getOperationTime()
                )).collect(Collectors.joining("\n"));
        return csvDownload("operation-logs.csv", csv);
    }

    @GetMapping("/export/system")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ResponseEntity<byte[]> exportSystem(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<SystemLog> logs = systemLogs(level, eventType, keyword, from, to).data();
        String csv = "id,level,eventType,source,message,logTime\n" +
                logs.stream().map(x -> csvRow(
                        x.getId(), x.getLevel(), x.getEventType(), x.getSource(), x.getMessage(), x.getLogTime()
                )).collect(Collectors.joining("\n"));
        return csvDownload("system-logs.csv", csv);
    }

    @GetMapping("/export/security")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ResponseEntity<byte[]> exportSecurity(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<Map<String, Object>> rows = securityLogs(operator, keyword, from, to).data();
        String csv = "category,operator,type,target,detail,time\n" +
                rows.stream().map(x -> csvRow(
                        x.get("category"), x.get("operator"), x.get("type"), x.get("target"), x.get("detail"), x.get("time")
                )).collect(Collectors.joining("\n"));
        return csvDownload("security-logs.csv", csv);
    }

    private boolean contains(String text, String keyword) {
        return text != null && text.toLowerCase().contains(keyword.toLowerCase());
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private boolean containsAny(String keyword, String... texts) {
        for (String t : texts) {
            if (contains(t, keyword)) return true;
        }
        return false;
    }

    private boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private List<OperationLog> filterOperationLogs(List<OperationLog> all,
                                                   String operator,
                                                   String operationType,
                                                   String keyword,
                                                   LocalDateTime from,
                                                   LocalDateTime to) {
        return all.stream()
                .filter(x -> !hasText(operator) || contains(x.getOperator(), operator))
                .filter(x -> !hasText(operationType) || equalsIgnoreCase(x.getOperationType(), operationType))
                .filter(x -> !hasText(keyword) || containsAny(keyword, x.getOperationType(), x.getOperationTarget(), x.getDetails(), x.getOperator()))
                .filter(x -> from == null || (x.getOperationTime() != null && !x.getOperationTime().isBefore(from)))
                .filter(x -> to == null || (x.getOperationTime() != null && !x.getOperationTime().isAfter(to)))
                .toList();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String csvRow(Object... cells) {
        return java.util.Arrays.stream(cells)
                .map(c -> {
                    String s = c == null ? "" : String.valueOf(c);
                    s = s.replace("\"", "\"\"");
                    return "\"" + s + "\"";
                }).collect(Collectors.joining(","));
    }

    private static ResponseEntity<byte[]> csvDownload(String fileName, String csv) {
        byte[] utf8 = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] payload = new byte[bom.length + utf8.length];
        System.arraycopy(bom, 0, payload, 0, bom.length);
        System.arraycopy(utf8, 0, payload, bom.length, utf8.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(payload);
    }

    private static boolean isSuperOrDataAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()) || "ROLE_DATA_ADMIN".equals(a.getAuthority()));
    }
}
