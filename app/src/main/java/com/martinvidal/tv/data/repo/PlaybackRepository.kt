package com.martinvidal.tv.data.repo

import android.net.Uri
import com.martinvidal.tv.data.net.DeviceProfile
import com.martinvidal.tv.data.net.PortalApi
import com.martinvidal.tv.data.net.PortalRequest
import com.martinvidal.tv.data.session.SettingsStore
import com.martinvidal.tv.ui.home.CardItem
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton

/** Datos minimos que necesita el reproductor. */
data class Playback(
    val url: String,
    val isLive: Boolean,
    /** Codigo `cyx_...` cuando el canal se reproduce via SDK ranger. */
    val mediaCode: String? = null,
    /** Program completo (con license) para PrepareProgram. */
    val programJson: String? = null
) {
    val viaSdk: Boolean get() = mediaCode != null && programJson != null
}

/**
 * Resolucion de la URL de reproduccion.
 *
 * Directo: `api/portalCore/v5/startPlayLive` (`StartPlayLiveBean`).
 * VOD: `api/portalCore/v10/startPlayVOD` (`StartPlayVODBean`).
 *
 * La respuesta real anida la direccion en `data.liveAddressList[].playCode`
 * (directo) o en `data.episodeList[].totalMovieList[].movieList[].license`
 * (VOD), pero el esquema varia entre portales. [extractUrl] recorre la
 * respuesta y elige la mejor candidata en lugar de fijar una ruta exacta.
 */
@Singleton
class PlaybackRepository @Inject constructor(
    private val api: PortalApi,
    private val session: SettingsStore,
    private val device: DeviceProfile,
    private val moshi: Moshi
) {

    fun demo(isLive: Boolean): Playback = Playback(DemoCatalog.STREAM, isLive)

    // PENDIENTE(P13): validar el extractor contra un portal vivo. Hoy solo se
    // probo el camino demo; el orden de calidades/CDN es una heuristica.
    suspend fun start(item: CardItem): Result<Playback> = runCatching {
        // Entrada de lista M3U: la direccion ya viene en la propia entrada.
        // Si lleva xup-license, se reproduce por el SDK ranger (genera el
        // Content-Auth internamente y sirve el stream por mem://).
        item.url?.takeIf { it.isNotBlank() }?.let { url ->
            sdkProgram(url, item.title)?.let { (code, program) ->
                return Result.success(Playback(url, true, code, program))
            }
            return Result.success(Playback(url, item.kind.isLive()))
        }

        val state = session.snapshot()
        val isLive = item.kind.isLive()

        val envelope = if (isLive) {
            api.epV5StartPlayLive(
                PortalRequest.build(
                    state,
                    device,
                    linkedMapOf(
                        "type" to "live",
                        "columnId" to null,
                        "channelCode" to item.id
                    )
                )
            )
        } else {
            api.epV10StartPlayVOD(
                PortalRequest.build(
                    state,
                    device,
                    linkedMapOf(
                        "columnId" to null,
                        "contentId" to item.id,
                        "seriesContentId" to "",
                        "type" to "vod",
                        "startTime" to 0,
                        "authType" to "",
                        "episodeNumberList" to intArrayOf()
                    )
                )
            )
        }

        envelope.problem()?.let { error(it) }
        val data = envelope.dataObject(moshi) ?: error("El portal devolvió una respuesta vacía")
        val url = extractUrl(data) ?: error("El portal no devolvió una dirección de reproducción")
        Playback(url, isLive)
    }

    /**
     * Construye el Program que espera PrepareProgram a partir de la license
     * incrustada en la URL M3U (`?xup-license=...`). Devuelve el media_code y
     * el JSON, o null si la entrada no es una licencia de Xuper.
     */
    private fun sdkProgram(url: String, title: String): Pair<String, String>? {
        val encodedLicense = Uri.parse(url).getQueryParameter("xup-license")
            ?.takeIf { it.isNotBlank() } ?: return null
        val license = Uri.decode(encodedLicense)
        val mediaCode = LICENSE_PARAM.findAll(license)
            .firstOrNull { it.groupValues[1] == "media_code" }
            ?.groupValues?.get(2) ?: return null
        if (!mediaCode.startsWith("cyx")) return null

        val media = org.json.JSONObject().apply {
            put("format", "ts")
            put("lang", "")
            put("license", license)
            put("name", mediaCode)
            put("quality", "480p")
            put("vcodec", "h264")
        }
        val program = org.json.JSONObject().apply {
            put("buss", "live")
            put("cause", "user")
            put("episode", "-1")
            put("from", "channellist")
            put("media", mediaCode)
            put("medias", org.json.JSONArray().put(media).put(media))
            put("name", mediaCode)
            put("start", 0)
            put("title", title)
        }
        return mediaCode to program.toString()
    }

    private fun String?.isLive(): Boolean {
        val value = this?.lowercase().orEmpty()
        return value == "live" || value == "channel" || value == "1" || value.contains("live")
    }

    /** Busca recursivamente la mejor URL de reproduccion de la respuesta. */
    private fun extractUrl(data: Map<String, Any?>): String? =
        walk(data, "", 0)?.second

    private fun walk(value: Any?, keyHint: String, depth: Int): Pair<Int, String>? {
        if (depth > 12) return null
        return when (value) {
            is Map<*, *> -> {
                var best: Pair<Int, String>? = null
                for ((rawKey, child) in value) {
                    val key = rawKey?.toString().orEmpty()
                    val hint = if (keyScore(key) > 0) key else keyHint
                    val found = walk(child, hint, depth + 1) ?: continue
                    val score = keyScore(key) + found.first + hintScore(keyHint)
                    if (best == null || score > best.first) best = score to found.second
                }
                best
            }
            is List<*> -> value.asSequence()
                .mapNotNull { walk(it, keyHint, depth + 1) }
                .maxByOrNull { it.first }
            is String -> if (isPlayable(value)) {
                valueScore(value) + hintScore(keyHint) to normalize(value)
            } else {
                null
            }
            else -> null
        }
    }

    private fun keyScore(key: String): Int {
        val k = key.lowercase()
        return when {
            "playcode" in k -> 120
            "playurl" in k -> 120
            "playaddress" in k -> 115
            "m3u8" in k -> 110
            "hls" in k -> 100
            "url" in k -> 90
            "license" in k -> 60
            "link" in k -> 50
            "address" in k -> 40
            else -> 0
        }
    }

    private fun hintScore(hint: String): Int = keyScore(hint) / 4

    private fun valueScore(value: String): Int {
        val v = value.lowercase()
        var score = 0
        if (v.startsWith("http://") || v.startsWith("https://")) score += 50
        if (v.startsWith("rtmp") || v.startsWith("rtsp")) score += 45
        if (".m3u8" in v) score += 50
        if (".mpd" in v) score += 40
        if (".mp4" in v) score += 40
        if (".flv" in v) score += 35
        if (".ts" in v) score += 20
        if ("/live/" in v || "playlist" in v) score += 15
        return score
    }

    private fun isPlayable(value: String): Boolean {
        val v = value.trim()
        if (v.length < 10 || v.any { it.isWhitespace() }) return false
        return v.startsWith("http://", true) ||
            v.startsWith("https://", true) ||
            v.startsWith("rtmp", true) ||
            v.startsWith("rtsp", true)
    }

    private fun normalize(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
    }

    private companion object {
        val LICENSE_PARAM = Regex("([a-z_]+)=([^&]+)")
    }
}
