package com.martinvidal.tv.data.repo

import android.content.Context
import com.martinvidal.tv.data.session.SettingsStore
import com.martinvidal.tv.ui.home.CardItem
import com.martinvidal.tv.ui.home.Shelf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fuente de contenido propia: una lista M3U/M3U8 accesible por HTTP.
 *
 * Sirve para que la app tenga canales reales sin depender del portal: el
 * distribuidor (o el propio usuario) entrega una URL de lista y aqui se
 * convierten las entradas `#EXTINF` en estanterias, busqueda y reproduccion.
 *
 * Formato soportado (el habitual de IPTV):
 *
 * ```
 * #EXTM3U
 * #EXTINF:-1 tvg-id="canal1" tvg-name="Canal 1" tvg-logo="https://..." group-title="Cine",Canal 1
 * https://servidor/canal1/index.m3u8
 * #EXTGRP:Deportes
 * #EXTINF:-1,Otra pista
 * https://servidor/otra.m3u8
 * ```
 *
 * Si no hay lista configurada se usa la lista incluida en la app
 * (`assets/lista.m3u`), para que la portada tenga canales reales desde el
 * primer arranque.
 *
 * La lista se cachea en memoria por URL; se vuelve a descargar si cambia la
 * URL o si [invalidate] se llama tras guardar la configuracion.
 */
@Singleton
class PlaylistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val session: SettingsStore
) {

    private data class Entry(
        val id: String,
        val name: String,
        val logo: String?,
        val group: String,
        val url: String
    )

    private var cachedUrl: String = ""
    private var cached: List<Entry> = emptyList()

    /** `true` si la ultima carga uso la lista incluida en la app. */
    var usedBundled: Boolean = false
        private set

    /** Hay lista configurada o, si no, la incluida en `assets/`. */
    suspend fun available(): Boolean =
        session.snapshot().playlistUrl.isNotBlank() || bundledExists()

    fun invalidate() {
        cachedUrl = ""
        cached = emptyList()
    }

    /** Estanterias agrupadas por `group-title`, en el orden de la lista. */
    suspend fun shelves(): List<Shelf> {
        val entries = load()
        if (entries.isEmpty()) error("La lista M3U no contiene canales")
        return entries
            .groupBy { it.group }
            .entries
            .map { (group, list) ->
                Shelf(
                    title = group,
                    items = list.map { it.toCard() },
                    id = "pl:" + group.lowercase()
                )
            }
    }

    suspend fun search(query: String): List<CardItem> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return load()
            .asSequence()
            .filter { it.name.lowercase().contains(needle) || it.group.lowercase().contains(needle) }
            .map { it.toCard() }
            .toList()
    }

    suspend fun find(id: String): CardItem? = load().firstOrNull { it.id == id }?.toCard()

    suspend fun urlOf(id: String): String? = load().firstOrNull { it.id == id }?.url

    private fun Entry.toCard(): CardItem = CardItem(
        id = id,
        title = name,
        poster = logo,
        kind = "live",
        url = url,
        group = group
    )

    private suspend fun load(): List<Entry> {
        val url = session.snapshot().playlistUrl
        val key = url.ifBlank { ASSET_URL }
        if (key == cachedUrl && cached.isNotEmpty()) return cached

        // Con URL remota: descargar; si falla (sin red, lista caida, HTTP 404),
        // usar la ultima copia buena guardada en disco. Solo si no hay ninguna
        // copia se cae a la lista incluida en assets.
        var fromBundled = url.isBlank()
        val entries = if (url.isBlank()) {
            readAsset()
        } else {
            val fresh = runCatching { fetch(url) }.getOrNull()
            val cachedDisk = if (fresh.isNullOrEmpty()) readDisk(url) else emptyList()
            when {
                !fresh.isNullOrEmpty() -> fresh
                cachedDisk.isNotEmpty() -> cachedDisk
                else -> {
                    fromBundled = true
                    readAsset()
                }
            }
        }
        usedBundled = fromBundled
        cachedUrl = key
        cached = entries
        return entries
    }

    private fun bundledExists(): Boolean =
        runCatching { context.assets.open(ASSET).close() }.isSuccess

    private suspend fun readAsset(): List<Entry> = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(ASSET).bufferedReader().use { it.readText() }
        }.map(::parse).getOrElse { emptyList() }
    }

    private suspend fun fetch(url: String): List<Entry> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
            .build()

        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("La lista M3U respondió HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }
        val entries = parse(body)
        // Solo se guarda una copia si la lista tenia canales: no queremos
        // sobrescribir una copia buena con una respuesta vacia del servidor.
        if (entries.isNotEmpty()) writeDisk(body)
        entries
    }

    /** Ultima lista descargada con exito; se usa si la fuente remota falla. */
    private fun readDisk(url: String): List<Entry> = runCatching {
        val file = File(context.filesDir, DISK_CACHE)
        if (!file.exists()) return@runCatching emptyList()
        val raw = file.readText()
        // La copia debe corresponder a la URL vigente; si el usuario cambio de
        // lista, los canales de la anterior no tienen sentido.
        val storedUrl = raw.lineSequence().firstOrNull()?.removePrefix(URL_MARKER)
        if (storedUrl?.trim() != url.trim()) emptyList() else parse(raw)
    }.getOrDefault(emptyList())

    private fun writeDisk(body: String) = runCatching {
        File(context.filesDir, DISK_CACHE).writeText(URL_MARKER + System.lineSeparator() + body)
    }

    private fun parse(raw: String): List<Entry> {
        val result = ArrayList<Entry>()
        val lines = raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }

        var pending: Pending? = null
        var lastGroup = ""

        for (line in lines) {
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = ATTR.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                    val name = line.substringAfter(',', "").trim()
                        .ifBlank { attrs["tvg-name"].orEmpty() }
                        .ifBlank { attrs["tvg-id"].orEmpty() }
                    val group = attrs["group-title"].orEmpty().ifBlank { lastGroup }
                    pending = Pending(
                        name = name,
                        logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                        group = group
                    )
                }
                line.startsWith("#EXTGRP", ignoreCase = true) -> {
                    // Grupo declarado aparte: se aplica a las entradas siguientes.
                    lastGroup = line.substringAfter(':', "").trim()
                }
                line.startsWith("#") -> Unit
                else -> {
                    val info = pending
                    pending = null
                    if (!isStreamUrl(line)) continue
                    val group = info?.group.orEmpty().ifBlank { lastGroup }.ifBlank { "Canales" }
                    val name = info?.name.orEmpty().ifBlank { nameFromUrl(line) }
                    result += Entry(
                        id = idOf(line),
                        name = name,
                        logo = info?.logo,
                        group = group,
                        url = line
                    )
                }
            }
        }
        return result
    }

    private fun isStreamUrl(value: String): Boolean =
        value.startsWith("http://", true) ||
            value.startsWith("https://", true) ||
            value.startsWith("rtmp", true) ||
            value.startsWith("rtsp", true) ||
            value.startsWith("udp://", true)

    private fun nameFromUrl(url: String): String =
        url.substringAfterLast('/').substringBefore('?').ifBlank { "Canal" }

    private fun idOf(url: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(url.toByteArray())
        return "pl-" + digest.take(6).joinToString("") { "%02x".format(it) }
    }

    private data class Pending(val name: String, val logo: String?, val group: String)

    private companion object {
        const val ASSET = "lista.m3u"
        const val ASSET_URL = "asset://$ASSET"
        const val DISK_CACHE = "playlist-cache.m3u"
        const val URL_MARKER = "# url:"
        val ATTR = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
    }
}
