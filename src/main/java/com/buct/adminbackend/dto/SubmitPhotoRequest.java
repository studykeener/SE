package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitPhotoRequest(
        @NotNull Long userId,
        @NotBlank String photoUrl,
        String description,
        Integer museumId,
        String objectId,
        @NotBlank String source
) {
}
