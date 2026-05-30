package com.buct.adminbackend.dto;

import com.buct.adminbackend.enums.ReviewStatus;

import java.time.LocalDateTime;

/** 从共用业务表聚合的用户行为追溯项 */
public record UserActivityTraceItem(
        Long recordId,
        String sourceTable,
        String activityType,
        String summary,
        Integer museumId,
        String objectId,
        String source,
        ReviewStatus reviewStatus,
        LocalDateTime activityTime
) {
}
