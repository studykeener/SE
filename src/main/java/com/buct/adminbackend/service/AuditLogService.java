package com.buct.adminbackend.service;

import com.buct.adminbackend.entity.DataChangeLog;
import com.buct.adminbackend.entity.LoginLog;
import com.buct.adminbackend.repository.DataChangeLogRepository;
import com.buct.adminbackend.repository.LoginLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final LoginLogRepository loginLogRepository;
    private final DataChangeLogRepository dataChangeLogRepository;

    public void logLogin(String username, String result, String ipAddress) {
        LoginLog log = new LoginLog();
        log.setUsername(username);
        log.setResult(result);
        log.setIpAddress(ipAddress);
        loginLogRepository.save(log);
    }

    public void logDataChange(String operator, String changeType, String targetType, String targetId, String detail) {
        DataChangeLog log = new DataChangeLog();
        log.setOperator(operator);
        log.setChangeType(changeType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        dataChangeLogRepository.save(log);
    }
}
