package com.buct.adminbackend.dto;

import com.buct.adminbackend.enums.ContentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateReviewContentRequest(
        @NotNull ContentType contentType,
        @NotBlank String sourceSystem,
        @NotBlank String submitter,
        @NotBlank String contentText,
        String contentUrl,
        @Min(0) @Max(100) Integer riskScore
) {
}
