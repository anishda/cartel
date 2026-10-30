package app.audioguide.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.audioguide.catalog.Catalog
import app.audioguide.domain.SpeechSynthesizer
import app.audioguide.domain.SpeechText
import app.audioguide.domain.VoiceStatus
import app.audioguide.favorites.Favorite
import app.audioguide.favorites.FavoriteKind
import app.audioguide.favorites.FavoritesStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Les favoris d'un même musée ; [museumName] est `null` pour les cartels sans œuvre connue. */
data class FavoriteGroup(val museumName: String?, val items: List<Favorite>)

class FavoritesViewModel(private val store: FavoritesStore) : ViewModel() {
    val groups: StateFlow<List<FavoriteGroup>> = store.favorites.map { favorites ->
        val byMuseum = favorites.filter { it.museumName != null }
            .groupBy { it.museumName }
            .map { (name, items) -> FavoriteGroup(name, items) }
            .sortedBy { it.museumName?.lowercase() }
        val cartels = favorites.filter { it.museumName == null }
        byMuseum + listOfNotNull(cartels.takeIf { it.isNotEmpty() }?.let { FavoriteGroup(null, it) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<Favorite>> = store.favorites
}

data class FavoriteUiState(
    /** `null` si le favori n'existe plus (supprimé). */
    val favorite: Favorite? = null,
    val isSpeaking: Boolean = false,
    val voiceReady: Boolean = false,
    /** Œuvre dont le pack est installé : la fiche complète est consultable. */
    val canOpenArtwork: Boolean = false,
)

class FavoriteViewModel(
    private val favoriteId: Long,
    private val store: FavoritesStore,
    private val synthesizer: SpeechSynthesizer,
    private val catalog: Catalog,
) : ViewModel() {
    val state: StateFlow<FavoriteUiState> = combine(
        store.favorites, synthesizer.isSpeaking, synthesizer.engineReady,
    ) { favorites, speaking, _ ->
        val favorite = favorites.firstOrNull { it.id == favoriteId }
        FavoriteUiState(
            favorite = favorite,
            isSpeaking = speaking,
            voiceReady = favorite != null && synthesizer.voiceStatus(favorite.languageTag) == VoiceStatus.Ready,
            canOpenArtwork = favorite?.kind == FavoriteKind.Artwork && favorite.museumId?.let(catalog::museumName) != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoriteUiState())

    fun speak() {
        val favorite = state.value.favorite ?: return
        synthesizer.speak(SpeechText.prepare(favorite.body), favorite.languageTag)
    }

    fun stop() = synthesizer.stop()

    fun saveNote(note: String) = store.saveNote(favoriteId, note)

    fun delete() {
        synthesizer.stop()
        viewModelScope.launch { store.remove(favoriteId) }
    }

    override fun onCleared() {
        synthesizer.stop()
    }
}
