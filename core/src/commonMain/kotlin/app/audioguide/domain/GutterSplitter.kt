package app.audioguide.domain

/** Un mot lu par l'OCR et son étendue horizontale, en fraction de la largeur de l'image (0 à 1). */
data class WordBox(val text: String, val left: Float, val right: Float)

/**
 * Coupe une ligne de l'OCR là où un grand espace sépare deux groupes de mots : deux colonnes de texte
 * sur la même base sont parfois lues comme une seule ligne, ce qui mélange leurs langues.
 *
 * Un espace est une gouttière s'il est très large (plus de 6 % de la largeur de l'image), ou nettement plus large
 * que les autres espaces de la ligne (au moins quatre fois leur médiane, et plus de 1,2 % de l'image). Un titre aux
 * grands mots uniformément espacés n'est donc pas coupé.
 */
object GutterSplitter {
    private const val SURE_GAP = 0.06f
    private const val MIN_RELATIVE_GAP = 0.012f
    private const val RELATIVE_FACTOR = 4f
    private const val MIN_TYPICAL_GAP = 0.002f

    /** Les groupes de mots de la ligne, de gauche à droite. */
    fun group(words: List<WordBox>, minGap: Float? = null): List<List<WordBox>> {
        if (words.isEmpty()) return emptyList()
        val gaps = words.zipWithNext { a, b -> b.left - a.right }
        val groups = mutableListOf(mutableListOf(words.first()))
        for ((i, gap) in gaps.withIndex()) {
            if (isGutter(gap, gaps, i, minGap)) groups += mutableListOf(words[i + 1]) else groups.last() += words[i + 1]
        }
        return groups
    }

    /** Les morceaux de texte de la ligne. Une ligne sans gouttière reste d'un seul tenant. */
    fun split(words: List<WordBox>, minGap: Float? = null): List<String> =
        group(words, minGap).map { group -> group.joinToString(" ") { it.text } }

    private fun isGutter(gap: Float, gaps: List<Float>, index: Int, minGap: Float?): Boolean {
        if (minGap != null) return gap > minGap
        if (gap > SURE_GAP) return true
        if (gap < MIN_RELATIVE_GAP) return false
        val others = gaps.filterIndexed { i, _ -> i != index }.sorted()
        if (others.isEmpty()) return false
        val typical = others[others.size / 2].coerceAtLeast(MIN_TYPICAL_GAP)
        return gap >= RELATIVE_FACTOR * typical
    }
}
