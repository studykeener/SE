package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotBlank;

public record ForwardRequest(
        @NotBlank String system,
        @NotBlank String subPath,
        String method,
        Object body
) {
}
