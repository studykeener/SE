package com.buct.adminbackend.dto;

import java.util.List;

public record MeResponse(
        AdminUserResponse adminUser,
        List<String> permissions
) {
}
