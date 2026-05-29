package com.buct.adminbackend.dto;

import com.buct.adminbackend.entity.User;
import com.buct.adminbackend.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record PlatformUserResponse(
        Long id,
        String username,
        @JsonProperty("displayName") String displayName,
        String email,
        String phone,
        String avatarUrl,
        Byte sex,
        @JsonProperty("sourceSystem") String sourceSystem,
        @JsonProperty("createdAt") LocalDateTime createdAt,
        LocalDateTime lastLoginAt,
        String lastLoginIp,
        UserStatus status,
        String disabledReason,
        LocalDateTime disabledAt,
        @JsonProperty("commentAllowed") Boolean commentAllowed,
        @JsonProperty("uploadAllowed") Boolean uploadAllowed
) {
    public static PlatformUserResponse from(User user) {
        if (user == null) {
            return null;
        }
        return new PlatformUserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getSex(),
                user.getUserSource(),
                user.getRegisterTime(),
                user.getLastLoginAt(),
                user.getLastLoginIp(),
                user.getStatus(),
                user.getDisabledReason(),
                user.getDisabledAt(),
                user.getCanComment(),
                user.getCanUpload()
        );
    }
}
