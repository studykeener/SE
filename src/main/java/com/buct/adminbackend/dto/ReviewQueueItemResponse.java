package com.buct.adminbackend.dto;

import com.buct.adminbackend.enums.ContentType;
import com.buct.adminbackend.enums.ReviewStatus;

import java.time.LocalDateTime;

public record ReviewQueueItemResponse(
        Long id,
        String sourceTable,
        ContentType contentType,
        String sourceSystem,
        String submitter,
        Long userId,
        Integer museumId,
        String objectId,
        String contentText,
        String contentUrl,
        ReviewStatus reviewStatus,
        Integer riskScore,
        LocalDateTime submitTime,
        LocalDateTime reviewTime,
        String reviewer,
        String rejectReason,
        Boolean autoReviewed,
        String autoDecisionNote
) {
}
