package app.audioguide.catalog

/**
 * Joconde écrit « NOM Prénom (dates) » ; on veut « Prénom Nom » pour l'affichage et la lecture vocale.
 * Heuristique : les premiers mots tout en majuscules forment le nom ; sans eux, le premier mot est le nom.
 */
object ArtistFormatter {
    private val particles = setOf("de", "du", "des", "d'", "la", "le", "van", "von", "da", "di", "del", "della", "y", "ter")
    private val romanSuffix = Regex("""^[IVX]+$""")

    fun format(raw: String?): String? {
        val variants = raw?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (variants.isEmpty()) return null
        // « (dit) » marque la forme usuelle du nom (« VINCI Léonard de (dit) »).
        val chosen = variants.firstOrNull { it.contains("(dit", ignoreCase = true) } ?: variants.first()
        val name = chosen.replace(Regex("""\([^)]*\)"""), " ").trim().replace(Regex("""\s+"""), " ")
        if (name.isEmpty()) return null

        val tokens = name.split(' ')
        if (tokens.size == 1) return titleCase(tokens.first())

        val surnameLength = tokens.takeWhile(::isAllUpper).size.coerceAtLeast(1)
        if (surnameLength == tokens.size) return tokens.joinToString(" ", transform = ::titleCase)

        val surname = tokens.take(surnameLength).joinToString(" ", transform = ::titleCase)
        var given = tokens.drop(surnameLength).toMutableList()

        val suffix = if (given.size > 1 && romanSuffix.matches(given.last())) given.removeLast() else null
        val movedParticles = ArrayDeque<String>()
        while (given.size > 1 && given.last().lowercase() in particles) movedParticles.addFirst(given.removeLast())

        return buildList {
            addAll(given)
            addAll(movedParticles)
            add(surname)
            suffix?.let { add(it) }
        }.joinToString(" ")
    }

    private fun isAllUpper(token: String): Boolean {
        val letters = token.filter { it.isLetter() }
        return letters.length > 1 && letters.all { it.isUpperCase() } && !romanSuffix.matches(token)
    }

    private fun titleCase(word: String): String =
        word.split('-').joinToString("-") { part ->
            part.lowercase().replaceFirstChar { it.uppercaseChar() }
        }
}
