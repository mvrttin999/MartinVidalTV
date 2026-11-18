package com.martinvidal.tv.data.repo

import android.content.Context
import com.martinvidal.tv.BuildConfig
import com.martinvidal.tv.data.session.SettingsStore
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manifiesto que publica el distribuidor junto al APK:
 *
 * ```json
 * {
 *   "versionCode": 2,
 *   "versionName": "1.0.1",
 *   "apkUrl": "https://ejemplo.com/MartinVidalTV-1.0.1.apk",
 *   "sha256": "hex de 64 caracteres del APK",
 *   "notes": "Novedades opcionales"
 * }
 * ```
 *
 * El SHA-256 evita instalar un APK modificado aunque el manifiesto se sirva
 * por HTTPS. En builds release solo se aceptan URLs HTTPS.
 */
@JsonClass(generateAdapter = false)
data class UpdateManifest(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val sha256: String = "",
    val notes: String = ""
)

data class UpdateOutcome(
    val code: Code,
    val version: String = "",
    val message: String = ""
) {
    enum class Code { DISABLED, TOO_SOON, UP_TO_DATE, ALREADY_READY, DOWNLOADED, FAILED }
}

/** Ruta interna donde se guarda el APK descargado; no necesita permisos de almacenamiento. */
fun Context.updateApkFile(): File = File(filesDir, "updates/app-update.apk")

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val session: SettingsStore,
    private val notifier: UpdateNotifier
) {

    companion object {
        private const val MIN_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private val LOCAL_HOSTS = setOf("127.0.0.1", "localhost", "10.0.2.2")
    }

    /**
     * Comprueba el manifiesto y, si hay una versión más nueva, descarga y
     * verifica el APK. Se ejecuta siempre en segundo plano (Worker).
     */
    suspend fun checkAndDownload(force: Boolean = false): UpdateOutcome =
        withContext(Dispatchers.IO) {
            runCatching { perform(force) }.getOrElse {
                UpdateOutcome(
                    UpdateOutcome.Code.FAILED,
                    message = it.message?.take(160) ?: "Error desconocido"
                )
            }
        }

    private suspend fun perform(force: Boolean): UpdateOutcome {
        val snapshot = session.snapshot()
        val configuredUrl = snapshot.updateUrl.ifBlank { BuildConfig.UPDATE_URL }
        if (configuredUrl.isBlank()) {
            return UpdateOutcome(UpdateOutcome.Code.DISABLED, message = "Sin URL de actualización")
        }
        val now = System.currentTimeMillis()
        if (!force && now - snapshot.lastUpdateCheck < MIN_INTERVAL_MS) {
            return UpdateOutcome(UpdateOutcome.Code.TOO_SOON, message = "Comprobada hace poco")
        }
        session.markUpdateChecked(now)

        val manifestUrl = safeUrl(configuredUrl)
        val manifest = fetchManifest(manifestUrl)
        if (manifest.versionCode <= BuildConfig.VERSION_CODE || manifest.versionName.isBlank()) {
            return UpdateOutcome(
                UpdateOutcome.Code.UP_TO_DATE,
                message = "Ya estás en la última versión"
            )
        }
        if (manifest.sha256.length != 64 ||
            !manifest.sha256.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        ) {
            return UpdateOutcome(
                UpdateOutcome.Code.FAILED,
                message = "El manifiesto no trae un SHA-256 válido"
            )
        }

        // Si la misma versión ya está verificada en disco, solo reiteramos el aviso.
        val file = context.updateApkFile()
        if (snapshot.updateVersion == manifest.versionName && file.exists() &&
            sha256(file).equals(manifest.sha256, ignoreCase = true)
        ) {
            notifier.show(manifest.versionName, manifest.notes)
            return UpdateOutcome(
                UpdateOutcome.Code.ALREADY_READY,
                version = manifest.versionName,
                message = "Actualización lista para instalar"
            )
        }

        downloadAndVerify(safeUrl(manifest.apkUrl), manifest.sha256, file)
        session.saveUpdateReady(manifest.versionName, manifest.notes, manifest.sha256)
        notifier.show(manifest.versionName, manifest.notes)
        return UpdateOutcome(
            UpdateOutcome.Code.DOWNLOADED,
            version = manifest.versionName,
            message = "Descargada la versión ${manifest.versionName}"
        )
    }

    private fun fetchManifest(url: String): UpdateManifest {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} al leer el manifiesto")
            val body = response.body?.string().orEmpty()
            val adapter = moshi.adapter(UpdateManifest::class.java)
            return adapter.fromJson(body) ?: throw IOException("Manifiesto JSON vacío")
        }
    }

    private fun downloadAndVerify(url: String, expectedSha: String, target: File) {
        val parent = target.parentFile ?: throw IOException("Ruta de descarga no válida")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("No se pudo crear $parent")
        val part = File(parent, "app-update.part")
        if (part.exists()) part.delete()

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} al descargar el APK")
            val source = response.body?.byteStream() ?: throw IOException("Respuesta vacía")
            val digest = MessageDigest.getInstance("SHA-256")
            part.outputStream().use { out ->
                source.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expectedSha, ignoreCase = true)) {
                part.delete()
                throw IOException("El APK descargado no coincide con el SHA-256 del manifiesto")
            }
        }
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) throw IOException("No se pudo guardar el APK descargado")
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").let { digest ->
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

    /** En release solo HTTPS; en debug se permite localhost para probar. */
    private fun safeUrl(raw: String): String {
        val url = runCatching { raw.toHttpUrl() }.getOrNull()
            ?: throw IllegalArgumentException("URL no válida: $raw")
        val local = url.host in LOCAL_HOSTS && BuildConfig.DEBUG
        if (url.scheme != "https" && !local) {
            throw IllegalArgumentException("Las actualizaciones requieren HTTPS: $raw")
        }
        return url.toString()
    }

    suspend fun clearReadyIfGone() {
        val file = context.updateApkFile()
        if (!file.exists()) session.clearUpdateReady()
    }

    suspend fun clearDownloaded() {
        context.updateApkFile().delete()
        session.clearUpdateReady()
    }

    fun downloadedVersion(): String? {
        val file = context.updateApkFile()
        return if (file.exists() && file.length() > 0) file.name else null
    }
}

/** Pequeña ayuda para el scheduler: evita cálculos de tiempo duplicados. */
internal object UpdateTiming {
    fun minutesToMillis(minutes: Long): Long = TimeUnit.MINUTES.toMillis(minutes)
}
