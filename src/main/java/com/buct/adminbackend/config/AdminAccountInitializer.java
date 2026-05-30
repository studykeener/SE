package com.buct.adminbackend.config;

import com.buct.adminbackend.entity.AdminUser;
import com.buct.adminbackend.enums.UserStatus;
import com.buct.adminbackend.repository.AdminUserRepository;
import com.buct.adminbackend.service.RolePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    public static final String DEFAULT_ADMIN_USERNAME = "admin";
    public static final String DEFAULT_ADMIN_PASSWORD = "123456";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final RolePermissionService rolePermissionService;

    @Override
    public void run(ApplicationArguments args) {
        Long superAdminRoleId = rolePermissionService.getRoleIdByCode("SUPER_ADMIN");
        adminUserRepository.findByUsername(DEFAULT_ADMIN_USERNAME).ifPresentOrElse(admin -> {
            if (!superAdminRoleId.equals(admin.getRoleId())) {
                admin.setRoleId(superAdminRoleId);
                adminUserRepository.save(admin);
            }
        }, () -> {
            AdminUser admin = new AdminUser();
            admin.setUsername(DEFAULT_ADMIN_USERNAME);
            admin.setPasswordHash(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
            admin.setRoleId(superAdminRoleId);
            admin.setStatus(UserStatus.ENABLED);
            adminUserRepository.save(admin);
        });
    }
}
