package app.audioguide.domain

/**
 * Prépare un texte pour la lecture vocale. Les moteurs vocaux lisent à plat ce qu'on leur donne : sans ponctuation
 * un titre se fond dans la phrase suivante, et un mot en capitales est parfois épelé. On rend le texte plus facile
 * à dire ; les synthétiseurs marquent ensuite une pause entre les paragraphes (séparés par une ligne vide).
 */
object SpeechText {
    private val allCaps = Regex("""\p{Lu}{4,}(?:[-'’]\p{Lu}+)*""")
    private val roman = Regex("""[IVXLCDM]+""")
    private val paragraphBreak = Regex("""\n[ \t]*\n+""")

    /**
     * [languageTag] : langue du texte (`fr-FR`…). En français, les nombres sont écrits en toutes lettres
     * ([FrenchNumbers]) ; sans langue, ou dans une autre, le texte garde ses chiffres. En français et en anglais, les
     * siècles et les numéros de souverains en chiffres romains sont dits en lettres ([RomanNumerals]).
     */
    fun prepare(text: String, languageTag: String? = null): String {
        val french = languageTag?.startsWith("fr", ignoreCase = true) == true
        val english = languageTag?.startsWith("en", ignoreCase = true) == true
        fun spoken(paragraph: String) = when {
            french -> FrenchNumbers.spellNumbersIn(RomanNumerals.spellFrench(paragraph))
            english -> RomanNumerals.spellEnglish(paragraph)
            else -> paragraph
        }
        return text.split(paragraphBreak)
            .map { paragraph -> paragraph.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ") }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n") { endWithPunctuation(softenCapitals(spoken(it))) }
    }

    private val shortCaps = Regex("""\b\p{Lu}{2,3}\b""")

    /** Les paragraphes à lire un par un, avec une pause entre deux. */
    fun paragraphs(prepared: String): List<String> = prepared.split(paragraphBreak).filter { it.isNotBlank() }

    /**
     * « GAUGUIN » devient « Gauguin » ; les chiffres romains (« XIV ») ne changent pas. Dans une phrase presque
     * entièrement en capitales (un titre), les petits mots (« ET ») passent aussi en minuscules ; ailleurs ils restent
     * (sigles : « ADN »).
     */
    private fun softenCapitals(text: String): String {
        val letters = text.filter { it.isLetter() }
        val mostlyCaps = letters.length >= 8 && letters.count { it.isUpperCase() } >= 0.8 * letters.length
        var result = allCaps.replace(text) { match ->
            val word = match.value
            if (roman.matches(word)) word else word.split('-').joinToString("-") { part ->
                part.lowercase().replaceFirstChar { it.uppercaseChar() }
            }
        }
        if (mostlyCaps) result = shortCaps.replace(result) { if (roman.matches(it.value)) it.value else it.value.lowercase() }
        return result
    }

    /** Ajoute un point si le paragraphe ne finit pas déjà par une ponctuation (même suivie d'un guillemet fermant). */
    private fun endWithPunctuation(paragraph: String): String {
        val core = paragraph.trimEnd(')', '»', '"', '”', '’', '\'')
        return if (core.isNotEmpty() && core.last().let { it.isLetterOrDigit() }) "$paragraph." else paragraph
    }
}
