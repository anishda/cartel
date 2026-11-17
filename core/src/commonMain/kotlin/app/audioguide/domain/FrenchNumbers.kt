package app.audioguide.domain

/**
 * Écrit les nombres en toutes lettres pour la lecture vocale française : « 1513 » devient « mille cinq cent treize ».
 * Les voix neuronales (Piper) avalent la dernière consonne des nombres écrits en chiffres (« mille cinq cent trei ») ;
 * les voix du système n'en souffrent pas, mais l'écriture en lettres ne leur nuit pas.
 */
object FrenchNumbers {
    private val units = listOf(
        "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf", "dix", "onze", "douze",
        "treize", "quatorze", "quinze", "seize",
    )
    private val tens = mapOf(2 to "vingt", 3 to "trente", 4 to "quarante", 5 to "cinquante", 6 to "soixante")

    /** Plus grand nombre épelé : au-delà, c'est un numéro ou une mesure que le moteur lira mieux en chiffres. */
    const val MAX = 9_999

    fun spell(n: Int): String {
        require(n in 0..MAX) { "Hors limites : $n" }
        return if (n < 1000) belowThousand(n, plural = true) else thousands(n)
    }

    /** Remplace chaque nombre de [text] par ses lettres ; « × » entre deux nombres devient « sur » (dimensions). */
    fun spellNumbersIn(text: String): String {
        var result = DIMENSION.replace(text) { "${it.groupValues[1]} sur ${it.groupValues[2]}" }
        result = DECIMAL.replace(result) { match ->
            val whole = match.groupValues[1].toInt()
            val fraction = match.groupValues[2]
            val spokenFraction = if (fraction.startsWith("0")) "zéro " + spell(fraction.toInt()) else spell(fraction.toInt())
            spell(whole) + " virgule " + spokenFraction
        }
        return INTEGER.replace(result) { spell(it.value.toInt()) }
    }

    private fun thousands(n: Int): String {
        val thousandsPart = n / 1000
        val rest = n % 1000
        val head = if (thousandsPart == 1) "mille" else belowThousand(thousandsPart, plural = false) + " mille"
        return if (rest == 0) head else head + " " + belowThousand(rest, plural = true)
    }

    /** [plural] : « cent » et « quatre-vingt » prennent un s quand rien ne les suit (« deux cents », mais « deux cent trois »). */
    private fun belowThousand(n: Int, plural: Boolean): String {
        val hundreds = n / 100
        val rest = n % 100
        if (hundreds == 0) return belowHundred(rest, plural)
        val hundredWord = if (hundreds == 1) "cent" else units[hundreds] + " cent" + if (rest == 0 && plural) "s" else ""
        return if (rest == 0) hundredWord else hundredWord + " " + belowHundred(rest, plural)
    }

    private fun belowHundred(n: Int, plural: Boolean): String = when {
        n <= 16 -> units[n]
        n < 20 -> "dix-" + units[n - 10]
        n < 70 -> {
            val ten = tens.getValue(n / 10)
            when (n % 10) {
                0 -> ten
                1 -> "$ten et un"
                else -> ten + "-" + units[n % 10]
            }
        }
        n < 80 -> if (n == 71) "soixante et onze" else "soixante-" + belowHundred(n - 60, plural = false)
        n == 80 -> if (plural) "quatre-vingts" else "quatre-vingt"
        else -> "quatre-vingt-" + belowHundred(n - 80, plural = false)
    }

    // Un nombre isolé : ni collé à d'autres chiffres, ni décimal, ni groupe de milliers (« 20 000 »), ni numéro (« RF 644 »
    // est retiré avant, et les nombres de plus de quatre chiffres restent en chiffres).
    private val INTEGER = Regex("""(?<![\d,.])(?<!\d\s)(?!0\d)\b\d{1,4}\b(?![\d]|[.,]\d|\s\d{3}\b)""")
    private val DECIMAL = Regex("""(?<![\d,.])\b(\d{1,3}),(\d{1,2})\b(?!\d)""")
    private val DIMENSION = Regex("""(\d+(?:,\d+)?) × (\d+(?:,\d+)?)""")
}
