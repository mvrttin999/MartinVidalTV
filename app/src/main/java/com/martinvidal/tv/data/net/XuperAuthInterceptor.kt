package com.martinvidal.tv.data.net

import com.martinvidal.tv.data.session.SettingsStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.util.Collections

/**
 * Cabeceras que el servidor de directos de Xuper exige en CADA petición
 * (manifiesto y segmentos). La lista M3U incluida usa esa fuente; sin estas
 * cabeceras el servidor responde 401/409/410.
 *
 * El `Content-Auth` es de un solo uso y caduca en minutos: se introduce desde
 * Ajustes con el valor capturado de la app original. Este interceptor no
 * intenta generarlo ni lo renueva: solo lo aplica a las peticiones del host.
 *
 * `Content-License` va por canal. La M3U lo lleva como parámetro `xup-license`
 * en la URL del manifiesto; aquí se extrae, se recuerda por `media_code` y se
 * envía como cabecera también en los segmentos (cuyas URL relativas no lo
 * repiten).
 */
class XuperAuthInterceptor(
    private val session: SettingsStore,
    bundledAuth: String
) : Interceptor {

    @Volatile
    private var overriddenAuth: String = ""

    @Volatile
    private var cachedAt = 0L

    private val fallbackAuth: String = bundledAuth.trim()

    private val licenses = Collections.synchronizedMap(mutableMapOf<String, String>())

    private fun currentAuth(): String {
        val now = System.currentTimeMillis()
        if (now - cachedAt < CACHE_MS && overriddenAuth.isNotBlank()) return overriddenAuth
        val stored = runCatching { runBlocking { session.snapshot().xuperAuth } }.getOrDefault("")
        overriddenAuth = stored.trim()
        cachedAt = now
        return overriddenAuth.ifBlank { fallbackAuth }
    }

    fun invalidate() {
        cachedAt = 0L
        overriddenAuth = ""
        licenses.clear()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url
        if (!isXuperHost(url.host)) return chain.proceed(original)

        val mediaCode = mediaCodeOf(url.encodedPath)
        var license = mediaCode?.let { licenses[it] }.orEmpty()

        var target = original
        val queryLicense = url.queryParameter(QUERY_LICENSE)
        if (!queryLicense.isNullOrBlank()) {
            license = queryLicense
            if (mediaCode != null) licenses[mediaCode] = queryLicense
            target = original.newBuilder()
                .url(url.newBuilder().removeAllQueryParameters(QUERY_LICENSE).build())
                .build()
        }

        val builder = target.newBuilder()
            .header("App", APP_ID)
            .header("App-Version", APP_VERSION)
            .header("Pragma", "akamai-x-cache-on")
            .header("Ranger-Id", RANGER_ID)
            .header("User-Agent", USER_AGENT)
            .header("X-Buffer", X_BUFFER)

        val auth = currentAuth()
        if (auth.isNotBlank()) builder.header("Content-Auth", auth)
        if (license.isNotBlank()) builder.header("Content-License", license)

        return chain.proceed(builder.build())
    }

    private fun isXuperHost(host: String): Boolean =
        host.equals(HOST, ignoreCase = true)

    /** `/live/<code>.m3u8` o `/live/<code>/<code>_shisui_N.ts`. */
    private fun mediaCodeOf(path: String): String? {
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.size < 2 || !parts[0].equals("live", ignoreCase = true)) return null
        return parts[1].substringBefore('.').takeIf { it.startsWith("cyx") }
    }

    private companion object {
        const val HOST = "64.31.43.179"
        const val QUERY_LICENSE = "xup-license"
        const val APP_ID = "com.android.msandroid"
        const val APP_VERSION = "43404"
        const val RANGER_ID = "4bnOxi_p-8U-O57dLlo0J3w8Mn9A2wcB9D"
        const val USER_AGENT = "Ranger/4.9.4-17294ac0"
        const val X_BUFFER = "7502000000"
        const val CACHE_MS = 5_000L
    }
}
