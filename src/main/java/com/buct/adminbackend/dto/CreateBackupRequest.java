package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateBackupRequest(
        @NotBlank String backupType, // FULL / TABLES
        List<String> tables
) {
}

