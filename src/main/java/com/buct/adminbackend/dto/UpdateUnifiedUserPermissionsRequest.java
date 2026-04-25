package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUnifiedUserPermissionsRequest(
        @NotNull Boolean commentAllowed,
        @NotNull Boolean uploadAllowed,
        @Size(max = 256) String reason
) {
}

