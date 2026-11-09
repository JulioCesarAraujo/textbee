package com.vernu.sms.offline

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Protocolo do WebSocket do modo offline (plano 1.3), igual a
 * api/src/gateway/offline/protocol.ts:
 *
 * servidor -> app  { type: "SEND_SMS", id, to, message, smsId?, smsBatchId?, simSubscriptionId?, createdAt }
 * app -> servidor  { type: "ACK", id, status: "received" | "duplicate" }
 * app -> servidor  { type: "RESULT", id, status: "SENT" | "FAILED", error? }
 * servidor -> app  { type: "RESULT_ACK", id }
 */
object OfflineProtocol {
    const val TYPE_SEND_SMS = "SEND_SMS"
    const val TYPE_ACK = "ACK"
    const val TYPE_RESULT = "RESULT"
    const val TYPE_RESULT_ACK = "RESULT_ACK"

    const val ACK_RECEIVED = "received"
    const val ACK_DUPLICATE = "duplicate"

    private val UUID_PATTERN =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    data class SendSms(
        val id: String,
        val to: String?,
        val message: String?,
        val smsId: String?,
        val smsBatchId: String?,
        val simSubscriptionId: Int?,
        val createdAt: Long,
        val raw: String,
    ) {
        val isValid: Boolean get() = !to.isNullOrBlank() && message != null
    }

    sealed interface Frame {
        data class SendSmsFrame(val value: SendSms) : Frame
        data class ResultAck(val id: String) : Frame
    }

    fun isUuid(value: String?): Boolean = value != null && UUID_PATTERN.matches(value)

    // null para JSON inválido, id que não é UUID ou tipo desconhecido
    fun parse(text: String): Frame? {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val id = json.optString("id", "")
        if (!isUuid(id)) return null
        return when (json.optString("type")) {
            TYPE_SEND_SMS -> Frame.SendSmsFrame(
                SendSms(
                    id = id,
                    to = json.optStringOrNull("to"),
                    message = json.optStringOrNull("message"),
                    smsId = json.optStringOrNull("smsId"),
                    smsBatchId = json.optStringOrNull("smsBatchId"),
                    simSubscriptionId = if (json.has("simSubscriptionId") && !json.isNull("simSubscriptionId"))
                        json.optInt("simSubscriptionId") else null,
                    createdAt = parseIsoDate(json.optStringOrNull("createdAt")) ?: System.currentTimeMillis(),
                    raw = text,
                )
            )
            TYPE_RESULT_ACK -> Frame.ResultAck(id)
            else -> null
        }
    }

    fun ack(id: String, status: String): String =
        JSONObject().put("type", TYPE_ACK).put("id", id).put("status", status).toString()

    fun result(id: String, status: String, error: String?): String {
        val json = JSONObject().put("type", TYPE_RESULT).put("id", id).put("status", status)
        if (!error.isNullOrBlank()) json.put("error", error.take(500))
        return json.toString()
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

    // "2026-11-09T16:01:33.123Z", formato do Date.toISOString() do backend
    private fun parseIsoDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX")) {
            val format = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            runCatching { format.parse(value) }.getOrNull()?.let { return it.time }
        }
        return null
    }
}
