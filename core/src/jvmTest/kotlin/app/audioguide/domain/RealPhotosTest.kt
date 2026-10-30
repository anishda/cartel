package app.audioguide.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Sorties réelles d'un OCR sur trois photos de cartels (exposition sur fond rouge, bannière en basse résolution,
 * salle sombre à deux colonnes). Le texte est anonymisé (les lettres deviennent des « x ») : seules la structure
 * et les positions comptent ici.
 */
class RealPhotosTest {
    @Serializable
    private class Fixture(val imageWidth: Int, val imageHeight: Int, val lines: List<Line>) {
        @Serializable
        class Line(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float, val confidence: Float)

        fun recognized() = RecognizedText(lines.map { TextLine(it.text, it.left, it.top, it.right, it.bottom, it.confidence) }, imageWidth, imageHeight)
    }

    private fun load(name: String): Fixture {
        val text = checkNotNull(RealPhotosTest::class.java.getResourceAsStream("/ocr/$name.json")) { "fixture $name absente" }
            .bufferedReader().readText()
        return Json { ignoreUnknownKeys = true }.decodeFromString(Fixture.serializer(), text)
    }

    @Test
    fun redExhibitionPanelKeepsTheTextAndDropsTheWatermark() {
        val fixture = load("exposition-rouge")
        val label = LabelExtractor.extract(fixture.recognized())

        assertEquals(1, label.columns.size)
        assertFalse(label.text.contains("03 20 36 05"), label.text) // numéro du filigrane
        assertFalse(label.text.contains("XXXXXXXXXX:"), label.text) // « Réalisation: »
        // Titres, commissaire, trois paragraphes : de 4 à 9 paragraphes selon l'interligne.
        val paragraphs = label.text.split("\n\n")
        assertTrue(paragraphs.size in 4..9, "${paragraphs.size} paragraphes : ${label.text}")
        assertTrue(label.ignoredLines >= 3, "ignorées : ${label.ignoredLines}")
        // Les trois grands paragraphes du corps sont là.
        assertTrue(paragraphs.count { it.length > 120 } >= 2, label.text)
    }

    @Test
    fun lowResolutionBannerDropsVerticalTextAndGarbageAndMergesFragments() {
        val fixture = load("banniere-basse-resolution")
        val label = LabelExtractor.extract(fixture.recognized())

        assertEquals(1, label.columns.size)
        // Le texte vertical (« ISSANCE DE ») et la ligne à confiance 0,30 sont écartés : au moins 2 lignes ignorées.
        assertTrue(label.ignoredLines >= 2, "ignorées : ${label.ignoredLines}")
        // Les morceaux d'une même ligne (« pour » « ancêtres » « des ») sont recollés : moins de lignes que de morceaux.
        assertTrue(label.text.lines().count { it.isNotBlank() } < fixture.lines.size - label.ignoredLines)
        assertTrue(label.text.split("\n\n").size >= 2, label.text)
    }

    @Test
    fun darkRoomWithTwoColumnsGivesTitlesAndTwoColumns() {
        val fixture = load("salle-sombre-deux-colonnes")
        val label = LabelExtractor.extract(fixture.recognized())

        assertEquals(2, label.columns.size, label.text)
        // Titre et sous-titre forment un seul bloc d'en-tête.
        assertTrue(label.header.isNotBlank() && label.header.split("\n\n").size == 1, label.header)
        // La colonne de gauche (français) est la plus fournie ; celle de droite est partielle (OCR incomplet).
        assertTrue(label.columns[0].length > label.columns[1].length)
        assertTrue(label.columns[1].isNotBlank())
    }
}
