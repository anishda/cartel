package app.audioguide.catalog

import app.audioguide.domain.InventoryNumbers
import app.audioguide.packs.PackStore

/** Un cartel rapproché d'une œuvre du catalogue. [score] : 100 et plus = quasi certain. */
data class ArtworkMatch(val artwork: Artwork, val score: Int)

/** Recherche à travers tous les packs installés. */
class Catalog(private val store: PackStore) {

    /** Œuvres dont le titre, l'artiste ou l'inventaire contient les mots de [query], meilleures d'abord. */
    suspend fun search(query: String, museumId: String? = null, limit: Int = 50): List<Artwork> =
        store.packs()
            .filter { museumId == null || it.info.museumId == museumId }
            .flatMap { it.search(query, limit) }
            .sortedWith(compareBy<SearchHit> { it.rank }.thenBy { normalizeForSearch(it.artwork.title) })
            .take(limit)
            .map { it.artwork }

    /** Nom du musée de [museumId] s'il est installé. */
    fun museumName(museumId: String): String? =
        store.installed.value.firstOrNull { it.museumId == museumId }?.museumName

    suspend fun artwork(museumId: String, reference: String): Artwork? =
        store.pack(museumId)?.byReference(reference)

    /**
     * Retrouve l'œuvre décrite par le texte brut d'un cartel : le numéro d'inventaire pèse le plus,
     * puis le titre (égal ou débutant pareil) qu'un nom d'artiste présent dans le texte confirme.
     */
    suspend fun match(rawCartelText: String, limit: Int = 3): List<ArtworkMatch> {
        val packs = store.packs()
        if (packs.isEmpty()) return emptyList()

        val scores = linkedMapOf<Pair<String, Long>, Pair<Artwork, Int>>()
        fun add(artwork: Artwork, points: Int) {
            val key = artwork.museumId to artwork.id
            val current = scores[key]
            scores[key] = artwork to ((current?.second ?: 0) + points)
        }

        val inventories = InventoryNumbers.find(rawCartelText).map(::normalizeInventory).distinct()
        val titles = candidateTitles(rawCartelText)
        val textNorm = " ${normalizeForSearch(rawCartelText)} "

        for (pack in packs) {
            inventories.forEach { inv -> pack.byInventory(inv).forEach { add(it, 100) } }
            for (title in titles) {
                pack.byTitle(title).forEach { add(it, 60) }
                // « La Joconde » sur le cartel, « Portrait de Lisa Gherardini… dit La Joconde » dans le catalogue.
                if (title.length >= 8) pack.byTitle(title, TitleMatch.Prefix).forEach { add(it, 20) }
                if (title.length >= 6) pack.byTitle(title, TitleMatch.Contains).forEach { add(it, 35) }
            }
        }

        // Un nom d'artiste lu sur le cartel confirme une œuvre déjà candidate.
        val ranked = scores.values.map { (artwork, points) ->
            val surname = artwork.artist?.let(::normalizeForSearch)?.substringAfterLast(' ')
            val boost = if (!surname.isNullOrEmpty() && surname.length > 2 && textNorm.contains(" $surname ")) 30 else 0
            ArtworkMatch(artwork, points + boost)
        }
        return ranked.filter { it.score >= MIN_SCORE }.sortedByDescending { it.score }.take(limit)
    }

    /** Lignes du début du cartel qui ressemblent à un titre, sous leur forme de recherche. */
    private fun candidateTitles(raw: String): List<String> =
        raw.lines()
            .map { it.trim().trim('"', '«', '»', '“', '”') }
            .filter { it.length in 3..120 && !InventoryNumbers.wholeLine.matches(it) && it.any(Char::isLetter) }
            .take(6)
            .map(::normalizeForSearch)
            .filter { it.isNotEmpty() }
            .distinct()

    private companion object {
        const val MIN_SCORE = 60
    }
}
