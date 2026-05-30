package com.buct.adminbackend.dto;

import com.buct.adminbackend.enums.UserStatus;
import jakarta.annotation.Nullable;

public record UpdateAdminRequest(
        @Nullable String role,
        @Nullable UserStatus status,
        /** 留空则不改密码 */
        @Nullable String newPassword
) {
}
