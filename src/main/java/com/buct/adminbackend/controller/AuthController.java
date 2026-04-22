package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.AdminUserResponse;
import com.buct.adminbackend.dto.CreateAdminUserRequest;
import com.buct.adminbackend.dto.LoginRequest;
import com.buct.adminbackend.dto.LoginResponse;
import com.buct.adminbackend.entity.AdminUser;
import com.buct.adminbackend.enums.RoleType;
import com.buct.adminbackend.enums.UserStatus;
import com.buct.adminbackend.repository.AdminUserRepository;
import com.buct.adminbackend.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    @PostMapping("/bootstrap")
    public ApiResponse<AdminUserResponse> bootstrap(@Valid @RequestBody CreateAdminUserRequest request) {
        if (adminUserRepository.count() > 0) {
            throw new IllegalArgumentException("系统已初始化，禁止重复创建超级管理员");
        }
        AdminUser adminUser = new AdminUser();
        adminUser.setUsername(request.username());
        adminUser.setPassword(passwordEncoder.encode(request.password()));
        adminUser.setRole(RoleType.SUPER_ADMIN);
        adminUser.setStatus(UserStatus.ENABLED);
        AdminUser saved = adminUserRepository.save(adminUser);
        return ApiResponse.ok("初始化成功", toResponse(saved));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        String token = jwtService.generateToken(userDetails);
        AdminUser adminUser = adminUserRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("管理员不存在"));
        LoginResponse response = new LoginResponse(token, toResponse(adminUser));
        return ApiResponse.ok("登录成功", response);
    }

    @GetMapping("/me")
    public ApiResponse<AdminUserResponse> me(Authentication authentication) {
        AdminUser adminUser = adminUserRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("管理员不存在"));
        return ApiResponse.ok(toResponse(adminUser));
    }

    private AdminUserResponse toResponse(AdminUser adminUser) {
        return new AdminUserResponse(
                adminUser.getId(),
                adminUser.getUsername(),
                adminUser.getRole(),
                adminUser.getStatus(),
                adminUser.getCreatedAt()
        );
    }
}
