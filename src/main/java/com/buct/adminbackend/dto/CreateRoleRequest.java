package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateRoleRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        List<Long> permissionIds
) {
}
