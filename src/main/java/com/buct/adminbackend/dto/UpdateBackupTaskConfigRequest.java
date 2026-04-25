package com.buct.adminbackend.dto;

public record UpdateBackupTaskConfigRequest(
        Boolean autoEnabled,
        String cronExpression,
        Integer retentionDays
) {
}

