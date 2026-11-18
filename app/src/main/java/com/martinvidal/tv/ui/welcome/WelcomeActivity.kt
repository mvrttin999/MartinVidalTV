package com.martinvidal.tv.ui.welcome

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.martinvidal.tv.BuildConfig
import com.martinvidal.tv.core.BaseActivity
import com.martinvidal.tv.data.net.DeviceProfile
import com.martinvidal.tv.data.repo.UpdateScheduler
import com.martinvidal.tv.data.session.SettingsStore
import com.martinvidal.tv.databinding.ActivityWelcomeBinding
import com.martinvidal.tv.ui.home.HomeActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pantalla de arranque.
 *
 * La app no tiene cuenta, sesion ni activacion: entra directo a la portada.
 * Muestra el catalogo de la lista M3U configurada (o la incluida), y si se
 * configuró un portal intenta su catalogo. El origen se elige en Ajustes.
 *
 * Aqui se programa tambien la comprobacion periodica de actualizaciones:
 * WorkManager la ejecuta en segundo plano aunque la app este cerrada.
 */
@AndroidEntryPoint
class WelcomeActivity : BaseActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    @Inject
    lateinit var session: SettingsStore

    @Inject
    lateinit var device: DeviceProfile

    @Inject
    lateinit var updates: UpdateScheduler

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* La descarga sigue adelante aunque el usuario lo rechace. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.version.text = "v${BuildConfig.VERSION_NAME}"

        updates.schedulePeriodic()
        requestNotificationPermissionIfNeeded()

        lifecycleScope.launch {
            val started = System.currentTimeMillis()
            session.ensureDeviceIds(device.sn, device.reserve1, device.sn)
            val minimumSplash = 900L
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < minimumSplash) delay(minimumSplash - elapsed)

            startActivity(Intent(this@WelcomeActivity, HomeActivity::class.java))
            finish()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
