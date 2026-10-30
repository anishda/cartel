package app.audioguide.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.audioguide.catalog.Artwork
import app.audioguide.catalog.Catalog
import app.audioguide.catalog.PackInfo
import app.audioguide.packs.PackStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(
    val query: String = "",
    /** `null` : tous les musées. */
    val museumId: String? = null,
    val packs: List<PackInfo> = emptyList(),
    val results: List<Artwork> = emptyList(),
    /** Vrai tant que la recherche de la requête actuelle n'est pas terminée. */
    val searching: Boolean = false,
) {
    val museumNames: Map<String, String> get() = packs.associate { it.museumId to it.museumName }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val catalog: Catalog,
    store: PackStore,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val museum = MutableStateFlow<String?>(null)

    val state: StateFlow<SearchUiState> = combine(query, museum, store.installed, ::Triple)
        .flatMapLatest { (q, selected, packs) ->
            // Un musée supprimé depuis le dernier choix de filtre n'existe plus.
            val museumId = selected?.takeIf { id -> packs.any { it.museumId == id } }
            val base = SearchUiState(query = q, museumId = museumId, packs = packs)
            flow {
                if (q.isBlank()) {
                    emit(base)
                } else {
                    emit(base.copy(searching = true))
                    delay(DEBOUNCE_MILLIS)
                    emit(base.copy(results = catalog.search(q, museumId)))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onMuseumSelected(museumId: String?) {
        museum.value = museumId
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 200L
    }
}
