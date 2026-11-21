package app.audioguide.domain

/**
 * Prononciation d'un mot français d'après son orthographe, pour les mots absents du lexique (noms propres, mots abîmés
 * par l'OCR). Des règles simples, lues de gauche à droite, la plus longue d'abord : moins juste qu'un lexique, mais la
 * voix dit un mot plausible au lieu de se taire. Les symboles sont ceux d'espeak-ng (voix Piper françaises).
 */
object FrenchSpellingRules {
    private const val VOWELS = "aàâäeéèêëiîïoôöuùûüyœ"

    /** Règles : graphie, phonèmes, et condition sur ce qui suit (`V` voyelle, `C` consonne ou fin, `F` fin du mot). */
    private data class Rule(val graph: String, val sound: String, val before: Char? = null)

    private val rules: List<Rule> = listOf(
        Rule("eaux", "o", 'F'), Rule("eau", "o"), Rule("aux", "o", 'F'),
        Rule("oin", "wɛ̃", 'C'), Rule("ain", "ɛ̃", 'C'), Rule("aim", "ɛ̃", 'C'), Rule("ein", "ɛ̃", 'C'), Rule("ien", "jɛ̃", 'C'),
        Rule("ille", "ij"), Rule("ill", "ij"), Rule("eil", "ɛj", 'C'), Rule("ail", "aj", 'C'),
        Rule("oeu", "ø"), Rule("œu", "ø"), Rule("ou", "u"), Rule("où", "u"), Rule("oû", "u"), Rule("oi", "wa"),
        Rule("au", "o"), Rule("ai", "ɛ"), Rule("aî", "ɛ"), Rule("ei", "ɛ"), Rule("eu", "ø"),
        Rule("an", "ɑ̃", 'C'), Rule("am", "ɑ̃", 'C'), Rule("en", "ɑ̃", 'C'), Rule("em", "ɑ̃", 'C'),
        Rule("on", "ɔ̃", 'C'), Rule("om", "ɔ̃", 'C'), Rule("in", "ɛ̃", 'C'), Rule("im", "ɛ̃", 'C'),
        Rule("yn", "ɛ̃", 'C'), Rule("ym", "ɛ̃", 'C'), Rule("un", "œ̃", 'C'), Rule("um", "œ̃", 'C'),
        Rule("er", "e", 'F'), Rule("ez", "e", 'F'), Rule("et", "ɛ", 'F'), Rule("es", "", 'F'), Rule("ent", "ɑ̃", 'F'),
        Rule("sch", "ʃ"), Rule("ch", "ʃ"), Rule("ph", "f"), Rule("th", "t"), Rule("gn", "ɲ"), Rule("qu", "k"),
        Rule("gu", "ɡ", 'V'), Rule("ge", "ʒ", 'V'), Rule("ss", "s"), Rule("sc", "s", 'V'), Rule("ck", "k"), Rule("cc", "k"),
        Rule("é", "e"), Rule("è", "ɛ"), Rule("ê", "ɛ"), Rule("ë", "ɛ"), Rule("à", "a"), Rule("â", "ɑ"), Rule("ä", "a"),
        Rule("î", "i"), Rule("ï", "i"), Rule("ô", "o"), Rule("ö", "o"), Rule("û", "y"), Rule("ù", "y"), Rule("ü", "y"),
        Rule("œ", "œ"), Rule("ç", "s"), Rule("a", "a"), Rule("i", "i"), Rule("o", "o"), Rule("u", "y"), Rule("y", "i"),
        Rule("j", "ʒ"), Rule("r", "ʁ"), Rule("x", "ks"), Rule("w", "w"), Rule("h", ""),
        Rule("b", "b"), Rule("d", "d"), Rule("f", "f"), Rule("k", "k"), Rule("l", "l"), Rule("m", "m"), Rule("n", "n"),
        Rule("p", "p"), Rule("t", "t"), Rule("v", "v"), Rule("z", "z"),
    ).sortedByDescending { it.graph.length }

