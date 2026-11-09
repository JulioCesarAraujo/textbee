package com.vernu.sms.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [OperationEntity::class], version = 1, exportSchema = false)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun operationDao(): OperationDao

    companion object {
        @Volatile
        private var instance: OfflineDatabase? = null

        fun get(context: Context): OfflineDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OfflineDatabase::class.java,
                    "offline_operations.db"
                ).build().also { instance = it }
            }
    }
}
