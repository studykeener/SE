package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotBlank;

public record ArtifactUpsertRequest(
        @NotBlank String name,
        String period,
        String type,
        String material,
        String description,
        String imageUrl,
        String sourceSystem,
        String sourceId,
        String kgSyncStatus
) {
}
