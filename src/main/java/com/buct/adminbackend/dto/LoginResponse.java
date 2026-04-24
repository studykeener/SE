package com.buct.adminbackend.dto;

public record LoginResponse(
        String token,
        AdminUserResponse adminUser,
        java.util.List<String> permissions
) {
}
