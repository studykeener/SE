package com.buct.adminbackend.dto;

import com.buct.adminbackend.enums.ReviewStatus;
import jakarta.validation.constraints.NotNull;

public record ReviewActionRequest(
        @NotNull ReviewStatus reviewStatus,
        String rejectReason
) {
}
