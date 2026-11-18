package com.martinvidal.tv.data.net

import com.martinvidal.tv.data.session.SettingsStore
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Ajusta cada peticion al contrato del portal:
 *
 * - reescribe el host con el portal configurado;
 * - anade las cabeceras `Content-type`, `apk`, `apkVer` y `spkgVer`, que son
 *   las unicas que envia la app original (`hd.b`);
 * - anade `X-Request-Trace` y `X-Client-Meta` (MD5 de traza + clave) cuando el
 *   endpoint lo pide. Aqui se envian siempre: el servidor ignora las
 *   cabeceras que no espera y asi cubrimos tambien las rutas marcadas.
 *
 * Los datos del equipo (portalCode, deviceToken, sn) no van en
 * cabeceras: viaja dentro de `specificParams`, como en el cliente original.
 */
class PortalInterceptor(
    private val session: SettingsStore,
    private val device: DeviceProfile
) : Interceptor {

    @Volatile
    private var cached: SettingsStore.Snapshot? = null

    @Volatile
    private var cachedAt = 0L

    private fun current(): SettingsStore.Snapshot {
        val now = System.currentTimeMillis()
        val snapshot = cached
        if (snapshot != null && now - cachedAt < CACHE_MS) return snapshot
        val fresh = runBlocking { session.snapshot() }
        cached = fresh
        cachedAt = now
        return fresh
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val state = current()
        val original = chain.request()

        // El mismo cliente se usa para el reproductor: solo las rutas del
        // portal se reescriben y se firman. Un manifiesto HLS externo jamas
        // debe acabar apuntando al host del portal.
        if (!original.url.encodedPath.startsWith("/api/")) {
            return chain.proceed(original)
        }

        val url = rewriteHost(original.url, state.host)
        val trace = java.util.UUID.randomUUID().toString()

        val request = original.newBuilder()
            .url(url)
            .header("Content-type", "application/json;charset=utf-8")
            .header("apk", device.appId)
            .header("apkVer", device.appVersion)
            .header("spkgVer", device.sysVersion)
            .header("X-Request-Trace", trace)
            .header("X-Client-Meta", PortalCrypto.md5(trace + PortalCrypto.KEY))
            .build()

        return chain.proceed(request)
    }

    /**
     * Sustituye esquema, host y puerto conservando la ruta del endpoint. Si el
     * host incluye un prefijo de ruta (`https://host/base`) tambien se aplica.
     */
    private fun rewriteHost(
        original: okhttp3.HttpUrl,
        host: String
    ): okhttp3.HttpUrl {
        if (host.isBlank()) return original
        val base = host.trimEnd('/').toHttpUrlOrNull() ?: return original
        val builder = original.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
        val prefix = base.encodedPath.trimEnd('/')
        if (prefix.isNotEmpty() && prefix != "/") {
            builder.encodedPath(prefix + original.encodedPath)
        }
        return builder.build()
    }

    private companion object {
        const val CACHE_MS = 15_000L
    }
}
