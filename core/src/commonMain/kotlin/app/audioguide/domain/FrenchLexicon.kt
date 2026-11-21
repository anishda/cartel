package app.audioguide.domain

/**
 * Lexique français de la voix neuronale : la prononciation de chaque mot, dans les phonèmes d'espeak-ng avec lesquels
 * les voix Piper françaises ont été entraînées. Le pack-builder le fabrique (voir `LexiconBuilder`) ; l'app le lit pour
 * phonétiser un texte qu'aucun pack ne contient, un cartel scanné par exemple ([FrenchPhonemizer]).
 *
 * Format : une ligne par mot, `mot\tphonèmes`, avec une troisième colonne `h` pour un h aspiré (« les héros » sans
 * liaison). Les mots sont en minuscules.
 */
class FrenchLexicon private constructor(
    private val entries: Map<String, String>,
    private val aspiratedH: Set<String>,
) {
    val size: Int get() = entries.size

    fun phonemes(word: String): String? = entries[word.lowercase()]

    /** Le mot commence par un h aspiré : pas de liaison ni d'élision devant lui. */
    fun hasAspiratedH(word: String): Boolean = word.lowercase() in aspiratedH

    companion object {
        fun parse(lines: Sequence<String>): FrenchLexicon {
            val entries = HashMap<String, String>(131_072)
            val aspirated = HashSet<String>()
            for (line in lines) {
                val parts = line.split('\t')
                if (parts.size < 2 || parts[0].isEmpty() || parts[1].isEmpty()) continue
                val word = parts[0].lowercase()
                entries.getOrPut(word) { parts[1] }
                if (parts.getOrNull(2) == "h") aspirated += word
            }
            return FrenchLexicon(entries, aspirated)
        }

        fun parse(text: String): FrenchLexicon = parse(text.lineSequence())

        /** Une ligne du fichier, pour le pack-builder. */
        fun line(word: String, phonemes: String, aspiratedH: Boolean = false): String =
            "${word.lowercase()}\t$phonemes" + if (aspiratedH) "\th" else ""
    }
}
