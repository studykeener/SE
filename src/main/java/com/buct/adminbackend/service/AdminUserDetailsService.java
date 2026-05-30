package com.buct.adminbackend.service;

import com.buct.adminbackend.entity.AdminUser;
import com.buct.adminbackend.enums.UserStatus;
import com.buct.adminbackend.repository.AdminUserRepository;
import com.buct.adminbackend.security.PermissionCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;
    private final RolePermissionService rolePermissionService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminUser adminUser = adminUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("管理员不存在"));
        if (adminUser.getStatus() == UserStatus.DISABLED) {
            throw new UsernameNotFoundException("管理员已被禁用");
        }
        String roleCode = rolePermissionService.getRoleCodeByAdminId(adminUser.getId());
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleCode));
        for (String permission : rolePermissionService.getPermissionCodesByAdminId(adminUser.getId())) {
            authorities.add(new SimpleGrantedAuthority(PermissionCodes.authority(permission)));
        }
        return User.withUsername(adminUser.getUsername())
                .password(adminUser.getPasswordHash())
                .authorities(new ArrayList<>(authorities))
                .build();
    }
}
