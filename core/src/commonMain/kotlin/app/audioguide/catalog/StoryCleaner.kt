package app.audioguide.catalog

/**
 * Nettoie le récit tiré de l'introduction d'un article Wikipédia. Une introduction d'encyclopédie est faite pour
 * être parcourue des yeux : noms dans d'autres langues, dates de vie, renvois vers d'autres wikis. Rien n'est
 * inventé ni reformulé : on ne fait qu'enlever.
 */
object StoryCleaner {
    /** Pour l'affichage : on retire seulement ce qui n'a aucun sens hors de Wikipédia. */
    fun forReading(text: String): String = text.lines()
        .map { line ->
            line.replace(INTERWIKI, "")
                .replace(EMPTY_PARENS, "")
                .let(::tidy)
        }
        .filter { it.isNotBlank() }
        .joinToString("\n")

    /**
     * Pour l'écoute : en plus, toutes les parenthèses (« (en italien : La Gioconda) », « (1791-1824) ») et les
     * phrases qui disent où l'œuvre est exposée, puisqu'on est devant.
     */
    fun forListening(text: String): String = forReading(text).lines()
        .map { line -> sentences(tidy(removeParentheses(line))).filterNot(::isLocationOnly).joinToString(" ") }
        .filter { it.isNotBlank() }
        .joinToString("\n")

    /**
     * Les [count] premières phrases du récit, pour l'écoute courte, en s'arrêtant avant de dépasser [maxChars]
     * (une intro de Wikipédia a parfois des phrases de cinq lignes). La première phrase est toujours gardée.
     */
    fun firstSentences(text: String, count: Int, maxChars: Int = Int.MAX_VALUE): String {
        val kept = mutableListOf<String>()
        var length = 0
        for (sentence in text.lines().filter { it.isNotBlank() }.flatMap(::sentences)) {
            if (kept.size == count || (kept.isNotEmpty() && length + sentence.length > maxChars)) break
            kept += sentence
            length += sentence.length + 1
        }
        return kept.joinToString(" ")
    }

    /** Découpe en phrases, sans couper après une initiale (« J. »). */
    fun sentences(paragraph: String): List<String> =
        SENTENCE_END.split(paragraph.trim()).map { it.trim() }.filter { it.isNotEmpty() }

    private fun removeParentheses(text: String): String {
        // Parenthèses imbriquées : on retire de l'intérieur vers l'extérieur.
        var current = text
        while (true) {
            val next = current.replace(INNER_PARENS, "")
            if (next == current) return next
            current = next
        }
    }

    /** Une phrase courte qui ne fait que situer l'œuvre (« Il est conservé au musée du Louvre à Paris. »). */
    private fun isLocationOnly(sentence: String): Boolean =
        sentence.length <= LOCATION_SENTENCE_MAX && LOCATION.containsMatchIn(sentence)

    private fun tidy(text: String): String = text
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s+([,.;:!?»)])")) { m -> if (m.groupValues[1] in ":;!?»") " " + m.groupValues[1] else m.groupValues[1] }
        .replace(Regex(",\\s*,"), ",")
        .trim()

    /** Renvois vers un article d'un autre wiki : « une icône médiatique (de) au fil… ». */
    private val INTERWIKI = Regex("\\s*\\((?:de|en|it|es|nl|la|pt|ru|ja|zh|el|ar)\\)")
    private val EMPTY_PARENS = Regex("\\s*\\(\\s*[,;]?\\s*\\)")
    private val INNER_PARENS = Regex("\\s*\\([^()]*\\)")
    private val SENTENCE_END = Regex("(?<=[.!?…])\\s+(?=[«A-ZÀÂÉÈÊÎÔÛÇ0-9])(?<!\\b[A-Z]\\.\\s)")
    private val LOCATION = Regex(
        "\\b(?:est|sont|fut|a été|ont été)\\s+(?:aujourd'hui\\s+|actuellement\\s+|désormais\\s+)?(?:conservée?s?|exposée?s?)\\s+(?:au|à|dans)\\b",
        RegexOption.IGNORE_CASE,
    )
    private const val LOCATION_SENTENCE_MAX = 140
}
