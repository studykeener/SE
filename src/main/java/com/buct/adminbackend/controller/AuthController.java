package com.buct.adminbackend.controller;

import com.buct.adminbackend.common.ApiResponse;
import com.buct.adminbackend.dto.AdminUserResponse;
import com.buct.adminbackend.dto.LoginRequest;
import com.buct.adminbackend.dto.LoginResponse;
import com.buct.adminbackend.entity.AdminUser;
import com.buct.adminbackend.repository.AdminUserRepository;
import com.buct.adminbackend.security.JwtService;
import com.buct.adminbackend.service.AuditLogService;
import com.buct.adminbackend.service.RolePermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AdminUserRepository adminUserRepository;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final RolePermissionService rolePermissionService;
    private final AuditLogService auditLogService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (BadCredentialsException e) {
            auditLogService.logLogin(request.username(), "FAILED", httpRequest.getRemoteAddr());
            throw new IllegalArgumentException("用户名或密码错误");
        }
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        String token = jwtService.generateToken(userDetails);
        AdminUser adminUser = adminUserRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("管理员不存在"));
        LoginResponse response = new LoginResponse(
                token,
                toResponse(adminUser),
                rolePermissionService.getPermissionCodesByAdminId(adminUser.getId())
        );
        auditLogService.logLogin(request.username(), "SUCCESS", httpRequest.getRemoteAddr());
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
