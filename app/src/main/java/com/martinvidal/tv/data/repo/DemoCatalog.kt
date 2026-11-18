package com.martinvidal.tv.data.repo

import com.martinvidal.tv.ui.home.CardItem
import com.martinvidal.tv.ui.home.Shelf

/**
 * Catálogo local de demostración.
 *
 * Se usa cuando el portal no entrega contenido (equipo sin alta, portal
 * retirado o sin red). Permite recorrer toda la interfaz —portada, ficha,
 * búsqueda y reproductor— sin depender del servidor.
 */
object DemoCatalog {

    /** Stream público de prueba (HLS) para validar el reproductor. */
    const val STREAM = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

    private val live = listOf(
        CardItem("live-1", "Magazine matinal", null, "live"),
        CardItem("live-2", "Informativo 24h", null, "live"),
        CardItem("live-3", "Cine de medianoche", null, "live"),
        CardItem("live-4", "Deportes en directo", null, "live"),
        CardItem("live-5", "Documental abierto", null, "live")
    )

    private val movies = listOf(
        CardItem("demo-1", "La última frontera", null, "movie"),
        CardItem("demo-2", "Señales en la niebla", null, "movie"),
        CardItem("demo-3", "El precio del silencio", null, "movie"),
        CardItem("demo-4", "Ciudad de cristal", null, "movie"),
        CardItem("demo-5", "Un verano más", null, "movie"),
        CardItem("demo-6", "Notas de invierno", null, "movie")
    )

    private val series = listOf(
        CardItem("demo-7", "Distrito sur", null, "series"),
        CardItem("demo-8", "Los días grises", null, "series"),
        CardItem("demo-9", "Código abierto", null, "series"),
        CardItem("demo-10", "Puerto océano", null, "series"),
        CardItem("demo-11", "Herencia", null, "series")
    )

    private val kids = listOf(
        CardItem("demo-12", "Aventuras de Ana", null, "kids"),
        CardItem("demo-13", "El taller de Martín", null, "kids"),
        CardItem("demo-14", "Mundo animal", null, "kids"),
        CardItem("demo-15", "Pequeños inventores", null, "kids")
    )

    private val docs = listOf(
        CardItem("demo-16", "Mares del sur", null, "doc"),
        CardItem("demo-17", "Historia oculta", null, "doc"),
        CardItem("demo-18", "Planeta hambre", null, "doc")
    )

    val shelves: List<Shelf>
        get() = listOf(
            Shelf("En directo", live, "live"),
            Shelf("Películas", movies, "movies"),
            Shelf("Series", series, "series"),
            Shelf("Infantil", kids, "kids"),
            Shelf("Documentales", docs, "docs")
        )

    fun find(id: String): CardItem? =
        shelves.asSequence().flatMap { it.items.asSequence() }.firstOrNull { it.id == id }

    fun shelf(id: String): Shelf? = shelves.firstOrNull { it.id == id }

    data class DemoDetail(
        val title: String,
        val kind: String,
        val synopsis: String,
        val year: String,
        val rating: String,
        val duration: String
    )

    fun detail(item: CardItem): DemoDetail = when (item.kind) {
        "live" -> DemoDetail(
            title = item.title,
            kind = "live",
            synopsis = "Canal de ejemplo para comprobar la reproducción en directo. " +
                "El portal real entrega la dirección definitiva al iniciar la sesión.",
            year = "2026",
            rating = "TP",
            duration = "En directo"
        )
        "series" -> DemoDetail(
            title = item.title,
            kind = "series",
            synopsis = "Primera temporada completa. Una historia de personajes que " +
                "descubren que el pasado nunca permanece enterrado.",
            year = "2025",
            rating = "+16",
            duration = "12 episodios"
        )
        "kids" -> DemoDetail(
            title = item.title,
            kind = "kids",
            synopsis = "Contenido infantil sin sustos ni violencia. Pensado para los " +
                "más pequeños de la casa.",
            year = "2026",
            rating = "TP",
            duration = "22 min"
        )
        "doc" -> DemoDetail(
            title = item.title,
            kind = "doc",
            synopsis = "Un recorrido documental por lugares y oficios que casi nadie " +
                "llega a ver.",
            year = "2024",
            rating = "TP",
            duration = "48 min"
        )
        else -> DemoDetail(
            title = item.title,
            kind = "movie",
            synopsis = "Una función de ejemplo. La ficha completa (reparto, géneros, " +
                "tráilers y calidades disponibles) llega desde el portal del distribuidor.",
            year = "2024",
            rating = "+13",
            duration = "108 min"
        )
    }

    fun search(query: String): List<CardItem> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return shelves.flatMap { it.items }
            .distinctBy { it.id }
            .filter { it.title.lowercase().contains(needle) }
    }
}
