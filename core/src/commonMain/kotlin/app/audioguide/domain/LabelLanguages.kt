package app.audioguide.domain

/** Le texte d'un cartel dans une des langues qu'il contient. */
data class LabelVersion(val languageTag: String, val text: String)

/**
 * Sépare un cartel bilingue (français et anglais, par exemple) en une version par langue,
 * quelle que soit la mise en page : deux paragraphes l'un sous l'autre, ou deux colonnes que l'OCR lit
 * ligne à ligne en alternant. Les lignes qu'on ne sait pas attribuer (titre, artiste, date, technique)
 * restent dans toutes les versions.
 */
object LabelLanguages {
    /** Mots minimum, dans une langue, pour la compter. */
    private const val MIN_WORDS = 8

    /** Une langue secondaire doit peser au moins ce rapport de la principale : une traduction a un volume voisin. */
    private const val MIN_RATIO = 0.35

    private val wordPattern = Regex("""[\p{L}']+""")

    /** Une version par langue si le cartel est bilingue, sinon une liste vide. */
    fun split(text: String): List<LabelVersion> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 4) return emptyList()

        val classes = lines.map(::classify)
        val words = mutableMapOf<String, Int>()
        lines.forEachIndexed { i, line ->
            classes[i]?.let { words[it] = (words[it] ?: 0) + wordPattern.findAll(line).count() }
        }

        val ranked = words.filterValues { it >= MIN_WORDS }.entries.sortedByDescending { it.value }
        if (ranked.size < 2) return emptyList()
        val main = ranked.first().value
        val kept = ranked.filter { it.value >= main * MIN_RATIO }.map { it.key }.toSet()
        if (kept.size < 2) return emptyList()

        // Ordre d'apparition dans le cartel : la langue du texte qui commence, en premier.
        val order = classes.filterNotNull().filter { it in kept }.distinct()
        return order.map { tag ->
            val own = lines.filterIndexed { i, _ -> classes[i] == tag || classes[i] == null || classes[i] !in kept }
            LabelVersion(tag, own.joinToString("\n"))
        }
    }

    /**
     * Cartel en colonnes : on devine la langue de chaque colonne entière (bien plus fiable que ligne par ligne,
     * et valable même si l'OCR n'a lu qu'une partie d'une colonne). Une version par langue distincte ; les titres
     * figurent dans toutes. Liste vide si les colonnes sont dans la même langue.
     */
    fun splitColumns(header: String, columns: List<String>): List<LabelVersion> {
        if (columns.size < 2) return emptyList()
        val tags = columns.map { LanguageGuesser.guess(it) }
        val distinct = tags.filterNotNull().distinct()
        if (distinct.size < 2) return emptyList()
        return distinct.map { tag ->
            val own = columns.filterIndexed { i, _ -> tags[i] == tag || tags[i] == null }
            LabelVersion(tag, (listOf(header) + own).filter { it.isNotBlank() }.joinToString("\n\n"))
        }
    }

    /** Langue d'une ligne, ou `null` si trop courte ou ambiguë. */
    private fun classify(line: String): String? {
        if (wordPattern.findAll(line).count() < 3) return null
        val ranked = LanguageGuesser.scores(line).entries.sortedByDescending { it.value }
        val best = ranked[0]
        return if (best.value >= 1 && best.value > ranked[1].value) best.key else null
    }
}
