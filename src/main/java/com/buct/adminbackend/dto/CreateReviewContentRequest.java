package com.buct.adminbackend.dto;

import com.buct.adminbackend.enums.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateReviewContentRequest(
        @NotNull ContentType contentType,
        @NotBlank String sourceSystem,
        String submitter,
        @NotBlank String contentText,
        String contentUrl,
        Long userId,
        Integer museumId,
        String objectId
) {
}
