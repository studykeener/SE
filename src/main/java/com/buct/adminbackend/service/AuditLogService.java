package com.buct.adminbackend.service;

import com.buct.adminbackend.entity.DataChangeLog;
import com.buct.adminbackend.entity.LoginLog;
import com.buct.adminbackend.entity.User;
import com.buct.adminbackend.repository.DataChangeLogRepository;
import com.buct.adminbackend.repository.LoginLogRepository;
import com.buct.adminbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    public static final String USER_TYPE_ADMIN = "ADMIN";
    public static final String USER_TYPE_PLATFORM = "USER";
    public static final String SOURCE_ADMIN = "admin";
    public static final String SOURCE_WEB = "web";
    public static final String SOURCE_APP = "app";

    private final LoginLogRepository loginLogRepository;
    private final DataChangeLogRepository dataChangeLogRepository;
    private final UserRepository userRepository;

    public void logLogin(String username, String result, String ipAddress, String userType, Long userId) {
        logLogin(username, result, ipAddress, userType, userId, null);
    }

    public void logLogin(String username,
                         String result,
                         String ipAddress,
                         String userType,
                         Long userId,
                         String sourceSystem) {
        String resolvedType = userType == null ? USER_TYPE_ADMIN : userType;
        LoginLog log = new LoginLog();
        log.setUsername(username);
        log.setResult(result);
        log.setIpAddress(ipAddress);
        log.setUserType(resolvedType);
        log.setUserId(userId);
        log.setSourceSystem(resolveSourceSystem(resolvedType, sourceSystem));
        loginLogRepository.save(log);
    }

    @Transactional
    public void reportPlatformLogin(Long userId, String source, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        String normalizedSource = normalizePlatformSource(source);
        LocalDateTime now = LocalDateTime.now();
        user.setLastLoginAt(now);
        if (StringUtils.hasText(ipAddress)) {
            user.setLastLoginIp(ipAddress.trim());
        }
        userRepository.save(user);
        logLogin(user.getUsername(), "SUCCESS", ipAddress, USER_TYPE_PLATFORM, userId, normalizedSource);
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

    public static String resolveSourceSystem(String userType, String sourceSystem) {
        if (USER_TYPE_ADMIN.equalsIgnoreCase(userType)) {
            return SOURCE_ADMIN;
        }
        return normalizePlatformSource(sourceSystem);
    }

    public static String normalizePlatformSource(String source) {
        if (!StringUtils.hasText(source)) {
            throw new IllegalArgumentException("前台登录 source_system 不能为空，须为 web 或 app");
        }
        return SOURCE_APP.equalsIgnoreCase(source.trim()) ? SOURCE_APP : SOURCE_WEB;
    }
}
