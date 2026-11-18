package app.audioguide.favorites

import app.audioguide.catalog.Artwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Les favoris de l'utilisateur. La base s'ouvre à la première utilisation, sans bloquer le démarrage :
 * [favorites] est vide jusqu'à ce qu'elle soit chargée.
 */
class FavoritesStore(path: String, now: () -> Long, private val scope: CoroutineScope) {
    /**
     * `null` si la base ne peut pas s'ouvrir, par exemple écrite par une version plus récente de l'app après un retour
     * en arrière : les favoris sont alors vides et les ajouts sans effet, au lieu de faire planter l'app. La base n'est
     * jamais modifiée ni supprimée : elle reprend dès qu'une version assez récente l'ouvre.
     */
    private val repository = scope.async(start = CoroutineStart.LAZY) { runCatching { FavoritesRepository.open(path, now) }.getOrNull() }

    val favorites: StateFlow<List<Favorite>> =
        flow { repository.await()?.let { emitAll(it.favorites) } ?: emit(emptyList()) }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun isArtworkFavorite(museumId: String, reference: String): Boolean =
        favorites.value.any { it.museumId == museumId && it.reference == reference }

    suspend fun addArtwork(artwork: Artwork, museumName: String): Favorite? =
        repository.await()?.addArtwork(artwork, museumName)

    suspend fun addCartel(title: String, body: String, languageTag: String): Favorite? =
        repository.await()?.addCartel(title, body, languageTag)

    suspend fun toggleArtwork(artwork: Artwork, museumName: String) {
        if (isArtworkFavorite(artwork.museumId, artwork.reference)) {
            repository.await()?.removeArtwork(artwork.museumId, artwork.reference)
        } else {
            repository.await()?.addArtwork(artwork, museumName)
        }
    }

    suspend fun remove(id: Long) { repository.await()?.remove(id) }

    suspend fun setNote(id: Long, note: String?) { repository.await()?.setNote(id, note) }

    /** Enregistre une note sans attendre, et même si l'écran qui l'a saisie est déjà fermé. */
    fun saveNote(id: Long, note: String?) {
        scope.launch { repository.await()?.setNote(id, note) }
    }
}
