package com.buct.adminbackend.dto;

import com.buct.adminbackend.enums.UserSource;
import com.buct.adminbackend.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdatePlatformUserRequest(
        @NotBlank String username,
        @NotBlank @Email String email,
        @NotNull UserSource source,
        @NotNull UserStatus status,
        @NotNull Boolean commentAllowed,
        @NotNull Boolean uploadAllowed
) {
}
