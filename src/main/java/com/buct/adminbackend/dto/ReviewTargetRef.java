package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReviewTargetRef(
        @NotBlank String sourceTable,
        @NotNull Long id
) {
}
