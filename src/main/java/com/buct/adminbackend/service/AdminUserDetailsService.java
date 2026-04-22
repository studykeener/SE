package com.buct.adminbackend.service;

import com.buct.adminbackend.entity.AdminUser;
import com.buct.adminbackend.enums.UserStatus;
import com.buct.adminbackend.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminUser adminUser = adminUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("管理员不存在"));
        if (adminUser.getStatus() == UserStatus.DISABLED) {
            throw new UsernameNotFoundException("管理员已被禁用");
        }
        return User.withUsername(adminUser.getUsername())
                .password(adminUser.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + adminUser.getRole().name())))
                .build();
    }
}
