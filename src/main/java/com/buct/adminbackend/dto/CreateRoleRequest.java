package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRoleRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description
) {
}
