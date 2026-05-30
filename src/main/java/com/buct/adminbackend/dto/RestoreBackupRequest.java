package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RestoreBackupRequest(
        @NotNull Boolean acknowledged,
        @NotBlank String confirmText
) {
}

