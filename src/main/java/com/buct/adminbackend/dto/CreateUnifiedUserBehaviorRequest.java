package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateUnifiedUserBehaviorRequest(
        @NotBlank @Size(max = 32) String behaviorType,
        @Size(max = 2000) String behaviorContent,
        @NotBlank @Size(max = 32) String sourceSystem,
        @Size(max = 64) String sourceRecordId,
        LocalDateTime behaviorTime
) {
}

