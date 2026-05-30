package com.buct.adminbackend.dto;

import com.buct.adminbackend.enums.ReviewStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BatchReviewRequest(
        @NotEmpty List<@Valid ReviewTargetRef> targets,
        @NotNull ReviewStatus reviewStatus,
        String rejectReason
) {
}
