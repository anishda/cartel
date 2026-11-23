package app.audioguide.domain

/**
 * Phonétise un texte français pour la voix neuronale (Piper), sans espeak-ng : mot par mot avec [FrenchLexicon], puis
 * ce que le contexte change (élisions, liaisons, mots outils sans accent). Un mot absent du lexique (nom propre, mot
 * abîmé par l'OCR) passe par des règles d'orthographe ([FrenchSpellingRules]).
 *
 * Le résultat a la forme des phonèmes des packs : une phrase par ligne, les mots séparés par une espace, la ponctuation
 * collée au mot qui la précède.
 */
class FrenchPhonemizer(private val lexicon: FrenchLexicon) {
    private data class Word(val text: String, val phonemes: String, val punctuation: String)

    fun phonemize(text: String): String =
        sentences(text).map { sentence -> phonemizeSentence(sentence) }.filter { it.isNotBlank() }.joinToString("\n")

    private fun phonemizeSentence(sentence: String): String {
        val words = mutableListOf<Word>()
        for (match in TOKEN.findAll(sentence)) {
            val token = match.value
            if (token.first().isLetterOrDigit()) {
                words += wordsOf(token).map { (text, phonemes) -> Word(text, phonemes, "") }
            } else if (words.isNotEmpty()) {
                val last = words.removeLast()
                words += last.copy(punctuation = last.punctuation + token.filter { it in KEPT_PUNCTUATION })
            }
        }
        return words.mapIndexed { i, word ->
            val next = words.getOrNull(i + 1)
            val liaison = if (word.punctuation.isEmpty() && next != null && startsWithVowel(next)) LIAISONS[word.text.lowercase()].orEmpty() else ""
            word.phonemes + liaison + word.punctuation
        }.joinToString(" ")
    }

    /** Un mot du texte : « l'homme » donne « l » collé à « homme », « cadran-solaire » deux mots. */
    private fun wordsOf(token: String): List<Pair<String, String>> {
        val lower = token.lowercase().replace('’', '\'')
        if (lower.all { it.isDigit() }) return spellDigits(lower).split(' ').map { it to lookup(it) }
        lexicon.phonemes(lower)?.let { return listOf(lower to unstressed(lower, it)) }
        ELISION.matchEntire(lower)?.let { m ->
            val (prefix, rest) = m.destructured
            return listOf(rest to (ELIDED.getValue(prefix) + lookup(rest)))
        }
        if ('-' in lower) return lower.split('-').filter { it.isNotEmpty() }.flatMap { wordsOf(it) }
        if ('\'' in lower) return lower.split('\'').filter { it.isNotEmpty() }.flatMap { wordsOf(it) }
        return listOf(lower to lookup(lower))
    }

    private fun lookup(word: String): String =
        lexicon.phonemes(word)?.let { unstressed(word, it) } ?: FrenchSpellingRules.phonemize(word)

    /** Les mots outils ne portent pas d'accent dans une phrase (« le », « et », « un ») : espeak les marque d'un tiret. */
    private fun unstressed(word: String, phonemes: String): String =
        if (phonemes.endsWith("-") || word in UNSTRESSED) phonemes.replace("ˈ", "").replace("ˌ", "") else phonemes

    private fun startsWithVowel(word: Word): Boolean {
        if (lexicon.hasAspiratedH(word.text)) return false
        val first = word.text.firstOrNull() ?: return false
        if (first.lowercaseChar() !in VOWEL_LETTERS && first.lowercaseChar() != 'h') return false
        val sound = word.phonemes.trimStart('ˈ', 'ˌ').firstOrNull() ?: return false
        return sound in VOWEL_SOUNDS
    }

    private fun spellDigits(digits: String): String {
        val n = digits.toIntOrNull()
        return if (n != null && n <= FrenchNumbers.MAX) FrenchNumbers.spell(n).replace('-', ' ')
        else digits.map { FrenchNumbers.spell(it.digitToInt()) }.joinToString(" ")
    }

    private fun sentences(text: String): List<String> =
        SENTENCE.findAll(text).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()

    private companion object {
        val TOKEN = Regex("""[\p{L}\d]+(?:['’\-][\p{L}\d]+)*['’]?|[^\s\p{L}\d]""")
        val SENTENCE = Regex("""[^.!?…]+[.!?…]*""")
        const val KEPT_PUNCTUATION = ",.;:!?"

        val ELISION = Regex("""^(l|d|j|m|t|s|n|c|qu|jusqu|lorsqu|puisqu|quoiqu)'(.+)$""")
        val ELIDED = mapOf(
            "l" to "l", "d" to "d", "j" to "ʒ", "m" to "m", "t" to "t", "s" to "s", "n" to "n", "c" to "s",
            "qu" to "k", "jusqu" to "ʒysk", "lorsqu" to "lɔʁsk", "puisqu" to "pyisk", "quoiqu" to "kwak",
        )

        /** Consonne de liaison ajoutée devant une voyelle. */
        val LIAISONS: Map<String, String> = buildMap {
            listOf(
                "les", "des", "mes", "tes", "ses", "ces", "aux", "nous", "vous", "ils", "elles", "deux", "trois",
                "six", "dix", "très", "plus", "sans", "dans", "chez", "tous", "nos", "vos", "leurs", "quels", "quelles",
                "aucuns", "plusieurs", "certains", "certaines", "quelques", "autres", "mêmes", "anciens", "anciennes",
                "grands", "grandes", "petits", "petites", "beaux", "belles", "nouveaux", "nouvelles", "premiers", "premières",
            ).forEach { put(it, "z") }
            listOf("un", "on", "en", "mon", "ton", "son", "aucun", "bien", "rien", "bon", "certain", "ancien").forEach { put(it, "n") }
            listOf("est", "sont", "ont", "font", "vont", "dont", "quand", "tout", "petit", "grand", "était", "étaient", "fait", "peut", "doit").forEach { put(it, "t") }
        }

        val UNSTRESSED = setOf(
            "le", "la", "les", "l", "de", "du", "des", "d", "un", "une", "et", "est", "à", "au", "aux", "en", "on", "il",
            "ils", "elle", "elles", "je", "tu", "nous", "vous", "ce", "cet", "cette", "ces", "se", "ne", "me", "te",
            "mon", "ton", "son", "ma", "ta", "sa", "mes", "tes", "ses", "nos", "vos", "qui", "que", "dont", "ou", "où",
            "mais", "donc", "par", "pour", "sur", "dans", "avec", "sans", "chez", "y",
        )

        const val VOWEL_LETTERS = "aàâäeéèêëiîïoôöuùûüyœæ"
        const val VOWEL_SOUNDS = "aeiouyɛɔəøœɑwɥj"
    }
}
