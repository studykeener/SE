package com.buct.adminbackend.dto;

import com.buct.adminbackend.enums.ReviewStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReviewTargetActionRequest(
        @NotBlank String sourceTable,
        @NotNull Long id,
        @NotNull ReviewStatus reviewStatus,
        String rejectReason
) {
}
