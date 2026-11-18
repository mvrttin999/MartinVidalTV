package com.martinvidal.tv.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.martinvidal.tv.BuildConfig
import com.martinvidal.tv.R
import com.martinvidal.tv.core.BaseActivity
import com.martinvidal.tv.data.net.DeviceProfile
import com.martinvidal.tv.data.repo.PlaylistRepository
import com.martinvidal.tv.data.repo.UpdateRepository
import com.martinvidal.tv.data.session.SettingsStore
import com.martinvidal.tv.databinding.ActivityPortalBinding
import com.martinvidal.tv.ui.update.UpdateActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Configuracion manual de la fuente de contenido.
 *
 * Dos caminos independientes:
 *  - portal del distribuidor (host + `portalCode` + `appId`); y
 *  - lista M3U propia, que da canales reales sin portal ni codigo.
 *
 * No hay cuenta ni activacion: los valores se guardan en [SettingsStore] y el
 * interceptor los aplica en la siguiente peticion. Desde aqui se abre tambien
 * la pantalla de actualizaciones.
 */
@AndroidEntryPoint
class PortalActivity : BaseActivity() {

    private lateinit var binding: ActivityPortalBinding

    @Inject
    lateinit var session: SettingsStore

    @Inject
    lateinit var device: DeviceProfile

    @Inject
    lateinit var playlist: PlaylistRepository

    @Inject
    lateinit var updates: UpdateRepository

    private var previousUpdateUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPortalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cancel.setOnClickListener { finish() }
        binding.save.setOnClickListener { save() }
        binding.updates.setOnClickListener {
            startActivity(Intent(this, UpdateActivity::class.java))
        }
        binding.version.text = "v${BuildConfig.VERSION_NAME}"

        lifecycleScope.launch {
            val state = session.snapshot()
            binding.host.setText(state.host.ifBlank { DEFAULT_HOST })
            binding.portalCode.setText(state.portalCode)
            binding.appId.setText(state.appId.ifBlank { device.appId })
            binding.playlist.setText(state.playlistUrl)
            binding.xuperAuth.setText(state.xuperAuth)
            binding.updateUrl.setText(
                state.updateUrl.ifBlank { BuildConfig.UPDATE_URL }
            )
            previousUpdateUrl = state.updateUrl
            binding.host.requestFocus()
        }
    }

    private fun save() {
        // El campo arranca con el esquema como pista ("https://"); si el
        // usuario no escribe un host, se considera portal sin configurar.
        val rawHost = binding.host.text.toString().trim().let {
            if (it.equals("https://", true) || it.equals("http://", true)) "" else it
        }
        val portalCode = binding.portalCode.text.toString().trim()
        val appId = binding.appId.text.toString().trim().ifBlank { device.appId }
        val rawPlaylist = binding.playlist.text.toString().trim()
        val rawAuth = binding.xuperAuth.text.toString().trim()
        val rawUpdate = binding.updateUrl.text.toString().trim()

        val host = if (rawHost.isBlank()) null else normalizeUrl(rawHost, trailingSlash = true)
        if (rawHost.isNotBlank() && host == null) {
            showError(getString(R.string.portal_error_host))
            return
        }

        val playlistUrl = if (rawPlaylist.isBlank()) null else normalizeUrl(rawPlaylist, trailingSlash = false)
        if (rawPlaylist.isNotBlank() && playlistUrl == null) {
            showError(getString(R.string.portal_error_host))
            return
        }
        val updateUrl = if (rawUpdate.isBlank()) null else normalizeUrl(rawUpdate, trailingSlash = false)
        if (rawUpdate.isNotBlank() && updateUrl == null) {
            showError(getString(R.string.portal_error_host))
            return
        }

        binding.save.isEnabled = false
        lifecycleScope.launch {
            session.savePortal(
                host = host.orEmpty(),
                appId = appId,
                portalCode = portalCode
            )
            session.savePlaylist(playlistUrl.orEmpty())
            session.saveXuperAuth(rawAuth)
            session.saveUpdateUrl(updateUrl.orEmpty())
            // Si cambia la fuente de actualizaciones, descarta el APK anterior:
            // ya no sabemos si corresponde a la nueva version publicada.
            if (updateUrl.orEmpty() != previousUpdateUrl) updates.clearDownloaded()
            playlist.invalidate()
            finish()
        }
    }

    /**
     * Acepta `host`, `host:puerto` o URL completa; devuelve siempre una URL.
     * El host del portal termina en `/`; la lista M3U conserva su ruta tal cual.
     */
    private fun normalizeUrl(value: String, trailingSlash: Boolean): String? {
        if (value.isBlank()) return null
        val withScheme = when {
            value.startsWith("http://", ignoreCase = true) -> value
            value.startsWith("https://", ignoreCase = true) -> value
            else -> "https://$value"
        }
        val url = runCatching { java.net.URI(withScheme) }.getOrNull() ?: return null
        if (url.host.isNullOrBlank()) return null
        val trimmed = withScheme.trimEnd('/')
        return if (trailingSlash) "$trimmed/" else trimmed
    }

    private fun showError(message: String) {
        binding.error.text = message
        binding.error.visibility = View.VISIBLE
    }

    private companion object {
        const val DEFAULT_HOST = "https://"
    }
}
