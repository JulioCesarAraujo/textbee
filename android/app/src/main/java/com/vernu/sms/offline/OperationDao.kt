package com.vernu.sms.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// Acesso à fila local (plano 3.2). Chamado fora da thread principal.
@Dao
interface OperationDao {

    // Retorna -1 quando o UUID já existe: a operação não é reprocessada (3.3)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIfAbsent(operation: OperationEntity): Long

    @Query("SELECT * FROM operations WHERE id = :id")
    fun getById(id: String): OperationEntity?

    // A mais recente com esse smsId (o SmsSendWorker e o recibo de status só conhecem o smsId)
    @Query("SELECT * FROM operations WHERE smsId = :smsId OR id = :smsId ORDER BY createdAt DESC LIMIT 1")
    fun findBySmsId(smsId: String): OperationEntity?

    // Ainda sem resultado, na ordem de criação
    @Query("SELECT * FROM operations WHERE sendStatus IN ('PENDING', 'QUEUED') ORDER BY createdAt ASC, receivedAt ASC")
    fun getPending(): List<OperationEntity>

    // Com resultado que o servidor ainda não confirmou, na ordem de criação
    @Query("SELECT * FROM operations WHERE sendStatus IN ('SENT', 'FAILED') AND syncStatus = 'PENDING' ORDER BY createdAt ASC, receivedAt ASC")
    fun getUnsyncedResults(): List<OperationEntity>

    @Query("UPDATE operations SET sendStatus = 'QUEUED', attempts = attempts + 1, updatedAt = :now WHERE id = :id AND sendStatus IN ('PENDING', 'QUEUED')")
    fun markQueued(id: String, now: Long = System.currentTimeMillis()): Int

    // Volta para PENDING (ex.: sem sinal, nova tentativa agendada)
    @Query("UPDATE operations SET sendStatus = 'PENDING', error = :error, updatedAt = :now WHERE id = :id AND sendStatus = 'QUEUED'")
    fun markRetrying(id: String, error: String?, now: Long = System.currentTimeMillis()): Int

    // Resultado final do envio; o servidor ainda precisa confirmá-lo
    @Query("UPDATE operations SET sendStatus = :status, error = :error, syncStatus = 'PENDING', updatedAt = :now WHERE id = :id")
    fun markSent(id: String, status: String, error: String?, now: Long = System.currentTimeMillis()): Int

    // Só uma operação já com resultado pode ser dada como sincronizada
    @Query("UPDATE operations SET syncStatus = 'SYNCED', updatedAt = :now WHERE id = :id AND sendStatus IN ('SENT', 'FAILED')")
    fun markSynced(id: String, now: Long = System.currentTimeMillis()): Int

    // Limpeza: operações concluídas e confirmadas há mais de :before
    @Query("DELETE FROM operations WHERE syncStatus = 'SYNCED' AND updatedAt < :before")
    fun deleteSyncedBefore(before: Long): Int
}
