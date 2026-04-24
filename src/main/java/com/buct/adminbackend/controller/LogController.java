package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.entity.DataChangeLog;
import com.buct.adminbackend.entity.LoginLog;
import com.buct.adminbackend.entity.OperationLog;
import com.buct.adminbackend.repository.DataChangeLogRepository;
import com.buct.adminbackend.repository.LoginLogRepository;
import com.buct.adminbackend.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class LogController {

    private final OperationLogRepository operationLogRepository;
    private final LoginLogRepository loginLogRepository;
    private final DataChangeLogRepository dataChangeLogRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<List<OperationLog>> list() {
        List<OperationLog> logs = operationLogRepository.findAll(Sort.by(Sort.Direction.DESC, "operationTime"));
        return ApiResponse.ok(logs);
    }

    @GetMapping("/login")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<List<LoginLog>> loginLogs() {
        return ApiResponse.ok(loginLogRepository.findAll(Sort.by(Sort.Direction.DESC, "loginTime")));
    }

    @GetMapping("/data-change")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DATA_ADMIN')")
    public ApiResponse<List<DataChangeLog>> dataChangeLogs(@RequestParam(required = false) String keyword) {
        List<DataChangeLog> all = dataChangeLogRepository.findAll(Sort.by(Sort.Direction.DESC, "changeTime"));
        if (keyword == null || keyword.isBlank()) {
            return ApiResponse.ok(all);
        }
        List<DataChangeLog> filtered = all.stream()
                .filter(x -> contains(x.getTargetType(), keyword) || contains(x.getDetail(), keyword) || contains(x.getOperator(), keyword))
                .toList();
        return ApiResponse.ok(filtered);
    }

    private boolean contains(String text, String keyword) {
        return text != null && text.toLowerCase().contains(keyword.toLowerCase());
    }
}
