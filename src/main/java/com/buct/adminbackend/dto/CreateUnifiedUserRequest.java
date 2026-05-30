package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUnifiedUserRequest(
        @NotBlank @Size(max = 50) String username,
        @Size(max = 50) String displayName,
        @Size(max = 100) String email,
        @Size(max = 20) String phone,
        @Size(max = 500) String avatarUrl,
        Byte sex,
        @NotBlank @Size(max = 20) String sourceSystem,
        @Size(min = 6, max = 64) String password
) {
}
