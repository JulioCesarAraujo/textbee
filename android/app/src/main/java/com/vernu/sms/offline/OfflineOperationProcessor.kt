package com.vernu.sms.offline

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.vernu.sms.database.SmsDedupeStore
import com.vernu.sms.helpers.DeviceLog
import com.vernu.sms.workers.SmsSendWorker
import java.util.concurrent.Executors

/**
 * Processa as operações do modo offline (plano 2.4, 2.6, 3.3 e 3.4).
 *
 * Fluxo de um SEND_SMS:
 *  1. grava no Room (insertIfAbsent);
 *  2. responde ACK;
 *  3. só então entrega ao SmsSendWorker, que mantém a fila, o espaçamento e o
 *     retry com backoff já existentes no app.
 *
 * O resultado do rádio volta por recordSendResult(), vira RESULT para o
 * servidor e fica pendente no Room até o RESULT_ACK. Sem conexão com o
 * servidor, os resultados esperam na fila local e saem na reconexão.
 *
 * Todo acesso ao Room acontece fora da thread principal: ou no executor
 * daqui, ou em workers.
 */
object OfflineOperationProcessor {
    private const val TAG = "OfflineOperations"

    // Falhas por falta de sinal são tentadas de novo antes de virar FAILED
    private const val MAX_NO_SIGNAL_ATTEMPTS = 5
    private const val NO_SIGNAL_BASE_DELAY_MS = 30_000L
    private const val NO_SIGNAL_MAX_DELAY_MS = 15 * 60_000L

    // Uma operação QUEUED há mais tempo que isso é conferida (ex.: app morto no meio do envio)
    private const val STALE_QUEUED_MS = 10 * 60_000L
    private const val KEEP_SYNCED_MS = 7 * 24 * 60 * 60_000L

