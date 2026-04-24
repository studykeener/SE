package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AssignIdsRequest(
        @NotEmpty List<Long> ids
) {
}
