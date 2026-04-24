package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateBehaviorRequest(
        @NotBlank String behaviorType,
        @NotBlank String behaviorDetail
) {
}
