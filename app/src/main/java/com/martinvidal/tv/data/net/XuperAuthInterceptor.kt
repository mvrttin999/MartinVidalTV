package com.martinvidal.tv.data.net

import okhttp3.Interceptor
import okhttp3.Response
import java.util.Collections

/**
 * Cabeceras residuales para el host de directos de Xuper.
 *
 * El Content-Auth ya no se envia desde aqui: el SDK ranger (RangerBridge) lo
 * genera internamente y sirve el stream por un proxy local mem://. Las
 * entradas M3U con xup-license se reproducen por ese camino y no llegan a este
 * interceptor. Se conservan las cabeceras de dispositivo y el Content-License
 * por canal para cualquier fuente http residual.
 */
class XuperAuthInterceptor : Interceptor {

    private val licenses = Collections.synchronizedMap(mutableMapOf<String, String>())

    fun invalidate() {
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
    }
}
