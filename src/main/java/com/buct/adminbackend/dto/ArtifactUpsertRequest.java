package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ArtifactUpsertRequest(
        Integer museumId,
        String objectId,
        @NotBlank String name,
        String period,
        String type,
        String material,
        String description,
        String imageUrl,
        String museum,
        String location,
        String detailUrl,
        String sourceSystem,
        String sourceId,
        String kgSyncStatus
) {
}
