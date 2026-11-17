package app.audioguide.catalog

import app.audioguide.domain.CartelCleaner

/** Texte lu à voix haute pour la fiche d'une œuvre. Les données sources sont en français : la lecture aussi. */
object ArtworkNarration {
    const val LANGUAGE_TAG = "fr-FR"

    /** Nombre de phrases du récit dans l'écoute courte. */
    const val SHORT_STORY_SENTENCES = 3

    /** Environ trente secondes de lecture. */
    const val SHORT_STORY_MAX_CHARS = 450

    /**
     * Écoute courte, celle qu'on lance devant l'œuvre : le début du récit seulement. Le titre et l'artiste sont déjà
     * sur la fiche et sur le cartel, les redire est une répétition. Sans récit, il n'y a rien d'autre à dire : on lit
     * alors le titre, l'artiste, la date et la technique.
     */
    fun buildShort(artwork: Artwork): String {
        val parts = mutableListOf<String>()
        val intro = artwork.story?.let {
            StoryCleaner.firstSentences(StoryCleaner.forListening(it.text), SHORT_STORY_SENTENCES, SHORT_STORY_MAX_CHARS)
        }.orEmpty()
        if (intro.isNotBlank()) {
            parts += sentence(intro)
            parts += sentence(SHORT_ATTRIBUTION)
        } else {
            parts.addAll(heading(artwork))
            artwork.technique?.let { parts += sentence(it.replaceFirstChar(Char::uppercaseChar)) }
        }
        return CartelCleaner.clean(parts.joinToString("\n"))
    }

    /**
     * Écoute complète : le récit en entier, puis la fiche. L'historique (chronologie illisible à voix haute) reste à
     * l'écrit. Comme dans l'écoute courte, titre et artiste ne sont lus que s'il n'y a pas de récit.
     */
    fun build(artwork: Artwork, museumName: String): String {
        val parts = mutableListOf<String>()
        val story = artwork.story?.let { StoryCleaner.forListening(it.text) }.orEmpty()
        if (story.isNotBlank()) {
            parts += story.lines().filter { it.isNotBlank() }.map(::sentence)
            parts += sentence(storyAttribution(artwork.story!!))
        } else {
            parts.addAll(heading(artwork))
        }
        artwork.school?.let { parts += sentence("École : $it") }
        artwork.technique?.let { parts += sentence("Technique : $it") }
        dimensions(artwork.heightCm, artwork.widthCm)?.let { parts += sentence(it) }
        parts += sentence(
            "Conservé au $museumName" + (artwork.location?.let { ", $it" } ?: ""),
        )
        // Le nettoyeur du cartel développe siècles et abréviations ; il n'y a pas de numéro d'inventaire à lire.
        return CartelCleaner.clean(parts.joinToString("\n"))
    }

    /**
     * Titre parlé : celui de l'article Wikipédia quand il est plus court que le titre Joconde (« La Joconde » plutôt
     * que « Portrait de Lisa Gherardini, épouse de Francesco del Giocondo, dit… »).
     */
    fun spokenTitle(artwork: Artwork): String {
        val article = artwork.story?.articleTitle?.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "")?.trim()
        return if (!article.isNullOrBlank() && article.length < artwork.title.length) article else artwork.title
    }

    private fun heading(artwork: Artwork): List<String> = listOfNotNull(
        sentence(spokenTitle(artwork)),
        artwork.artist?.let { sentence("Par $it") },
        artwork.dateText?.let { sentence(it) },
    )

    private const val SHORT_ATTRIBUTION = "Source : Wikipédia"

    /** Mention de la source du récit, lue après lui (CC BY-SA impose l'attribution). */
    fun storyAttribution(story: ArtworkStory): String = "Récit d'après l'article Wikipédia « ${story.articleTitle} »"

    fun dimensions(heightCm: Double?, widthCm: Double?): String? = when {
        heightCm != null && widthCm != null -> "Dimensions : ${cm(heightCm)} centimètres de haut sur ${cm(widthCm)} de large"
        heightCm != null -> "Hauteur : ${cm(heightCm)} centimètres"
        widthCm != null -> "Largeur : ${cm(widthCm)} centimètres"
        else -> null
    }

    private fun cm(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().replace('.', ',')

    private fun sentence(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.last() in ".!?") trimmed else "$trimmed."
    }
}
