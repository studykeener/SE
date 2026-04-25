package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchUserIdsRequest(@NotEmpty List<Long> ids) {
}
