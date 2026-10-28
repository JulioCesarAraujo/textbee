package com.vernu.sms.database.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

@Entity(tableName = "local_messages")
@TypeConverters({LocalMessageTypeConverters.class})
public class LocalMessageEntity {
    @PrimaryKey
    public String id;

    public String payload;
    public String sendStatus;
    public String syncStatus;
    public int attempts;
    public long createdAt;
    public Long sentAt;
    public Long receivedAt;

    public LocalMessageEntity() {}

    public LocalMessageEntity(String id, String payload, String sendStatus, String syncStatus, int attempts, long createdAt) {
        this.id = id;
        this.payload = payload;
        this.sendStatus = sendStatus;
        this.syncStatus = syncStatus;
        this.attempts = attempts;
        this.createdAt = createdAt;
    }
}