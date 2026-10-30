package app.audioguide.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LabelLanguagesTest {
    private val header = "Édouard MANET (1832-1883)\nOlympia\n1863"

    private val french = listOf(
        "Le tableau reprend le motif de la Vénus",
        "couchée de la Renaissance, mais en le",
        "transposant dans le Paris du XIXe siècle.",
        "Présenté au Salon de 1865, il provoque",
        "un scandale que la presse a relayé.",
    )
    private val english = listOf(
        "The painting takes up the motif of the",
        "reclining Venus of the Renaissance, but",
        "sets it in the Paris of the nineteenth century.",
        "Shown at the Salon of 1865, it caused",
        "a scandal that the press has relayed.",
    )

    @Test
    fun splitsStackedParagraphs() {
        val versions = LabelLanguages.split((listOf(header) + french + english).joinToString("\n"))
        assertEquals(listOf("fr-FR", "en-US"), versions.map { it.languageTag })
        val fr = versions[0].text
        val en = versions[1].text
        assertTrue(fr.contains("Vénus couchée".take(5)) && fr.contains("transposant"), fr)
        assertFalse(fr.contains("painting"), fr)
        assertTrue(en.contains("painting") && !en.contains("tableau"), en)
    }

    @Test
    fun splitsInterleavedColumns() {
        // Deux colonnes lues ligne à ligne : une ligne française, puis sa voisine anglaise.
        val interleaved = french.zip(english).flatMap { (fr, en) -> listOf(fr, en) }
        val versions = LabelLanguages.split((listOf(header) + interleaved).joinToString("\n"))
        assertEquals(setOf("fr-FR", "en-US"), versions.map { it.languageTag }.toSet())
        val fr = versions.first { it.languageTag == "fr-FR" }.text
        assertEquals(french, fr.lines().filter { it in french }, "l'ordre français est conservé")
        assertFalse(fr.contains("painting"))
    }

    @Test
    fun neutralLinesStayInEveryVersion() {
        val versions = LabelLanguages.split((listOf(header) + french + english).joinToString("\n"))
        versions.forEach { assertTrue(it.text.contains("Olympia") && it.text.contains("1863"), it.text) }
    }

    @Test
    fun monolingualLabelIsNotSplit() {
        assertTrue(LabelLanguages.split((listOf(header) + french).joinToString("\n")).isEmpty())
        assertTrue(LabelLanguages.split((listOf(header) + english).joinToString("\n")).isEmpty())
    }

    @Test
    fun aSingleQuotedLineDoesNotMakeALabelBilingual() {
        val text = (listOf(header) + french + "Her gaze is unsettling and the press was cruel").joinToString("\n")
        assertTrue(LabelLanguages.split(text).isEmpty())
    }

    @Test
    fun tooShortOrEmptyTextIsNotSplit() {
        assertTrue(LabelLanguages.split("").isEmpty())
        assertTrue(LabelLanguages.split("Olympia\n1863").isEmpty())
    }

    @Test
    fun firstLanguageToAppearComesFirst() {
        val versions = LabelLanguages.split((listOf(header) + english + french).joinToString("\n"))
        assertEquals(listOf("en-US", "fr-FR"), versions.map { it.languageTag })
    }
}
