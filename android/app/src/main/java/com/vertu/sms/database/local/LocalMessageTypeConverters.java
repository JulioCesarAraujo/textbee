package com.vernu.sms.database.local;

import androidx.room.TypeConverter;

public class LocalMessageTypeConverters {
    @TypeConverter
    public static MessageStatus fromString(String value) {
        if (value == null) return null;
        return MessageStatus.valueOf(value);
    }

    @TypeConverter
    public static String toString(MessageStatus status) {
        return status == null ? null : status.name();
    }
}

enum MessageStatus {
    PENDING,
    QUEUED,
    SENT,
    DELIVERED,
    FAILED,
    REJECTED
}