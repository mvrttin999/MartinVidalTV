package com.martinvidal.tv.ui.update

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.martinvidal.tv.data.repo.UpdateOutcome
import com.martinvidal.tv.data.repo.UpdateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Hilo de trabajo en segundo plano: comprueba el manifiesto y descarga el APK
 * cuando hay versión nueva. No muestra ninguna pantalla; al terminar publica
 * un aviso (UpdateNotifier) si quedó una actualización lista.
 */
@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: UpdateRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = repository.checkAndDownload(force = inputData.getBoolean(KEY_FORCE, false))
        val output = Data.Builder()
            .putString(KEY_CODE, result.code.name)
            .putString(KEY_VERSION, result.version)
            .putString(KEY_MESSAGE, result.message)
            .build()
        return when (result.code) {
            UpdateOutcome.Code.FAILED -> Result.failure(output)
            else -> Result.success(output)
        }
    }

    companion object {
        const val KEY_FORCE = "force"
        const val KEY_CODE = "code"
        const val KEY_VERSION = "version"
        const val KEY_MESSAGE = "message"
    }
}
