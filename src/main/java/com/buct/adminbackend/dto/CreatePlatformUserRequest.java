package com.buct.adminbackend.dto;

import com.buct.adminbackend.enums.UserSource;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePlatformUserRequest(
        @NotBlank String username,
        @NotBlank @Email String email,
        @NotNull UserSource source
) {
}
