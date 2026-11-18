package com.martinvidal.tv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * La factoria de Hilt permite que los Worker reciban repositorios inyectados.
 * Sin esto WorkManager crea los workers con un constructor por defecto y Hilt
 * no puede satisfacer sus dependencias.
 */
@HiltAndroidApp
class MartInvidalApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
