package com.vernu.sms.database.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {LocalMessageEntity.class}, version = 1, exportSchema = false)
public abstract class LocalMessageDatabase {
    public abstract LocalMessageDao messageDao();
}