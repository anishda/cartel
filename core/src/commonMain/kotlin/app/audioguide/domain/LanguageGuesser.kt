package app.audioguide.domain

/**
 * Devine la langue d'un cartel d'après ses mots courants. Rudimentaire mais suffisant pour choisir la voix :
 * l'utilisateur peut toujours corriger.
 */
object LanguageGuesser {
    private val stopwords: Map<String, Set<String>> = mapOf(
        "fr-FR" to setOf("le", "la", "les", "un", "une", "des", "du", "de", "et", "est", "dans", "pour", "par", "sur", "avec", "au", "aux", "qui", "que", "son", "sa", "ses", "il", "elle", "en", "ce", "cette"),
        "en-US" to setOf("the", "and", "of", "in", "to", "is", "was", "for", "with", "by", "on", "at", "his", "her", "its", "from", "this", "that", "an", "as", "he", "she", "it"),
        "es-ES" to setOf("el", "la", "los", "las", "un", "una", "y", "de", "del", "en", "es", "por", "con", "para", "su", "sus", "que", "se", "al", "lo", "como"),
        "it-IT" to setOf("il", "lo", "la", "gli", "le", "un", "una", "e", "di", "del", "della", "in", "per", "con", "su", "che", "è", "al", "dei", "nel", "sua", "suo"),
        "de-DE" to setOf("der", "die", "das", "und", "ist", "von", "zu", "im", "den", "dem", "des", "ein", "eine", "mit", "auf", "für", "sich", "nicht", "als", "auch", "wurde"),
        "nl-NL" to setOf("de", "het", "een", "en", "van", "in", "is", "op", "te", "met", "voor", "dat", "die", "zijn", "werd", "aan", "door", "als"),
    )

    private val words = Regex("""[\p{L}']+""")

    /** Nombre de mots courants reconnus pour chaque langue dans [text]. */
    internal fun scores(text: String): Map<String, Int> {
        val tokens = words.findAll(text.lowercase()).map { it.value.trim('\'') }.toList()
        return stopwords.mapValues { (_, set) -> tokens.count { it in set } }
    }

    /** Retourne l'étiquette BCP 47 de la langue la plus probable, ou `null` si le texte ne tranche pas. */
    fun guess(text: String): String? {
        val tokenCount = words.findAll(text).count()
        if (tokenCount < 4) return null
        val ranked = scores(text).entries.sortedByDescending { it.value }
        val best = ranked[0]
        val second = ranked[1]
        // Assez de mots reconnus, et nettement devant la deuxième langue.
        return if (best.value >= 2 && best.value > second.value * 1.3) best.key else null
    }
}
