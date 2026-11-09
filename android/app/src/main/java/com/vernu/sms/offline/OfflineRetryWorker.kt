package com.vernu.sms.offline

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Nova tentativa de uma operação que falhou por falta de sinal na rede móvel
 * (plano 2.6). Persistido pelo WorkManager, então sobrevive a reinícios.
 */
class OfflineRetryWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val id = inputData.getString(KEY_OPERATION_ID) ?: return Result.failure()
        val op = OfflineDatabase.get(applicationContext).operationDao().getById(id) ?: return Result.success()
        if (op.sendStatus == OperationEntity.SEND_PENDING) {
            OfflineOperationProcessor.enqueue(applicationContext, op)
        }
        return Result.success()
    }

    companion object {
        private const val KEY_OPERATION_ID = "operation_id"

        private fun uniqueName(id: String) = "offline_retry_$id"

        fun schedule(context: Context, operationId: String, delayMs: Long) {
            val request = OneTimeWorkRequest.Builder(OfflineRetryWorker::class.java)
                .setInputData(Data.Builder().putString(KEY_OPERATION_ID, operationId).build())
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueName(operationId), ExistingWorkPolicy.REPLACE, request)
        }

        // Chamar fora da thread principal
        fun isScheduled(context: Context, operationId: String): Boolean = try {
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(uniqueName(operationId)).get()
                .any { !it.state.isFinished }
        } catch (e: Exception) {
            false
        }
    }
}
