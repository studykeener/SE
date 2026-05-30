package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAdminUserRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String role
) {
}
