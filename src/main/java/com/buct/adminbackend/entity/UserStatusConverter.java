package com.buct.adminbackend.entity;

import com.buct.adminbackend.enums.UserStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class UserStatusConverter implements AttributeConverter<UserStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(UserStatus status) {
        if (status == null) {
            return 1;
        }
        return status == UserStatus.ENABLED ? 1 : 0;
    }

    @Override
    public UserStatus convertToEntityAttribute(Integer db) {
        if (db == null || db == 1) {
            return UserStatus.ENABLED;
        }
        return UserStatus.DISABLED;
    }
}
