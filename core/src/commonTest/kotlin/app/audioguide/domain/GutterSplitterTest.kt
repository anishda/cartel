package app.audioguide.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class GutterSplitterTest {
    private fun words(vararg parts: Triple<String, Float, Float>) = parts.map { (t, l, r) -> WordBox(t, l, r) }

    @Test
    fun aLineWithoutGutterStaysWhole() {
        val line = words(Triple("Le", 0.05f, 0.07f), Triple("tableau", 0.075f, 0.14f), Triple("reprend", 0.145f, 0.21f))
        assertEquals(listOf("Le tableau reprend"), GutterSplitter.split(line))
    }

    @Test
    fun aVeryWideGapSeparatesTwoColumns() {
        val line = words(
            Triple("Le", 0.05f, 0.07f), Triple("tableau", 0.075f, 0.14f), Triple("Vénus", 0.145f, 0.21f),
            Triple("The", 0.30f, 0.33f), Triple("painting", 0.335f, 0.41f),
        )
        assertEquals(listOf("Le tableau Vénus", "The painting"), GutterSplitter.split(line))
    }

    @Test
    fun aNarrowGutterIsDetectedWhenMuchWiderThanTheWordSpaces() {
        // Gouttière de 2,5 % : trop étroite pour un seuil fixe, mais 6 fois les espaces entre mots (0,4 %).
        val line = words(
            Triple("Le", 0.050f, 0.070f), Triple("musée", 0.074f, 0.120f), Triple("YVES", 0.124f, 0.160f),
            Triple("SAINT", 0.164f, 0.210f), Triple("LAURENT", 0.214f, 0.270f),
            Triple("The", 0.295f, 0.320f), Triple("musée", 0.324f, 0.370f), Triple("YVES", 0.374f, 0.410f),
        )
        assertEquals(listOf("Le musée YVES SAINT LAURENT", "The musée YVES"), GutterSplitter.split(line))
    }

    @Test
    fun aTitleWithLargeEvenSpacesIsNotCut() {
        // Deux grands mots espacés de 2,4 % : rien à comparer, ce n'est pas une gouttière.
        val title = words(Triple("JEAN-RENÉ", 0.10f, 0.35f), Triple("GAUGUIN", 0.374f, 0.62f))
        assertEquals(listOf("JEAN-RENÉ GAUGUIN"), GutterSplitter.split(title))
    }

    @Test
    fun threeColumnsGiveThreePieces() {
        val line = words(Triple("a", 0.05f, 0.08f), Triple("b", 0.20f, 0.23f), Triple("c", 0.40f, 0.43f))
        assertEquals(listOf("a", "b", "c"), GutterSplitter.split(line))
    }

    @Test
    fun theThresholdCanBeForced() {
        val line = words(Triple("a", 0.05f, 0.08f), Triple("b", 0.10f, 0.13f))
        assertEquals(listOf("a b"), GutterSplitter.split(line))
        assertEquals(listOf("a", "b"), GutterSplitter.split(line, minGap = 0.01f))
    }

    @Test
    fun groupsKeepTheirWordsAndPositions() {
        val line = words(Triple("a", 0.05f, 0.08f), Triple("b", 0.083f, 0.11f), Triple("c", 0.40f, 0.43f))
        val groups = GutterSplitter.group(line)
        assertEquals(2, groups.size)
        assertEquals(listOf("a", "b"), groups[0].map { it.text })
        assertEquals(0.40f, groups[1].first().left)
    }

    @Test
    fun emptyAndSingleWord() {
        assertEquals(emptyList(), GutterSplitter.split(emptyList()))
        assertEquals(listOf("seul"), GutterSplitter.split(words(Triple("seul", 0.1f, 0.2f))))
    }
}
