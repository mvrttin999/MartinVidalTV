package com.martinvidal.tv.data.repo

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.martinvidal.tv.ui.update.UpdateWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Programa la comprobación de actualizaciones en segundo plano. */
@Singleton
class UpdateScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val PERIODIC = "update_periodic"
        const val NOW = "update_now"
        private const val INITIAL_DELAY_MIN = 10L
        private const val INTERVAL_HOURS = 6L
        private const val FLEX_HOURS = 3L
    }

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Se llama al arrancar la app; KEEP no reinicia el contador cada vez. */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(
            INTERVAL_HOURS, TimeUnit.HOURS,
            FLEX_HOURS, TimeUnit.HOURS
        )
            .setInitialDelay(INITIAL_DELAY_MIN, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    /** Comprobación inmediata, por ejemplo al pulsar "Buscar ahora". */
    fun checkNow() {
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setInputData(Data.Builder().putBoolean(UpdateWorker.KEY_FORCE, true).build())
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }
}
