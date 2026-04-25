package com.buct.adminbackend.service;

import com.buct.adminbackend.entity.SystemLog;
import com.buct.adminbackend.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemLogService {
    private final SystemLogRepository systemLogRepository;

    public void info(String eventType, String source, String message) {
        save("INFO", eventType, source, message, null);
    }

    public void warn(String eventType, String source, String message) {
        save("WARN", eventType, source, message, null);
    }

    public void error(String eventType, String source, String message, Throwable ex) {
        String stack = ex == null ? null : stackTrace(ex);
        save("ERROR", eventType, source, message, stack);
    }

    private void save(String level, String eventType, String source, String message, String stack) {
        SystemLog log = new SystemLog();
        log.setLevel(level);
        log.setEventType(eventType);
        log.setSource(source);
        log.setMessage(message);
        log.setStackTrace(stack);
        systemLogRepository.save(log);
    }

    private String stackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.toString()).append("\n");
        for (StackTraceElement e : t.getStackTrace()) {
            sb.append("  at ").append(e).append("\n");
            if (sb.length() > 3800) break;
        }
        return sb.toString();
    }
}

