package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ArtifactUpsertRequest(
        Integer museumId,
        @NotBlank @Size(max = 255) String objectId,
        @NotBlank @Size(max = 500) String name,
        @NotBlank @Size(max = 200) String period,
        @NotBlank @Size(max = 100) String type,
        String material,
        @NotBlank String description,
        @NotBlank String imageUrl,
        String museum,
        String location,
        String detailUrl,
        @NotBlank String sourceSystem,
        String sourceId
) {
}