    /** Consonnes finales muettes (« Renoir » garde son r, « Degas » perd son s). */
    private const val SILENT_FINALS = "stdxzpg"

    fun phonemize(word: String): String {
        val w = word.lowercase().filter { it.isLetter() }
        if (w.isEmpty()) return ""
        if (w.length == 1) return LETTERS[w[0]] ?: ""
        val out = StringBuilder()
        var i = 0
        while (i < w.length) {
            val rest = w.length - i
            // e muet en fin de mot, et consonnes finales muettes.
            if (rest == 1 && w[i] == 'e' && i > 0) break
            if (rest == 1 && w[i] in SILENT_FINALS && i > 0) break
            // Pluriel muet : « -es », « -ts », « -ds » (« Monets » se dit comme « Monet »).
            if (rest == 2 && w.last() == 's' && i > 0 && (w[i] == 'e' || w[i] in SILENT_FINALS)) break
            val c = w[i]
            val next = w.getOrNull(i + 1)
            // Les graphies de plusieurs lettres d'abord (« ch », « gn », « ss », « qu »…).
            val long = rules.firstOrNull { it.graph.length > 1 && it.graph[0] != 'e' && w.startsWith(it.graph, i) && matches(it, w, i) }
            if (long != null) { out.append(long.sound); i += long.graph.length; continue }
            // c et g devant e, i, y ; s entre deux voyelles ; doubles consonnes.
            when {
                c == 'c' && next != null && next in "eéèêiîy" -> { out.append('s'); i++; continue }
                c == 'c' -> { out.append('k'); i++; continue }
                c == 'g' && next != null && next in "eéèêiîy" -> { out.append('ʒ'); i++; continue }
                c == 'g' -> { out.append('ɡ'); i++; continue }
                c == 's' && i > 0 && w[i - 1] in VOWELS && next != null && next in VOWELS -> { out.append('z'); i++; continue }
                c == 's' -> { out.append('s'); i++; continue }
                c == 'e' -> {
                    // e devant deux consonnes ou une consonne finale : è ; sinon e muet ou ə.
                    val after = w.substring(i + 1)
                    val rule = rules.firstOrNull { it.graph.startsWith('e') && w.startsWith(it.graph, i) && matches(it, w, i) }
                    if (rule != null) { out.append(rule.sound); i += rule.graph.length; continue }
                    val consonants = after.takeWhile { it !in VOWELS }
                    out.append(if (consonants.length >= 2 || (consonants.length == 1 && consonants.length == after.length && consonants !in SILENT_FINALS)) "ɛ" else "ə")
                    i++
                    continue
                }
                next != null && c == next && c !in VOWELS -> { i++; continue }
            }
            val rule = rules.firstOrNull { w.startsWith(it.graph, i) && matches(it, w, i) }
            if (rule == null) { i++; continue }
            out.append(rule.sound)
            i += rule.graph.length
        }
        return stress(out.toString())
    }

    private fun matches(rule: Rule, word: String, at: Int): Boolean {
        val end = at + rule.graph.length
        val following = word.getOrNull(end)
        return when (rule.before) {
            null -> true
            'F' -> end == word.length
            'V' -> following != null && following in VOWELS
            'C' -> following == null || (following !in VOWELS && following != 'n' && following != 'm')
            else -> true
        }
    }

    private const val VOWEL_SOUNDS = "aeiouyɛɔəøœɑ"

    /** L'accent tombe sur la dernière voyelle prononcée, comme espeak le note (« ʒokˈɔ̃d »). */
    private fun stress(phonemes: String): String {
        val index = phonemes.indexOfLast { it in VOWEL_SOUNDS && it != 'ə' }
        return if (index < 0) phonemes else phonemes.substring(0, index) + "ˈ" + phonemes.substring(index)
    }

    private val LETTERS = mapOf(
        'a' to "ˈa", 'à' to "ˈa", 'y' to "iɡʁˈɛk", 'o' to "ˈo", 'e' to "ˈə", 'é' to "ˈe", 'u' to "ˈy", 'i' to "ˈi",
    )
}
