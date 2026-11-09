package com.vernu.sms.offline

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Uma operação SEND_SMS recebida do servidor local (plano 3.1).
 *
 * sendStatus:
 *  - PENDING: gravada, ainda não entregue ao SmsSendWorker
 *  - QUEUED: entregue ao SmsSendWorker, aguardando o resultado do rádio
 *  - SENT / FAILED: resultado final
 *
 * syncStatus:
 *  - PENDING: o servidor ainda não confirmou o resultado (RESULT_ACK)
 *  - SYNCED: o servidor confirmou
 */
@Entity(
    tableName = "operations",
    indices = [
        Index(value = ["sendStatus", "createdAt"]),
        Index(value = ["syncStatus", "createdAt"]),
        Index(value = ["smsId"]),
    ]
)
data class OperationEntity(
    // UUID gerado pelo servidor: chave de idempotência
    @PrimaryKey val id: String,
    // Frame SEND_SMS original, como chegou
    val payload: String,
    val recipient: String,
    val message: String,
    val smsId: String?,
    val smsBatchId: String?,
    val simSubscriptionId: Int?,
    val sendStatus: String = SEND_PENDING,
    val syncStatus: String = SYNC_PENDING,
    // Vezes que a operação foi entregue ao SmsSendWorker
    val attempts: Int = 0,
    val error: String? = null,
    // Momento em que o servidor criou a operação; define a ordem de envio
    val createdAt: Long,
    val receivedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    // Id usado no SmsSendWorker e nos relatórios de status
    val workSmsId: String get() = smsId?.takeIf { it.isNotBlank() } ?: id

    val hasResult: Boolean get() = sendStatus == SEND_SENT || sendStatus == SEND_FAILED

    companion object {
        const val SEND_PENDING = "PENDING"
        const val SEND_QUEUED = "QUEUED"
        const val SEND_SENT = "SENT"
        const val SEND_FAILED = "FAILED"

        const val SYNC_PENDING = "PENDING"
        const val SYNC_SYNCED = "SYNCED"
    }
}
