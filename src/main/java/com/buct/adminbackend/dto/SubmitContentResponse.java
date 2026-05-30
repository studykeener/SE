package com.buct.adminbackend.dto;

import com.buct.adminbackend.enums.ReviewStatus;

public record SubmitContentResponse(
        Long id,
        String sourceTable,
        ReviewStatus reviewStatus,
        Integer riskScore,
        String sensitiveWordsHit,
        boolean displayable,
        boolean pendingManualReview,
        String message
) {
}
