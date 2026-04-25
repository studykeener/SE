package com.buct.adminbackend.config;

import com.buct.adminbackend.entity.AdminUser;
import com.buct.adminbackend.enums.RoleType;
import com.buct.adminbackend.enums.UserStatus;
import com.buct.adminbackend.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 默认超级管理员：账号 admin / 密码 123456。若不存在则创建；不可在业务中删除（由控制器校验）。
 */
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    public static final String DEFAULT_ADMIN_USERNAME = "admin";
    public static final String DEFAULT_ADMIN_PASSWORD = "123456";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (adminUserRepository.findByUsername(DEFAULT_ADMIN_USERNAME).isEmpty()) {
            AdminUser admin = new AdminUser();
            admin.setUsername(DEFAULT_ADMIN_USERNAME);
            admin.setPassword(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
            admin.setRole(RoleType.SUPER_ADMIN);
            admin.setStatus(UserStatus.ENABLED);
            adminUserRepository.save(admin);
        }
    }
}
