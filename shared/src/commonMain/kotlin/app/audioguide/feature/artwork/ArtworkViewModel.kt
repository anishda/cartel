package app.audioguide.feature.artwork

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.audioguide.catalog.Artwork
import app.audioguide.catalog.ArtworkNarration
import app.audioguide.catalog.Catalog
import app.audioguide.catalog.PackInfo
import app.audioguide.domain.SpeechSynthesizer
import app.audioguide.favorites.FavoritesStore
import app.audioguide.feature.listening.ListeningController
import app.audioguide.packs.PackStore
import app.audioguide.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ArtworkUiState(
    val loading: Boolean = true,
    val artwork: Artwork? = null,
    val pack: PackInfo? = null,
    val isFavorite: Boolean = false,
)

class ArtworkViewModel(
    private val museumId: String,
    private val reference: String,
    private val catalog: Catalog,
    store: PackStore,
    private val synthesizer: SpeechSynthesizer,
    private val favorites: FavoritesStore,
    private val settings: SettingsStore,
) : ViewModel() {
    /** Lecture de la fiche : écoute courte ou complète, pause, saut. */
    val listening = ListeningController(synthesizer, viewModelScope, settings)

    /** `null` tant que la recherche dans le pack n'est pas terminée. */
    private val loaded = MutableStateFlow<Loaded?>(null)

    private class Loaded(val artwork: Artwork?)

    val state: StateFlow<ArtworkUiState> = combine(
        loaded, store.installed, favorites.favorites,
    ) { loaded, packs, saved ->
        ArtworkUiState(
            loading = loaded == null,
            artwork = loaded?.artwork,
            pack = packs.firstOrNull { it.museumId == museumId },
            isFavorite = saved.any { it.museumId == museumId && it.reference == reference },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtworkUiState())

    init {
        viewModelScope.launch { loaded.value = Loaded(catalog.artwork(museumId, reference)) }
        // Les textes à lire dépendent de la fiche et du nom du musée : on les donne dès qu'on les a.
        viewModelScope.launch {
            state.collect { current ->
                val artwork = current.artwork ?: return@collect
                val museumName = current.pack?.museumName ?: return@collect
                listening.configure(
                    ArtworkNarration.LANGUAGE_TAG,
                    short = ArtworkNarration.buildShort(artwork),
                    full = ArtworkNarration.build(artwork, museumName),
                )
            }
        }
    }

    fun toggleFavorite() {
        val current = state.value
        val artwork = current.artwork ?: return
        val museumName = current.pack?.museumName ?: return
        viewModelScope.launch { favorites.toggleArtwork(artwork, museumName) }
    }

    override fun onCleared() {
        listening.stop()
    }
}
