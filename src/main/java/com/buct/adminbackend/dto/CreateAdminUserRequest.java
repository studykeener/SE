package com.buct.adminbackend.dto;

import com.buct.adminbackend.enums.RoleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAdminUserRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotNull RoleType role
) {
}
