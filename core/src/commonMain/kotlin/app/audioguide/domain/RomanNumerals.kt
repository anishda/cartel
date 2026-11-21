package app.audioguide.domain

/**
 * Chiffres romains dits à voix haute. Les moteurs vocaux ne les reconnaissent pas toujours : la voix neuronale (Piper,
 * via espeak-ng) épelle « XVIIIe » (« iks-vé-i-i-i ») et dit « Louis XIV » « Louis quatorze romain » ; les voix du
 * système s'en sortent avec « XVIIIe siècle » bien écrit, mais pas avec ce que rend l'OCR d'un cartel, où le « e » en
 * exposant devient « XVIII e », « XVIIIᵉ », « XVIII° » ou disparaît (« XVIII siècle »).
 *
 * Les siècles deviennent des ordinaux en lettres (« dix-huitième siècle », « eighteenth century »), les numéros de
 * souverains des nombres (« Louis quatorze », « François premier », « Louis the Fourteenth »).
 */
object RomanNumerals {
    /** Un nombre romain bien formé jusqu'à 39 : assez pour les siècles et les souverains, et pas de faux positifs. */
    private const val ROMAN = """X{0,3}(?:IX|IV|V?I{0,3})"""
    private val canonical = Regex("^$ROMAN$")

    // « XVIIIe », « XVIIIème », « XVIIIᵉ », « Ier », « Iʳᵉ » : suffixe collé, siècle ou non à la suite.
    private val frenchAttached = Regex("""\b($ROMAN)(ᵉʳ|ʳᵉ|ᵉ|er|re|ère|ème|eme|è|e)(?![\p{L}\d])""")
    // « XVIII e siècle », « XVIII° siècle », « XVIII siècle » : suffixe détaché ou perdu, seulement devant « siècle ».
    private val frenchDetached = Regex("""\b($ROMAN)(?:\s?(?:ᵉʳ|ᵉ|er|e|°|º))?(?=\s+siècles?\b)""")
    private val frenchRegnal = Regex("""\b(\p{Lu}\p{Ll}+(?:-\p{Lu}\p{Ll}+)?)\s+($ROMAN)(?![\p{L}\d'’°ºᵉ])""")

    // « XVIIIth century », « XVIII century », « XVIIIe century ».
    private val englishCentury = Regex("""\b($ROMAN)(?:st|nd|rd|th|e)?(?=\s+centur(?:y|ies)\b)""")
    private val englishRegnal = Regex("""\b(\p{Lu}\p{Ll}+(?:-\p{Lu}\p{Ll}+)?)\s+($ROMAN)(?![\p{L}\d'’°ºᵉ])""")

    /** Mots devant lesquels un chiffre romain n'est pas un numéro de souverain (« Le XV de France », « Act V »). */
    private val notNames = setOf(
        "Le", "La", "Les", "Du", "De", "Des", "Au", "Aux", "En", "Et", "Un", "Une", "Ce", "Cette",
        "The", "A", "An", "Of", "In", "And", "Act", "Acte", "Chapter", "Chapitre", "Part", "Partie",
        "War", "Tome", "Volume", "Livre", "Book", "Section", "Article", "Planche", "Plate", "Fig", "Pl",
    )

    fun value(roman: String): Int? {
        if (roman.isEmpty() || !canonical.matches(roman)) return null
        val values = mapOf('I' to 1, 'V' to 5, 'X' to 10)
        var total = 0
        for ((i, c) in roman.withIndex()) {
            val v = values.getValue(c)
            val next = roman.getOrNull(i + 1)?.let { values.getValue(it) } ?: 0
            total += if (v < next) -v else v
        }
        return total
    }

    /** Siècles seulement : « XVIIIe » → « dix-huitième », « Ier » → « premier », « XVIII e siècle » → « dix-huitième siècle ». */
    fun frenchCenturies(text: String): String {
        var result = frenchAttached.replace(text) { match ->
            val n = value(match.groupValues[1]) ?: return@replace match.value
            val suffix = match.groupValues[2]
            val first = suffix == "er" || suffix == "ᵉʳ" || suffix == "re" || suffix == "ère" || suffix == "ʳᵉ"
            when {
                n == 1 && first -> if (suffix == "er" || suffix == "ᵉʳ") "premier" else "première"
                n >= 2 && !first -> frenchOrdinal(n)
                else -> match.value
            }
        }
        result = frenchDetached.replace(result) { match ->
            val n = value(match.groupValues[1]) ?: return@replace match.value
            frenchOrdinal(n)
        }
        return result
    }

    /** Siècles, puis souverains et papes : « Louis XIV » → « Louis quatorze ». */
    fun spellFrench(text: String): String {
        val centuries = frenchCenturies(text)
        return frenchRegnal.replace(centuries) { match ->
            val name = match.groupValues[1]
            val n = value(match.groupValues[2])
            if (n == null || n < 2 || name in notNames) match.value else "$name ${FrenchNumbers.spell(n)}"
        }
    }

    /** « XVIIIth century » → « eighteenth century », « Louis XIV » → « Louis the Fourteenth ». */
    fun spellEnglish(text: String): String {
        val centuries = englishCentury.replace(text) { match ->
            val n = value(match.groupValues[1]) ?: return@replace match.value
            englishOrdinal(n)
        }
        return englishRegnal.replace(centuries) { match ->
            val name = match.groupValues[1]
            val n = value(match.groupValues[2])
            if (n == null || n < 2 || name in notNames) match.value
            else "$name the ${englishOrdinal(n).replaceFirstChar { it.uppercaseChar() }}"
        }
    }

    /** 2 → « deuxième », 21 → « vingt et unième » : le nombre en lettres, sans son « e » final, plus « ième ». */
    fun frenchOrdinal(n: Int): String {
        require(n >= 1)
        if (n == 1) return "premier"
        val cardinal = FrenchNumbers.spell(n).removeSuffix("s")
        return when {
            cardinal.endsWith("cinq") -> cardinal + "uième"
            cardinal.endsWith("neuf") -> cardinal.dropLast(1) + "vième"
            cardinal.endsWith("e") -> cardinal.dropLast(1) + "ième"
            else -> cardinal + "ième"
        }
    }

    private val englishUnits = listOf(
        "", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth",
        "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth", "sixteenth", "seventeenth", "eighteenth",
        "nineteenth",
    )

    fun englishOrdinal(n: Int): String {
        require(n in 1..39)
        if (n < 20) return englishUnits[n]
        val ten = if (n < 30) "twenty" else "thirty"
        return if (n % 10 == 0) ten.dropLast(1) + "ieth" else "$ten-${englishUnits[n % 10]}"
    }
}
