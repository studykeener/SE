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

    public void logChange(String operator,
                          Long operatorId,
                          String module,
                          String operationType,
                          String operationTarget,
                          String targetId,
                          String beforeData,
                          String afterData,
                          String details) {
        OperationLog log = new OperationLog();
        log.setOperator(operator);
        log.setOperatorId(operatorId);
        log.setModule(module);
        log.setOperationType(operationType);
        log.setOperationTarget(operationTarget);
        log.setTargetId(targetId);
        log.setBeforeData(beforeData);
        log.setAfterData(afterData);
        log.setDetails(details);
        operationLogRepository.save(log);
    }
}
