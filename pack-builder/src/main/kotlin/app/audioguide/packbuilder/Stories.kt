package app.audioguide.packbuilder

import app.audioguide.catalog.ArtworkDraft
import app.audioguide.catalog.ArtworkStory
import app.audioguide.catalog.normalizeForSearch
import app.audioguide.catalog.normalizeInventory
import java.net.URLEncoder

/**
 * Relie une œuvre de Joconde à son élément Wikidata. Le numéro d'inventaire ne suffit pas : un même numéro
 * désigne parfois deux objets de départements différents (RF 644 : Olympia, et un buste). On exige donc
 * en plus un nom d'artiste commun, ou un titre proche.
 */
object WorkMatcher {
    fun match(draft: ArtworkDraft, candidates: List<WikidataWork>): WikidataWork? {
        val confirmed = candidates.filter { confirms(draft, it) }
        // Deux éléments confirmés pour une même œuvre : doublon Wikidata, on prend le plus connu.
        return confirmed.maxByOrNull { it.sitelinks }
    }

    private fun confirms(draft: ArtworkDraft, work: WikidataWork): Boolean {
        val artist = significant(draft.artist.orEmpty())
        if (artist.isNotEmpty() && work.creators.any { (significant(it) intersect artist).isNotEmpty() }) return true

        val title = significant(draft.title)
        val label = significant(work.label.orEmpty())
        if (title.isEmpty() || label.isEmpty()) return false
        val shared = (title intersect label).size
        return shared > 0 && shared * 2 >= minOf(title.size, label.size)
    }

    /** Mots porteurs de sens : sans accents, quatre lettres au moins, hors mots outils courants. */
    private fun significant(text: String): Set<String> =
        normalizeForSearch(text).split(' ').filter { it.length >= 4 && it !in STOP_WORDS }.toSet()

    private val STOP_WORDS = setOf(
        "dans", "avec", "pour", "sous", "dite", "dit", "entre", "vers", "leur", "leurs", "elle", "sont",
        "the", "with", "from", "maitre", "anonyme", "inconnu", "atelier", "ecole", "attribue", "portrait",
    )
}

/** Met en forme l'introduction d'un article pour la lecture : paragraphes non vides, longueur bornée. */
object StoryFormatter {
    const val MAX_CHARS = 1_800

    fun format(intro: String): String? {
        val paragraphs = intro.lines()
            // Transcriptions phonétiques « [la dʒoˈkonda] » : illisibles par une voix.
            .map { it.replace(Regex("\\s*\\[[^\\]]*]"), "") }
            // Les modèles d'API laissent parfois des parenthèses vides : « Olympia () est… ».
            .map { it.replace(Regex("\\s*\\(\\s*[,;]?\\s*\\)"), "") }
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.length >= 40 }
        if (paragraphs.isEmpty()) return null

        val kept = mutableListOf<String>()
        var length = 0
        for (paragraph in paragraphs) {
            if (length + paragraph.length <= MAX_CHARS) {
                kept += paragraph
                length += paragraph.length
                continue
            }
            if (kept.isEmpty()) kept += cutAtSentence(paragraph, MAX_CHARS)
            break
        }
        return kept.joinToString("\n")
    }

    private fun cutAtSentence(text: String, max: Int): String {
        val head = text.take(max)
        val end = Regex("[.!?](?=\\s)").findAll(head).lastOrNull()?.range?.last
        return if (end != null && end > max / 3) head.substring(0, end + 1) else head.substringBeforeLast(' ') + "…"
    }
}

/** Ajoute à chaque œuvre son lien Wikidata, sa notoriété et, s'il existe, le début de son article Wikipedia. */
class StoryEnricher(private val source: WikimediaSource, private val log: (String) -> Unit = {}) {
    data class Result(val drafts: List<ArtworkDraft>, val linked: Int, val withStory: Int)

    fun enrich(museum: MuseumSpec, drafts: List<ArtworkDraft>): Result {
        val works = source.works(museum).groupBy { normalizeInventory(it.inventoryNo) }
        log("  Wikidata : ${works.values.sumOf { it.size }} œuvres avec inventaire")

        val matches = drafts.associateWith { WorkMatcher.match(it, works[normalizeInventory(it.inventoryNo)].orEmpty()) }
        val titles = matches.values.mapNotNull { it?.frwikiTitle }
        val intros = source.intros(titles)

        var linked = 0
        var withStory = 0
        val enriched = drafts.map { draft ->
            val work = matches[draft] ?: return@map draft
            linked++
            val story = work.frwikiTitle?.let { title ->
                intros[title]?.let(StoryFormatter::format)?.let { ArtworkStory(it, title, articleUrl(title)) }
            }
            if (story != null) withStory++
            draft.copy(wikidataId = work.id, notability = work.sitelinks, story = story)
        }
        return Result(enriched, linked, withStory)
    }

    companion object {
        const val LICENSE = "CC BY-SA 4.0"

        fun articleUrl(title: String): String =
            "https://fr.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), Charsets.UTF_8).replace("+", "_")
    }
}
