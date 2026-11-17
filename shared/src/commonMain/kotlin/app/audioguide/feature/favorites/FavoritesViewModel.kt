package app.audioguide.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.audioguide.catalog.ArtworkNarration
import app.audioguide.catalog.Catalog
import app.audioguide.feature.listening.ListeningController
import app.audioguide.settings.SettingsStore
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

class FavoritesViewModel(private val store: FavoritesStore, private val catalog: Catalog) : ViewModel() {
    /** Favoris déjà vérifiés : un favori dont l'œuvre n'a pas d'image libre n'est pas relu à chaque changement. */
    private val checked = mutableSetOf<Long>()

    init {
        // Les favoris d'avant les miniatures et l'écoute courte n'ont ni image ni version courte : on les refait à partir
        // du pack, s'il est encore installé.
        viewModelScope.launch {
            store.favorites.collect { favorites ->
                val old = favorites.filter { it.kind == FavoriteKind.Artwork && (it.image == null || it.shortBody == null) && checked.add(it.id) }
                old.forEach { favorite ->
                    val museumId = favorite.museumId ?: return@forEach
                    val reference = favorite.reference ?: return@forEach
                    val artwork = catalog.artwork(museumId, reference) ?: return@forEach
                    if (favorite.image == null) artwork.image?.let { store.setImage(favorite.id, it) }
                    if (favorite.shortBody == null) {
                        val museumName = favorite.museumName ?: catalog.museumName(museumId) ?: return@forEach
                        store.setNarration(favorite.id, ArtworkNarration.build(artwork, museumName), ArtworkNarration.buildShort(artwork))
                    }
                }
            }
        }
    }

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
    /** Œuvre dont le pack est installé : la fiche complète est consultable. */
    val canOpenArtwork: Boolean = false,
)

class FavoriteViewModel(
    private val favoriteId: Long,
    private val store: FavoritesStore,
    private val synthesizer: SpeechSynthesizer,
    private val catalog: Catalog,
    settings: SettingsStore,
) : ViewModel() {
    /** Lecture du favori : écoute courte (œuvres) ou complète, pause, saut. */
    val listening = ListeningController(synthesizer, viewModelScope, settings)

    val state: StateFlow<FavoriteUiState> = store.favorites.map { favorites ->
        val favorite = favorites.firstOrNull { it.id == favoriteId }
        FavoriteUiState(
            favorite = favorite,
            canOpenArtwork = favorite?.kind == FavoriteKind.Artwork && favorite.museumId?.let(catalog::museumName) != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoriteUiState())

    init {
        viewModelScope.launch {
            state.collect { current ->
                val favorite = current.favorite ?: return@collect
                listening.configure(favorite.languageTag, short = favorite.shortBody, full = favorite.body)
            }
        }
    }

    fun saveNote(note: String) = store.saveNote(favoriteId, note)

    fun delete() {
        synthesizer.stop()
        viewModelScope.launch { store.remove(favoriteId) }
    }

    override fun onCleared() {
        listening.stop()
    }
}
