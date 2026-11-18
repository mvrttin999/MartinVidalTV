package com.martinvidal.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinvidal.tv.data.repo.CatalogRepository
import com.martinvidal.tv.data.repo.DemoCatalog
import com.martinvidal.tv.data.repo.PlaylistRepository
import com.martinvidal.tv.data.session.SettingsStore
import com.martinvidal.tv.ui.home.CardItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchState(
    val loading: Boolean = false,
    val results: List<CardItem> = emptyList(),
    val searched: Boolean = false,
    val demo: Boolean = false,
    val notice: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val playlist: PlaylistRepository,
    private val session: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    // PENDIENTE(P18): paginar resultados y anadir busqueda por persona/genero
    // (blSearchByContent, getItemByPerson) cuando el portal este vivo.
    fun search(query: String) {
        val value = query.trim()
        if (value.isEmpty() || _state.value.loading) return
        _state.value = SearchState(loading = true)
        viewModelScope.launch {
            val snapshot = session.snapshot()

            if (playlist.available()) {
                val items = runCatching { playlist.search(value) }.getOrNull()
                _state.value = SearchState(
                    results = items.orEmpty(),
                    searched = true,
                    demo = false,
                    notice = if (items == null) "No se pudo leer la lista M3U." else null
                )
                return@launch
            }

            if (snapshot.host.isBlank()) {
                publishDemo(
                    value,
                    "Sin portal ni lista M3U: se busca en el catálogo de ejemplo."
                )
                return@launch
            }
            /* `search` es suspend y lanza excepcion en vez de devolver Result:
               se envuelve para poder decidir el fallback del catalogo demo. */
            runCatching { catalog.search(value) }.fold(
                onSuccess = { items ->
                    _state.value = SearchState(
                        results = items,
                        searched = true,
                        demo = false
                    )
                },
                onFailure = {
                    publishDemo(
                        value,
                        "El portal no respondió. Se muestra el catálogo de ejemplo."
                    )
                }
            )
        }
    }

    private fun publishDemo(query: String, notice: String) {
        _state.value = SearchState(
            results = DemoCatalog.search(query),
            searched = true,
            demo = true,
            notice = notice
        )
    }
}
