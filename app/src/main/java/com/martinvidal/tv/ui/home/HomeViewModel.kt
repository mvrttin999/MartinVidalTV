package com.martinvidal.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinvidal.tv.data.repo.CatalogRepository
import com.martinvidal.tv.data.repo.DemoCatalog
import com.martinvidal.tv.data.repo.PlaylistRepository
import com.martinvidal.tv.data.session.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val loading: Boolean = true,
    val sections: List<Section> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
    val error: String? = null,
    val demo: Boolean = false,
    /** `true` cuando las estanterias vienen de una lista M3U. */
    val playlist: Boolean = false,
    /** `true` si esa lista es la que viene incluida en la app. */
    val bundled: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val playlist: PlaylistRepository,
    private val session: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private var loading = false
    private var loadedSource = ""

    // PENDIENTE(P18): paginar la portada (getNextColumns, pageNum/pageSize)
    // y refrescar al volver de segundo plano. Hoy se pide una sola pagina.
    fun start(sections: List<Section>) {
        _state.value = _state.value.copy(sections = sections)
        load()
    }

    /**
     * Orden de preferencia: lista M3U propia (contenido real sin portal),
     * catalogo del portal si se configuró un host y, si no, catálogo de ejemplo.
     */
    fun load() {
        if (loading) return
        loading = true
        _state.value = _state.value.copy(loading = true, error = null)

        viewModelScope.launch {
            try {
                val snapshot = session.snapshot()
                loadedSource = sourceKey(snapshot)

                if (playlist.available()) {
                    val shelves = runCatching { playlist.shelves() }.getOrNull()
                    if (!shelves.isNullOrEmpty()) {
                        _state.value = _state.value.copy(
                            loading = false,
                            shelves = shelves,
                            error = null,
                            demo = false,
                            playlist = true,
                            bundled = playlist.usedBundled
                        )
                        return@launch
                    }
                }

                if (snapshot.host.isBlank()) {
                    _state.value = _state.value.copy(
                        loading = false,
                        shelves = DemoCatalog.shelves,
                        error = null,
                        demo = true,
                        playlist = false
                    )
                    return@launch
                }

                runCatching { catalog.home() }
                    .onSuccess { shelves ->
                        _state.value = _state.value.copy(
                            loading = false,
                            shelves = shelves,
                            error = null,
                            demo = false,
                            playlist = false
                        )
                    }
                    .onFailure { error ->
                        _state.value = _state.value.copy(
                            loading = false,
                            shelves = DemoCatalog.shelves,
                            error = error.message ?: "No se pudo conectar con el portal",
                            demo = true,
                            playlist = false
                        )
                    }
            } finally {
                loading = false
            }
        }
    }

    /**
     * La portada se recarga cuando cambio la fuente de contenido: al volver de
     * Ajustes. Si la fuente es la misma se
     * conserva el foco y la posicion de las estanterias.
     */
    fun refreshIfSourceChanged() {
        viewModelScope.launch {
            val snapshot = session.snapshot()
            if (sourceKey(snapshot) != loadedSource) load()
        }
    }

    private fun sourceKey(snapshot: SettingsStore.Snapshot): String =
        listOf(snapshot.host, snapshot.portalCode, snapshot.appId, snapshot.playlistUrl)
            .joinToString("|")
}
