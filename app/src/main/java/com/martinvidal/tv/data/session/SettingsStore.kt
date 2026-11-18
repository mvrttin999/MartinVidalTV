package com.martinvidal.tv.data.session

import android.content.Context
import com.martinvidal.tv.BuildConfig
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.portalDataStore by preferencesDataStore(name = "portal")

/**
 * Ajustes locales de la app e identidad del equipo.
 *
 * No hay cuenta, usuario, token ni activación: la fuente de contenido se
 * elige aquí (portal o lista M3U) y el equipo solo guarda identificadores
 * estables para construir las peticiones al portal del distribuidor.
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val APP_ID = stringPreferencesKey("app_id")
        val PORTAL_CODE = stringPreferencesKey("portal_code")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_TOKEN = stringPreferencesKey("device_token")
        val SN = stringPreferencesKey("sn")
        val RESERVE1 = stringPreferencesKey("reserve1")
        val B29 = stringPreferencesKey("b29")
        val PLAYLIST_URL = stringPreferencesKey("playlist_url")
        val XUPER_AUTH = stringPreferencesKey("xuper_auth")
        val UPDATE_URL = stringPreferencesKey("update_url")
        val UPDATE_VERSION = stringPreferencesKey("update_version")
        val UPDATE_NOTES = stringPreferencesKey("update_notes")
        val UPDATE_SHA = stringPreferencesKey("update_sha")
        val UPDATE_CHECK = stringPreferencesKey("update_check")
    }

    /** Si no hay URL guardada, se usa la fijada al compilar (-PplaylistUrl). */
    private fun effectivePlaylist(stored: String): String =
        stored.ifBlank { BuildConfig.PLAYLIST_URL }

    val host: Flow<String> = context.portalDataStore.data.map { it[Keys.HOST].orEmpty() }
    val appId: Flow<String> = context.portalDataStore.data.map { it[Keys.APP_ID].orEmpty() }
    val portalCode: Flow<String> = context.portalDataStore.data.map { it[Keys.PORTAL_CODE].orEmpty() }
    val playlistUrl: Flow<String> = context.portalDataStore.data.map {
        effectivePlaylist(it[Keys.PLAYLIST_URL].orEmpty())
    }
    /** Content-Auth capturado de Xuper; vacío usa el incluido en assets. */
    val xuperAuth: Flow<String> = context.portalDataStore.data.map { it[Keys.XUPER_AUTH].orEmpty() }
    val updateUrl: Flow<String> = context.portalDataStore.data.map { it[Keys.UPDATE_URL].orEmpty() }
    val updateVersion: Flow<String> = context.portalDataStore.data.map { it[Keys.UPDATE_VERSION].orEmpty() }

    suspend fun snapshot(): Snapshot {
        val prefs = context.portalDataStore.data.first()
        return Snapshot(
            host = prefs[Keys.HOST].orEmpty(),
            appId = prefs[Keys.APP_ID].orEmpty(),
            portalCode = prefs[Keys.PORTAL_CODE].orEmpty(),
            deviceId = prefs[Keys.DEVICE_ID].orEmpty(),
            deviceToken = prefs[Keys.DEVICE_TOKEN].orEmpty(),
            sn = prefs[Keys.SN].orEmpty(),
            reserve1 = prefs[Keys.RESERVE1].orEmpty(),
            b29 = prefs[Keys.B29].orEmpty(),
            playlistUrl = effectivePlaylist(prefs[Keys.PLAYLIST_URL].orEmpty()),
            xuperAuth = prefs[Keys.XUPER_AUTH].orEmpty(),
            updateUrl = prefs[Keys.UPDATE_URL].orEmpty(),
            updateVersion = prefs[Keys.UPDATE_VERSION].orEmpty(),
            updateNotes = prefs[Keys.UPDATE_NOTES].orEmpty(),
            updateSha = prefs[Keys.UPDATE_SHA].orEmpty(),
            lastUpdateCheck = prefs[Keys.UPDATE_CHECK]?.toLongOrNull() ?: 0L
        )
    }

    /** Crea el identificador estable de equipo la primera vez. */
    suspend fun ensureDeviceIds(sn: String, reserve1: String, b29: String) {
        context.portalDataStore.edit { prefs ->
            if (prefs[Keys.DEVICE_TOKEN].isNullOrBlank()) {
                prefs[Keys.DEVICE_TOKEN] = UUID.randomUUID().toString().replace("-", "")
            }
            if (prefs[Keys.SN].isNullOrBlank()) prefs[Keys.SN] = sn
            if (prefs[Keys.RESERVE1].isNullOrBlank()) prefs[Keys.RESERVE1] = reserve1
            if (prefs[Keys.B29].isNullOrBlank()) prefs[Keys.B29] = b29
            if (prefs[Keys.DEVICE_ID].isNullOrBlank()) {
                prefs[Keys.DEVICE_ID] = prefs[Keys.DEVICE_TOKEN].orEmpty()
            }
        }
    }

    suspend fun savePortal(host: String, appId: String, portalCode: String) {
        context.portalDataStore.edit {
            it[Keys.HOST] = host
            it[Keys.APP_ID] = appId
            it[Keys.PORTAL_CODE] = portalCode
        }
    }

    /** Lista M3U propia: canales en directo sin depender del portal. */
    suspend fun savePlaylist(url: String) {
        context.portalDataStore.edit {
            if (url.isBlank()) it.remove(Keys.PLAYLIST_URL) else it[Keys.PLAYLIST_URL] = url
        }
    }

    /** Content-Auth de Xuper; vacío vuelve al valor incluido en assets. */
    suspend fun saveXuperAuth(auth: String) {
        context.portalDataStore.edit {
            if (auth.isBlank()) it.remove(Keys.XUPER_AUTH) else it[Keys.XUPER_AUTH] = auth.trim()
        }
    }

    /** URL del manifiesto de actualizaciones (JSON); vacío desactiva el módulo. */
    suspend fun saveUpdateUrl(url: String) {
        context.portalDataStore.edit {
            if (url.isBlank()) it.remove(Keys.UPDATE_URL) else it[Keys.UPDATE_URL] = url
        }
    }

    /** Guarda el resultado de una descarga verificada por SHA-256. */
    suspend fun saveUpdateReady(version: String, notes: String, sha256: String) {
        context.portalDataStore.edit {
            it[Keys.UPDATE_VERSION] = version
            it[Keys.UPDATE_NOTES] = notes
            it[Keys.UPDATE_SHA] = sha256
        }
    }

    suspend fun clearUpdateReady() {
        context.portalDataStore.edit {
            it.remove(Keys.UPDATE_VERSION)
            it.remove(Keys.UPDATE_NOTES)
            it.remove(Keys.UPDATE_SHA)
        }
    }

    suspend fun markUpdateChecked(now: Long) {
        context.portalDataStore.edit { it[Keys.UPDATE_CHECK] = now.toString() }
    }

    data class Snapshot(
        val host: String,
        val appId: String,
        val portalCode: String,
        val deviceId: String,
        val deviceToken: String = "",
        val sn: String = "",
        val reserve1: String = "",
        val b29: String = "",
        val playlistUrl: String = "",
        val xuperAuth: String = "",
        val updateUrl: String = "",
        val updateVersion: String = "",
        val updateNotes: String = "",
        val updateSha: String = "",
        val lastUpdateCheck: Long = 0L
    )
}
