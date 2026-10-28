package com.vernu.sms.database.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface LocalMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertMessage(LocalMessageEntity message);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIfAbsent(LocalMessageEntity message);

    @Query("SELECT * FROM local_messages WHERE status = 'PENDING' OR status = 'QUEUED' ORDER BY timestamp ASC")
    List<LocalMessageEntity> getPendingMessages();

    @Query("SELECT * FROM local_messages WHERE id = :messageId")
    LocalMessageEntity getMessageById(String messageId);

    @Query("SELECT COUNT(*) FROM local_messages WHERE status = 'PENDING' OR status = 'QUEUED'")
    int getPendingMessageCount();

    @Query("SELECT * FROM local_messages WHERE status = :status")
    List<LocalMessageEntity> getMessagesByStatus(String status);

    @Update
    void updateMessage(LocalMessageEntity message);

    @Query("UPDATE local_messages SET status = :status, sentAt = :sentAt WHERE id = :messageId")
    void updateMessageStatus(String messageId, String status, long sentAt);

    @Query("DELETE FROM local_messages WHERE id = :messageId")
    void deleteMessage(String messageId);
}