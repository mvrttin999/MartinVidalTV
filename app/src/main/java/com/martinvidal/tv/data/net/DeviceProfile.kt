package com.martinvidal.tv.data.net

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Perfil de dispositivo que el portal espera en `commonParams`.
 *
 * Los valores de appId/version son los del contrato del portal; el resto se
 * toma del dispositivo real igual que hacia el APK original.
 */
@Singleton
class DeviceProfile @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val appId: String = APP_ID
    val appVersion: String = APP_VERSION_CODE
    val appLanguage: String = Locale.getDefault().language.takeIf { it.isNotBlank() } ?: "es"
    val loginType: String = "1"

    val hardware: String = Build.HARDWARE.orEmpty()
    val model: String = Build.MODEL.orEmpty()
    val product: String = Build.PRODUCT.orEmpty()
    val cpu: String = Build.CPU_ABI.orEmpty()
    val sdkVer: Int = Build.VERSION.SDK_INT

    val sysVersion: String by lazy { buildSysVersion() }

    val sn: String by lazy {
        runCatching { Build.SERIAL }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
            ?: Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
    }

    /**
     * `reserve1` es el identificador de red del equipo. Android moderno ya no
     * expone la MAC real, asi que se usa una derivada estable del ANDROID_ID.
     */
    val reserve1: String by lazy {
        val seed = sn
        val hash = PortalCrypto.md5("mac:$seed")
        (0 until 6).joinToString(":") { i -> hash.substring(i * 2, i * 2 + 2) }
    }

    private fun buildSysVersion(): String {
        val stamp = runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            }.format(Date(Build.TIME))
        }.getOrDefault(Build.DISPLAY.orEmpty())
        return "${stamp}_${Build.VERSION.SDK_INT}_${Build.VERSION.RELEASE}_${kernelVersion()}"
    }

    private fun kernelVersion(): String = runCatching {
        val text = ProcessBuilder("cat", "/proc/version").start().inputStream
            .bufferedReader()
            .readText()
        val start = text.indexOf("version ")
        if (start < 0) return@runCatching ""
        val tail = text.substring(start + 8)
        tail.substring(0, tail.indexOf(' '))
    }.getOrDefault("")

    companion object {
        const val APP_ID = "com.android.msandroid"
        const val APP_VERSION_CODE = "49920"
        const val APP_VERSION_NAME = "4.99.20"
    }
}
