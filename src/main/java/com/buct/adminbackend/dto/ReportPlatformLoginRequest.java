package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReportPlatformLoginRequest(
        @NotNull Long userId,
        @NotBlank String source,
        String ipAddress
) {
}
