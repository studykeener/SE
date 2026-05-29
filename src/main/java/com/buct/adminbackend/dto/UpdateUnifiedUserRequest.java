package com.buct.adminbackend.dto;

import com.buct.adminbackend.enums.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUnifiedUserRequest(
        @NotBlank @Size(max = 50) String username,
        @Size(max = 50) String displayName,
        @Size(max = 100) String email,
        @Size(max = 20) String phone,
        @Size(max = 500) String avatarUrl,
        Byte sex,
        @NotBlank @Size(max = 20) String sourceSystem,
        @NotNull UserStatus status,
        @NotNull Boolean commentAllowed,
        @NotNull Boolean uploadAllowed,
        @Size(max = 255) String disabledReason
) {
}
