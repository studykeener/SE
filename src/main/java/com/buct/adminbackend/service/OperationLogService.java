package com.buct.adminbackend.service;

import com.buct.adminbackend.entity.OperationLog;
import com.buct.adminbackend.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;

    public void log(String operator, String type, String target, String details) {
        OperationLog log = new OperationLog();
        log.setOperator(operator);
        log.setOperationType(type);
        log.setOperationTarget(target);
        log.setDetails(details);
        operationLogRepository.save(log);
    }
}
