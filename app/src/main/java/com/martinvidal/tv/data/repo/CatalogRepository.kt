package com.martinvidal.tv.data.repo

import com.martinvidal.tv.data.net.DeviceProfile
import com.martinvidal.tv.data.net.PortalApi
import com.martinvidal.tv.data.net.PortalRequest
import com.martinvidal.tv.data.session.SettingsStore
import com.martinvidal.tv.ui.home.CardItem
import com.martinvidal.tv.ui.home.Shelf
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Acceso al catalogo del portal: portada, columnas, VOD, canales y busqueda.
 *
 * Los nombres de los campos siguen los beans del cliente original
 * (`GetHomeBean`, `GetNextColumnBean`, `GetLiveDataBean`, `SearchByNameBean`),
 * y la identidad de sesion viaja en `specificParams`.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val api: PortalApi,
    private val session: SettingsStore,
    private val device: DeviceProfile,
    private val moshi: Moshi
) {

    suspend fun home(): List<Shelf> {
        val envelope = api.epGetHome(request(mapOf("version" to "1")))
        envelope.problem()?.let { error(it) }
        val data = envelope.dataObject(moshi).orEmpty()
        val shelves = data.list("recommendList").mapNotNull { raw ->
            val shelf = raw.asMap() ?: return@mapNotNull null
            val title = shelf.string("name", "alias").orEmpty()
            val items = shelf.list("assetList").mapNotNull { it.asMap()?.toCard() } +
                shelf.list("channelList").mapNotNull { it.asMap()?.toCard() }
            if (items.isEmpty()) null else Shelf(title.ifBlank { "Recomendados" }, items)
        }
        if (shelves.isEmpty()) error("El portal no devolvió contenido")
        return shelves
    }

    suspend fun nextColumns(columnCode: String, page: Int = 1): List<CardItem> {
        val envelope = api.epGetNextColumns(
            request(
                mapOf(
                    "columnCode" to columnCode,
                    "pageNum" to page,
                    "pageSize" to 60
                )
            )
        )
        envelope.problem()?.let { error(it) }
        return envelope.dataObject(moshi).orEmpty()
            .list("recommendList")
            .flatMap { it.asMap()?.list("assetList").orEmpty() }
            .mapNotNull { it.asMap()?.toCard() }
    }

    suspend fun live(columnId: Int): List<CardItem> {
        val envelope = api.epV7GetLiveData(
            request(
                mapOf(
                    "columnId" to columnId,
                    "pageNum" to 1,
                    "pageSize" to 3000
                )
            )
        )
        envelope.problem()?.let { error(it) }
        return envelope.dataObject(moshi).orEmpty()
            .list("channelList")
            .mapNotNull { it.asMap()?.toCard() }
    }

    suspend fun search(value: String, page: Int = 1): List<CardItem> {
        val envelope = api.epV3SearchByName(
            request(
                mapOf(
                    "value" to value,
                    "pageNum" to page,
                    "pageSize" to 60
                )
            )
        )
        envelope.problem()?.let { error(it) }
        val data = envelope.dataObject(moshi).orEmpty()
        return data.list("itemList").mapNotNull { it.asMap()?.toCard() } +
            data.list("contentList").mapNotNull { it.asMap()?.toCard() }
    }

    suspend fun detail(contentId: String): Map<String, Any?>? {
        val envelope = api.epV4GetItemData(
            request(
                mapOf(
                    "contentId" to contentId,
                    "macAddr" to session.snapshot().reserve1.ifBlank { device.reserve1 }
                )
            )
        )
        envelope.problem()?.let { error(it) }
        return envelope.dataObject(moshi)
    }

    private suspend fun request(specific: Map<String, Any?>): Map<String, Any?> =
        PortalRequest.build(session.snapshot(), device, specific)

    private fun Map<String, Any?>.toCard(): CardItem? {
        val id = string("contentId", "channelCode", "code") ?: return null
        val title = string("name", "showChannelName", "alias") ?: return null
        val poster = string("showPosterUrl", "showIconUrl")
            ?: list("posterList").firstNotNullOfOrNull { it.asMap()?.string("fileUrl") }
        val type = string("contentType", "programType", "type")
        return CardItem(id = id, title = title, poster = poster, kind = type)
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asMap(): Map<String, Any?>? = this as? Map<String, Any?>

    private fun Map<String, Any?>.string(vararg keys: String): String? {
        for (key in keys) {
            val value = this[key]
            if (value is String && value.isNotBlank()) return value
        }
        return null
    }

    private fun Map<String, Any?>.list(key: String): List<Any?> {
        val value = this[key]
        return value as? List<Any?> ?: emptyList()
    }
}
