package com.buct.adminbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitCommentRequest(
        @NotNull Long userId,
        @NotNull Integer museumId,
        @NotBlank String objectId,
        @NotBlank String content,
        @NotBlank String source
) {
}
