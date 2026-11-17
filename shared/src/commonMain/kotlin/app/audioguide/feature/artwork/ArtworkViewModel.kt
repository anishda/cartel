package app.audioguide.feature.artwork

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.audioguide.catalog.Artwork
import app.audioguide.catalog.ArtworkNarration
import app.audioguide.catalog.Catalog
import app.audioguide.catalog.PackInfo
import app.audioguide.domain.SpeechSynthesizer
import app.audioguide.domain.SpeechText
import app.audioguide.domain.VoiceStatus
import app.audioguide.favorites.FavoritesStore
import app.audioguide.packs.PackStore
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
    val isSpeaking: Boolean = false,
    val voiceReady: Boolean = false,
    val isFavorite: Boolean = false,
)

class ArtworkViewModel(
    private val museumId: String,
    private val reference: String,
    private val catalog: Catalog,
    store: PackStore,
    private val synthesizer: SpeechSynthesizer,
    private val favorites: FavoritesStore,
) : ViewModel() {
    /** `null` tant que la recherche dans le pack n'est pas terminée. */
    private val loaded = MutableStateFlow<Loaded?>(null)

    private class Loaded(val artwork: Artwork?)

    val state: StateFlow<ArtworkUiState> = combine(
        combine(loaded, store.installed, ::Pair),
        combine(synthesizer.isSpeaking, synthesizer.engineReady, favorites.favorites, ::Triple),
    ) { (loaded, packs), (speaking, _, saved) ->
        ArtworkUiState(
            loading = loaded == null,
            artwork = loaded?.artwork,
            pack = packs.firstOrNull { it.museumId == museumId },
            isSpeaking = speaking,
            voiceReady = synthesizer.voiceStatus(ArtworkNarration.LANGUAGE_TAG) == VoiceStatus.Ready,
            isFavorite = saved.any { it.museumId == museumId && it.reference == reference },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtworkUiState())

    init {
        viewModelScope.launch { loaded.value = Loaded(catalog.artwork(museumId, reference)) }
    }

    /** [full] : tout le récit et la fiche ; sinon l'écoute courte, celle qu'on lance devant l'œuvre. */
    fun speak(full: Boolean = false) {
        val current = state.value
        val artwork = current.artwork ?: return
        val museumName = current.pack?.museumName ?: return
        val text = if (full) ArtworkNarration.build(artwork, museumName) else ArtworkNarration.buildShort(artwork)
        synthesizer.speak(SpeechText.prepare(text), ArtworkNarration.LANGUAGE_TAG)
    }

    fun toggleFavorite() {
        val current = state.value
        val artwork = current.artwork ?: return
        val museumName = current.pack?.museumName ?: return
        viewModelScope.launch { favorites.toggleArtwork(artwork, museumName) }
    }

    fun stop() = synthesizer.stop()

    override fun onCleared() {
        synthesizer.stop()
    }
}