    private val NO_SIGNAL_CODES = setOf(
        SmsManager.RESULT_ERROR_NO_SERVICE,
        SmsManager.RESULT_ERROR_RADIO_OFF,
        SmsManager.RESULT_NETWORK_ERROR,
    )

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "OfflineOperations").apply { isDaemon = true }
    }
    private val lock = Any()

    // Canal ativo, definido pelo LocalChannelService
    @Volatile
    private var channel: MessageChannel? = null

    fun attach(channel: MessageChannel) {
        this.channel = channel
    }

    fun detach(channel: MessageChannel) {
        if (this.channel === channel) this.channel = null
    }

    fun execute(task: () -> Unit) {
        executor.execute {
            try {
                task()
            } catch (e: Exception) {
                Log.e(TAG, "Offline operation task failed", e)
            }
        }
    }

    // Conectado: reenvia resultados não confirmados e retoma o que ficou parado
    fun onConnected(context: Context) = execute {
        resendUnsyncedResults(context)
        sweep(context)
        dao(context).deleteSyncedBefore(System.currentTimeMillis() - KEEP_SYNCED_MS)
    }

    fun onFrame(context: Context, text: String) = execute {
        when (val frame = OfflineProtocol.parse(text)) {
            is OfflineProtocol.Frame.SendSmsFrame -> handleSendSms(context, frame.value)
            is OfflineProtocol.Frame.ResultAck -> {
                if (dao(context).markSynced(frame.id) > 0) Log.d(TAG, "Result ${frame.id} confirmed by the server")
            }
            null -> Log.w(TAG, "Ignoring unknown frame: ${text.take(200)}")
        }
    }

    /**
     * Resultado do envio de um SMS. Retorna true quando o smsId pertence a uma
     * operação do modo offline: nesse caso o status vai ao servidor pelo
     * RESULT do WebSocket e não pelo relatório HTTP. Chamar fora da thread principal.
     */
    fun recordSendResult(
        context: Context,
        smsId: String?,
        sent: Boolean,
        error: String?,
        resultCode: Int?,
    ): Boolean = synchronized(lock) {
        if (smsId.isNullOrBlank()) return false
        val dao = dao(context)
        val op = dao.findBySmsId(smsId) ?: return false
        val status = if (sent) OperationEntity.SEND_SENT else OperationEntity.SEND_FAILED

        // Mensagens longas geram um aviso por parte: um FAILED não é desfeito por um SENT
        if (op.hasResult && (op.sendStatus == status || op.sendStatus == OperationEntity.SEND_FAILED)) return true

        // Sem sinal na rede móvel (plano 2.6): a operação espera e é tentada de novo
        if (!sent && resultCode != null && resultCode in NO_SIGNAL_CODES &&
            op.attempts < MAX_NO_SIGNAL_ATTEMPTS && op.sendStatus == OperationEntity.SEND_QUEUED
        ) {
            dao.markRetrying(op.id, error)
            val delay = (NO_SIGNAL_BASE_DELAY_MS shl (op.attempts - 1).coerceIn(0, 10))
                .coerceAtMost(NO_SIGNAL_MAX_DELAY_MS)
            OfflineRetryWorker.schedule(context, op.id, delay)
            DeviceLog.log(context, "offline_retry_scheduled", "no mobile signal, retry in ${delay / 1000}s", op.workSmsId)
            return true
        }

        dao.markSent(op.id, status, if (sent) null else error)
        sendResult(op.id, status, if (sent) null else error)
        true
    }

    // Entrega ao SmsSendWorker uma operação que ainda não tem resultado
    fun enqueue(context: Context, op: OperationEntity) = synchronized(lock) {
        val dao = dao(context)
        // Já enviada antes (ex.: chegou também por push): devolve o resultado sem reenviar
        if (SmsDedupeStore.get(context).wasSent(op.workSmsId, op.recipient)) {
            dao.markSent(op.id, OperationEntity.SEND_SENT, null)
            sendResult(op.id, OperationEntity.SEND_SENT, null)
            return
        }
        dao.markQueued(op.id)
        SmsSendWorker.enqueue(
            context, op.recipient, op.message, op.workSmsId, op.smsBatchId,
            op.simSubscriptionId, System.currentTimeMillis()
        )
    }

    // Retoma operações paradas, na ordem de criação (plano 3.4 e 3.5)
    fun sweep(context: Context) {
        val now = System.currentTimeMillis()
        for (op in dao(context).getPending()) {
            when {
                op.sendStatus == OperationEntity.SEND_PENDING && !OfflineRetryWorker.isScheduled(context, op.id) ->
                    enqueue(context, op)
                op.sendStatus == OperationEntity.SEND_QUEUED && now - op.updatedAt > STALE_QUEUED_MS ->
                    // O SmsSendWorker não envia de novo o que já foi ao rádio (SmsDedupeStore)
                    enqueue(context, op)
            }
        }
    }

    private fun handleSendSms(context: Context, frame: OfflineProtocol.SendSms) {
        val dao = dao(context)
        if (!frame.isValid) {
            Log.w(TAG, "SEND_SMS ${frame.id} has no recipient or message")
            sendAck(frame.id, OfflineProtocol.ACK_RECEIVED)
            sendResult(frame.id, OperationEntity.SEND_FAILED, "Invalid SEND_SMS payload")
            return
        }

        val entity = OperationEntity(
            id = frame.id,
            payload = frame.raw,
            recipient = frame.to!!,
            message = frame.message!!,
            smsId = frame.smsId,
            smsBatchId = frame.smsBatchId,
            simSubscriptionId = frame.simSubscriptionId,
            createdAt = frame.createdAt,
        )

        // 1. grava
        val inserted = synchronized(lock) { dao.insertIfAbsent(entity) != -1L }
        if (!inserted) {
            // UUID repetido (plano 3.3): não reprocessa, devolve o resultado anterior
            val existing = dao.getById(frame.id) ?: return
            sendAck(frame.id, OfflineProtocol.ACK_DUPLICATE)
            when {
                existing.hasResult -> sendResult(existing.id, existing.sendStatus, existing.error)
                existing.sendStatus == OperationEntity.SEND_PENDING &&
                    !OfflineRetryWorker.isScheduled(context, existing.id) -> enqueue(context, existing)
            }
            return
        }

        // 2. ACK  3. fila de envio
        sendAck(frame.id, OfflineProtocol.ACK_RECEIVED)
        DeviceLog.log(context, "offline_received", "to ${entity.recipient}", entity.workSmsId)
        enqueue(context, entity)
    }

    private fun resendUnsyncedResults(context: Context) {
        for (op in dao(context).getUnsyncedResults()) {
            if (!sendResult(op.id, op.sendStatus, op.error)) break
        }
    }

    private fun sendAck(id: String, status: String) {
        channel?.send(OfflineProtocol.ack(id, status))
    }

    // false quando não há conexão: o resultado espera no Room e sai na reconexão
    private fun sendResult(id: String, status: String, error: String?): Boolean =
        channel?.send(OfflineProtocol.result(id, status, error)) == true

    private fun dao(context: Context) = OfflineDatabase.get(context).operationDao()
}
