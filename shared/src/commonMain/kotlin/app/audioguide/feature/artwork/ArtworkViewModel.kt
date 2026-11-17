package app.audioguide.feature.artwork

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.audioguide.catalog.Artwork
import app.audioguide.catalog.ArtworkNarration
import app.audioguide.catalog.Catalog
import app.audioguide.catalog.PackInfo
import app.audioguide.domain.PlayerStatus
import app.audioguide.domain.ReadingPlan
import app.audioguide.domain.ReadingPlayer
import app.audioguide.domain.SpeechSynthesizer
import app.audioguide.domain.SpeechText
import app.audioguide.domain.VoiceStatus
import app.audioguide.favorites.FavoritesStore
import app.audioguide.packs.PackStore
import app.audioguide.platform.currentTimeMillis
import app.audioguide.settings.SettingsStore
import kotlinx.coroutines.delay
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
    /** Durée estimée des deux écoutes, `null` tant que la fiche n'est pas chargée. */
    val shortMillis: Long? = null,
    val fullMillis: Long? = null,
    val status: PlayerStatus = PlayerStatus.Idle,
    val positionMillis: Long = 0,
    val totalMillis: Long = 0,
) {
    /** Une lecture est en cours ou en pause : on affiche le lecteur plutôt que les deux boutons. */
    val isPlayerActive: Boolean get() = status != PlayerStatus.Idle
}

class ArtworkViewModel(
    private val museumId: String,
    private val reference: String,
    private val catalog: Catalog,
    store: PackStore,
    private val synthesizer: SpeechSynthesizer,
    private val favorites: FavoritesStore,
    private val settings: SettingsStore,
) : ViewModel() {
    private val player = ReadingPlayer(synthesizer, viewModelScope, ::currentTimeMillis)
    private val position = MutableStateFlow(0L)

    /** `null` tant que la recherche dans le pack n'est pas terminée. */
    private val loaded = MutableStateFlow<Loaded?>(null)

    private class Loaded(val artwork: Artwork?)

    val state: StateFlow<ArtworkUiState> = combine(
        combine(loaded, store.installed, ::Pair),
        combine(synthesizer.isSpeaking, synthesizer.engineReady, favorites.favorites, ::Triple),
        combine(player.state, position, settings.settings, ::Triple),
    ) { (loaded, packs), (speaking, _, saved), (playing, positionMillis, prefs) ->
        val artwork = loaded?.artwork
        val museumName = packs.firstOrNull { it.museumId == museumId }?.museumName
        ArtworkUiState(
            loading = loaded == null,
            artwork = artwork,
            pack = packs.firstOrNull { it.museumId == museumId },
            isSpeaking = speaking,
            voiceReady = synthesizer.voiceStatus(ArtworkNarration.LANGUAGE_TAG) == VoiceStatus.Ready,
            isFavorite = saved.any { it.museumId == museumId && it.reference == reference },
            shortMillis = artwork?.let { plan(it, museumName, full = false, prefs.speechRate).totalMillis },
            fullMillis = artwork?.takeIf { museumName != null }?.let { plan(it, museumName, full = true, prefs.speechRate).totalMillis },
            status = playing.status,
            positionMillis = if (playing.status == PlayerStatus.Idle) 0 else positionMillis,
            totalMillis = playing.totalMillis,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtworkUiState())

    init {
        viewModelScope.launch { loaded.value = Loaded(catalog.artwork(museumId, reference)) }
        // La position avance en continu pendant la lecture : on la rafraîchit quatre fois par seconde.
        viewModelScope.launch {
            while (true) {
                delay(POSITION_TICK_MILLIS)
                if (player.state.value.status != PlayerStatus.Idle) position.value = player.positionMillis()
            }
        }
    }

    private fun plan(artwork: Artwork, museumName: String?, full: Boolean, rate: Float): ReadingPlan {
        val text = if (full) ArtworkNarration.build(artwork, museumName.orEmpty()) else ArtworkNarration.buildShort(artwork)
        return ReadingPlan.build(SpeechText.prepare(text), rate)
    }

    /** [full] : tout le récit et la fiche ; sinon l'écoute courte, celle qu'on lance devant l'œuvre. */
    fun speak(full: Boolean = false) {
        val current = state.value
        val artwork = current.artwork ?: return
        val museumName = current.pack?.museumName ?: return
        player.load(plan(artwork, museumName, full, settings.settings.value.speechRate), ArtworkNarration.LANGUAGE_TAG)
        position.value = 0
        player.play()
    }

    fun pause() = player.pause()

    fun resume() = player.resume()

    /** Saute à [millis] depuis le début de la lecture. */
    fun seekTo(millis: Long) {
        player.seekTo(millis)
        position.value = player.positionMillis()
    }

    fun skip(deltaMillis: Long) {
        player.skip(deltaMillis)
        position.value = player.positionMillis()
    }

    fun toggleFavorite() {
        val current = state.value
        val artwork = current.artwork ?: return
        val museumName = current.pack?.museumName ?: return
        viewModelScope.launch { favorites.toggleArtwork(artwork, museumName) }
    }

    fun stop() = player.stop()

    override fun onCleared() {
        player.stop()
    }

    private companion object {
        const val POSITION_TICK_MILLIS = 250L
    }
}
