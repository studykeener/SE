package com.buct.adminbackend.entity;

import com.buct.adminbackend.enums.ReviewStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class CommentAuditStatusConverter implements AttributeConverter<ReviewStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ReviewStatus status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case APPROVED -> 1;
            case REJECTED -> 2;
            case RECHECK -> 3;
            default -> 0;
        };
    }

    @Override
    public ReviewStatus convertToEntityAttribute(Integer db) {
        if (db == null) {
            return ReviewStatus.PENDING;
        }
        if (db == 1) {
            return ReviewStatus.APPROVED;
        }
        if (db == 2) {
            return ReviewStatus.REJECTED;
        }
        if (db == 3) {
            return ReviewStatus.RECHECK;
        }
        return ReviewStatus.PENDING;
    }
}
