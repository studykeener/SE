package com.buct.adminbackend.dto;

import com.buct.adminbackend.enums.RoleType;
import com.buct.adminbackend.enums.UserStatus;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String username,
        RoleType role,
        UserStatus status,
        LocalDateTime createdAt
) {
}
