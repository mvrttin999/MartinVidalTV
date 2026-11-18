package com.martinvidal.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinvidal.tv.data.repo.CatalogRepository
import com.martinvidal.tv.data.repo.DemoCatalog
import com.martinvidal.tv.data.repo.Playback
import com.martinvidal.tv.data.repo.PlaybackRepository
import com.martinvidal.tv.ui.home.CardItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailState(
    val loading: Boolean = true,
    val title: String = "",
    val kind: String? = null,
    val synopsis: String = "",
    val meta: String = "",
    val poster: String? = null,
    val demo: Boolean = false,
    val notice: String? = null,
    val playLoading: Boolean = false,
    val playError: String? = null,
    val playback: Playback? = null
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val playback: PlaybackRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    fun load(item: CardItem, demo: Boolean) {
        _state.value = DetailState(
            loading = true,
            title = item.title,
            kind = item.kind,
            poster = item.poster
        )
        // Entrada de lista M3U: no hay ficha en el portal, se muestra lo que
        // trae la propia entrada y se reproduce su URL directa.
        if (!item.url.isNullOrBlank()) {
            _state.value = DetailState(
                loading = false,
                title = item.title,
                kind = item.kind ?: "live",
                synopsis = "Canal de la lista de canales. Se reproduce directamente " +
                    "desde la dirección indicada en la lista, sin pasar por el portal.",
                meta = item.group ?: "En directo",
                poster = item.poster,
                demo = false
            )
            return
        }

        if (demo || DemoCatalog.find(item.id) != null) {
            applyDemo(item, notice = null)
            return
        }
        viewModelScope.launch {
            val data = runCatching { catalog.detail(item.id) }.getOrNull()
            if (data.isNullOrEmpty()) {
                applyDemo(
                    item,
                    notice = "No se pudo cargar la ficha del portal. Se muestra información de ejemplo."
                )
                return@launch
            }
            val fallback = DemoCatalog.detail(item)
            _state.value = DetailState(
                loading = false,
                title = data.string("name", "contentName", "title") ?: item.title,
                kind = item.kind ?: fallback.kind,
                synopsis = data.string("introduction", "description", "synopsis", "brief")
                    ?: fallback.synopsis,
                meta = metaOf(
                    data.string("releaseYear", "year", "publishTime") ?: fallback.year,
                    data.string("score", "rating", "ageLevel") ?: fallback.rating,
                    data.string("duration", "timeLength") ?: fallback.duration
                ),
                poster = data.string("showPosterUrl", "posterUrl", "iconUrl") ?: item.poster,
                demo = false
            )
        }
    }

    fun play(item: CardItem) {
        if (_state.value.playLoading) return
        val useDemo = item.url.isNullOrBlank() &&
            (_state.value.demo || DemoCatalog.find(item.id) != null)
        _state.value = _state.value.copy(playLoading = true, playError = null)
        viewModelScope.launch {
            if (useDemo) {
                _state.value = _state.value.copy(
                    playLoading = false,
                    playback = playback.demo(item.kind.isLive())
                )
                return@launch
            }
            playback.start(item).fold(
                onSuccess = {
                    _state.value = _state.value.copy(playLoading = false, playback = it)
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        playLoading = false,
                        playError = it.message ?: "No se pudo iniciar la reproducción"
                    )
                }
            )
        }
    }

    /** Evita relanzar el reproductor al recomponer la pantalla. */
    fun consumePlayback() {
        _state.value = _state.value.copy(playback = null)
    }

    fun dismissPlayError() {
        _state.value = _state.value.copy(playError = null)
    }

    private fun applyDemo(item: CardItem, notice: String?) {
        val detail = DemoCatalog.detail(item)
        _state.value = DetailState(
            loading = false,
            title = detail.title,
            kind = detail.kind,
            synopsis = detail.synopsis,
            meta = metaOf(detail.year, detail.rating, detail.duration),
            poster = item.poster,
            demo = true,
            notice = notice
        )
    }

    private fun metaOf(year: String, rating: String, duration: String): String =
        listOf(year, rating, duration).filter { it.isNotBlank() }.joinToString("  ·  ")

    private fun String?.isLive(): Boolean {
        val value = this?.lowercase().orEmpty()
        return value == "live" || value == "channel" || value == "1" || value.contains("live")
    }

    private fun Map<String, Any?>.string(vararg keys: String): String? {
        for (key in keys) {
            val value = this[key]
            if (value is String && value.isNotBlank()) return value
            if (value is Number) return value.toString()
        }
        return null
    }
}
