package com.vernu.sms.database;

import android.content.Context;

import androidx.room.Room;

import com.vernu.sms.database.local.LocalMessageDatabase;

public class DatabaseClient {
    private static DatabaseClient instance;
    private final Context context;
    private LocalMessageDatabase localMessageDatabase;

    private DatabaseClient(Context context) {
        this.context = context;
        localMessageDatabase = Room.databaseBuilder(
                context,
                LocalMessageDatabase.class,
                "local_message_db"
        ).build();
    }

    public static synchronized DatabaseClient getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseClient(context.getApplicationContext());
        }
        return instance;
    }

    public LocalMessageDatabase getLocalMessageDatabase() {
        return localMessageDatabase;
    }
}