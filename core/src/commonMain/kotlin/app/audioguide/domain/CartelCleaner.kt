package app.audioguide.domain

/**
 * Prépare le texte brut d'un cartel pour la lecture vocale : retire les numéros d'inventaire,
 * recolle les mots coupés, développe dates, siècles et abréviations.
 */
object CartelCleaner {
    private val hyphenatedBreak = Regex("""([A-Za-zÀ-ÿ])-\n([a-zà-ÿ])""")
    // Un « de » déjà présent devant la période est absorbé, pour ne pas lire « de de 1503 à 1519 ».
    private val yearRange = Regex("""(?:\bde\s+)?\b(\d{3,4})\s*[-–—]\s*(\d{3,4})\b""")
    private val century = Regex("""\b([IVX]+)(er|re|e|ème|eme|è)\b""")
    // Dates de Joconde : « 1er quart XVIe siècle », « 2e moitié XIXe siècle », « début XVIIe siècle ». Appliqué après la
    // conversion des siècles en lettres, pour dire « premier quart du seizième siècle ».
    private val part = Regex("""\b(\d)(?:er|ère|re|e|ème|è)\s+(quart|tiers|moiti[ée])\s+(?:du\s+)?(?!du\b)(\p{L}+(?:-\p{L}+)?(?: et unième)? siècle)""", RegexOption.IGNORE_CASE)
    private val edge = Regex("""\b(début|milieu|fin)\s+(?:du\s+)?(?!du\b)(\p{L}+(?:-\p{L}+)?(?: et unième)? siècle)""", RegexOption.IGNORE_CASE)
    private val dimensionH = Regex("""\bH\.\s?(?=\d)""")
    private val dimensionL = Regex("""\bL\.\s?(?=\d)""")
    // L'OCR lit parfois le zéro d'une mesure comme un « o » (« 19o cm »).
    private val zeroReadAsLetter = Regex("""(\d)[oO](\s?cm\b)""")
    private val centimetres = Regex("""(\d+(?:[.,]\d+)?)\s?cm\b""")
    private val spaces = Regex("""[ \t ]+""")

    private val ordinals = listOf(
        "premier", "deuxième", "troisième", "quatrième", "cinquième", "sixième", "septième",
        "huitième", "neuvième", "dixième", "onzième", "douzième", "treizième", "quatorzième",
        "quinzième", "seizième", "dix-septième", "dix-huitième", "dix-neuvième", "vingtième",
        "vingt et unième",
    )

    /** Une ligne vide sépare deux paragraphes : chaque paragraphe est nettoyé seul, et la séparation est gardée. */
    fun clean(raw: String): String =
        raw.replace("\r\n", "\n").replace('\r', '\n')
            .split(paragraphBreak)
            .map(::cleanParagraph)
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")

    private val paragraphBreak = Regex("""\n[ \t]*\n+""")

    private fun cleanParagraph(raw: String): String {
        var text = raw

        text = text.lines()
            .filterNot { InventoryNumbers.wholeLine.matches(it) }
            .joinToString("\n") { line -> InventoryNumbers.inline.replace(line, "") }
        text = InventoryNumbers.prefix.replace(text, "")

        text = hyphenatedBreak.replace(text) { it.groupValues[1] + it.groupValues[2] }
        text = reflow(text)

        text = yearRange.replace(text) { match ->
            val from = match.groupValues[1].toInt()
            val to = match.groupValues[2].toInt()
            if (from in 500..2100 && to in 500..2100 && from < to) "de $from à $to" else match.value
        }
        text = century.replace(text) { match ->
            val value = romanToInt(match.groupValues[1])
            when {
                value == 1 && match.groupValues[2] == "er" -> "premier"
                value in 2..21 && match.groupValues[2] != "er" -> ordinals[value - 1]
                else -> match.value
            }
        }
        text = part.replace(text) { match ->
            val rank = match.groupValues[1].toInt()
            val portion = match.groupValues[2].lowercase().replace("moitie", "moitié")
            val feminine = portion == "moitié"
            val word = when {
                rank == 1 -> if (feminine) "première" else "premier"
                rank in 2..4 -> ordinals[rank - 1]
                else -> return@replace match.value
            }
            "$word $portion du ${match.groupValues[3]}"
        }
        text = edge.replace(text) { "${it.groupValues[1].lowercase()} du ${it.groupValues[2]}" }
        text = text
            .replace("av. J.-C.", "avant Jésus-Christ")
            .replace("apr. J.-C.", "après Jésus-Christ")
            .replace(Regex("""\benv\."""), "environ")
        text = dimensionH.replace(text, "hauteur ")
        text = dimensionL.replace(text, "largeur ")
        text = zeroReadAsLetter.replace(text) { "${it.groupValues[1]}0${it.groupValues[2]}" }
        text = centimetres.replace(text) { "${it.groupValues[1]} centimètres" }

        return text.lines()
            .map { spaces.replace(it, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    /** Une ligne qui ne finit pas une phrase et que suit une minuscule continue sur la ligne suivante. */
    private fun reflow(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val out = StringBuilder()
        for ((index, line) in lines.withIndex()) {
            out.append(line)
            val next = lines.getOrNull(index + 1) ?: break
            val endsSentence = line.last() in ".!?:;»)"
            val continues = next.first().isLowerCase()
            out.append(if (!endsSentence && continues) ' ' else '\n')
        }
        return out.toString()
    }

    private fun romanToInt(roman: String): Int {
        val values = mapOf('I' to 1, 'V' to 5, 'X' to 10)
        var total = 0
        for ((i, c) in roman.withIndex()) {
            val value = values.getValue(c)
            val next = roman.getOrNull(i + 1)?.let { values.getValue(it) } ?: 0
            total += if (value < next) -value else value
        }
        return total
    }
}
