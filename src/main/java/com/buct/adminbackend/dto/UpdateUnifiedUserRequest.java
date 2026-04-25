package com.buct.adminbackend.dto;

import com.buct.adminbackend.enums.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUnifiedUserRequest(
        @NotBlank @Size(max = 64) String username,
        @Size(max = 128) String displayName,
        @Size(max = 128) String email,
        @Size(max = 32) String phone,
        @NotBlank @Size(max = 32) String sourceSystem,
        @NotBlank @Size(max = 64) String sourceUserId,
        @NotNull UserStatus status,
        @NotNull Boolean commentAllowed,
        @NotNull Boolean uploadAllowed
) {
}

