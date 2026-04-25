package com.buct.adminbackend.dto;

import com.buct.adminbackend.enums.RoleType;
import com.buct.adminbackend.enums.UserStatus;
import jakarta.annotation.Nullable;

public record UpdateAdminRequest(
        @Nullable RoleType role,
        @Nullable UserStatus status,
        /** 留空则不改密码 */
        @Nullable String newPassword
) {
}
