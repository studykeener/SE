package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotBlank;

public record RestoreBackupRequest(
        @NotBlank String confirmText
) {
}

