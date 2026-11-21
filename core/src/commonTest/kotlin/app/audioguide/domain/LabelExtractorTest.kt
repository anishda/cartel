package app.audioguide.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LabelExtractorTest {
    /** Ligne placée sur une image de 1000 × 1000 : `x`, `y`, `w`, `h` en fractions. */
    private fun line(text: String, x: Float, y: Float, w: Float, h: Float = 0.02f, confidence: Float = 1f) =
        TextLine(text, x, y, x + w, y + h, confidence)

    private fun extract(vararg lines: TextLine) = LabelExtractor.extract(RecognizedText(lines.toList(), 1000, 1000))

    /** Un paragraphe de [count] lignes de même largeur, à partir de [y], interligne [pitch]. */
    private fun paragraph(prefix: String, y: Float, count: Int, x: Float = 0.2f, w: Float = 0.5f, pitch: Float = 0.03f) =
        (0 until count).map { line("$prefix ligne ${it + 1} du texte courant", x, y + it * pitch, w) }

    @Test
    fun stackedParagraphsAreRebuiltFromTheLineSpacing() {
        val lines = paragraph("Premier", 0.30f, 3) + paragraph("Second", 0.30f + 3 * 0.03f + 0.05f, 2)
        val label = extract(*lines.toTypedArray())
        assertEquals(1, label.columns.size)
        val paragraphs = label.columns.single().split("\n\n")
        assertEquals(2, paragraphs.size, label.columns.single())
        assertEquals("Premier ligne 1 du texte courant Premier ligne 2 du texte courant Premier ligne 3 du texte courant", paragraphs[0])
        assertEquals(0, label.ignoredLines)
    }

    @Test
    fun aLowercaseLineAfterAnUnfinishedSentenceContinuesTheParagraph() {
        val label = extract(
            line("Un texte de plusieurs lignes qui présente les", 0.2f, 0.30f, 0.5f),
            line("trois éléments essentiels de la composition", 0.2f, 0.33f, 0.5f),
            line("et que la photo sépare de la fin par un blanc", 0.2f, 0.36f, 0.5f),
            line("essentiels.", 0.2f, 0.42f, 0.1f),
            line("Une autre idée commence ici sur une nouvelle", 0.2f, 0.48f, 0.5f),
            line("ligne et finit un peu plus loin sur la suivante.", 0.2f, 0.51f, 0.5f),
        )
        val paragraphs = label.text.split("\n\n")
        assertEquals(2, paragraphs.size, label.text)
        assertTrue(paragraphs[0].endsWith("un blanc essentiels."), label.text)
    }

    @Test
    fun theLineTextKeepsTheOcrOrderOfEveryRetainedLine() {
        val label = extract(*paragraph("Texte", 0.30f, 3).toTypedArray(), line("www.exemple.fr", 0.2f, 0.60f, 0.3f))
        assertEquals(listOf("Texte ligne 1 du texte courant", "Texte ligne 2 du texte courant", "Texte ligne 3 du texte courant"), label.lineText.lines())
    }

    @Test
    fun wordsCutAtTheEndOfALineAreJoined() {
        val label = extract(
            line("Un tableau peint à l'huile sur une pein-", 0.2f, 0.30f, 0.5f),
            line("ture ancienne et très précieuse pour nous", 0.2f, 0.33f, 0.5f),
            line("Une dernière ligne pour finir le texte ici", 0.2f, 0.36f, 0.5f),
        )
        assertTrue(label.text.contains("peinture ancienne"), label.text)
        assertFalse(label.text.contains("pein-"), label.text)
    }

    @Test
    fun fragmentsOfTheSameLineAreMerged() {
        val label = extract(
            line("Les premiers canons avaient", 0.2f, 0.30f, 0.5f),
            line("pour", 0.20f, 0.33f, 0.06f),
            line("ancêtres", 0.28f, 0.33f, 0.10f),
            line("des", 0.40f, 0.33f, 0.04f),
            line("armes qui lançaient des projectiles", 0.2f, 0.36f, 0.5f),
        )
        assertTrue(label.text.contains("pour ancêtres des"), label.text)
    }

    @Test
    fun noiseIsDroppedWhateverItsPlace() {
        val body = paragraph("Texte", 0.30f, 4)
        val label = extract(
            *body.toTypedArray(),
            line("03 20 36 05 05", 0.20f, 0.52f, 0.2f, confidence = 1f), // téléphone
            line("www.exemple-musee.fr", 0.20f, 0.45f, 0.3f), // adresse web
            line("contact@musee.fr", 0.20f, 0.46f, 0.3f), // courriel
            line("~|.:", 0.20f, 0.47f, 0.1f), // pas du texte
            line("Conon fur unsse", 0.20f, 0.48f, 0.2f, confidence = 0.2f), // OCR douteux
            line("ISSANCE DE LARTILLERIE", 0.05f, 0.30f, 0.02f, h = 0.2f), // texte vertical
        )
        assertEquals(4, Regex("""ligne \d du texte courant""").findAll(label.text).count(), label.text)
        assertFalse(label.text.contains("03 20"), label.text)
        assertFalse(label.text.contains("www."), label.text)
        assertFalse(label.text.contains("@"), label.text)
        assertFalse(label.text.contains("Conon"), label.text)
        assertFalse(label.text.contains("ISSANCE"), label.text)
        assertEquals(6, label.ignoredLines)
    }

    @Test
    fun tinyCaptionsAndLogosBesideTheTextAreDropped() {
        val label = extract(
            *paragraph("Corps", 0.30f, 4).toTypedArray(),
            line("FedEx", 0.30f, 0.46f, 0.05f, h = 0.006f),
            line("Office du Tourisme", 0.40f, 0.46f, 0.1f, h = 0.007f),
        )
        assertFalse(label.text.contains("FedEx"), label.text)
        assertFalse(label.text.contains("Tourisme"), label.text)
    }

    @Test
    fun theMainCentredBlockBeatsAFarSmallOne() {
        val label = extract(
            *paragraph("Cartel", 0.35f, 5).toTypedArray(),
            line("Réalisation :", 0.02f, 0.93f, 0.12f),
            line("Publicité Durand", 0.02f, 0.96f, 0.15f),
        )
        assertFalse(label.text.contains("Réalisation"), label.text)
        assertEquals(2, label.ignoredLines)
    }

    @Test
    fun twoSubstantialBlocksAreBothKeptSideBySide() {
        val label = extract(
            *paragraph("Gauche", 0.30f, 6, x = 0.05f, w = 0.35f).toTypedArray(),
            *paragraph("Droite", 0.30f, 6, x = 0.60f, w = 0.35f).toTypedArray(),
        )
        assertEquals(2, label.columns.size)
        assertTrue(label.columns[0].contains("Gauche") && !label.columns[0].contains("Droite"))
        assertTrue(label.columns[1].contains("Droite"))
    }

    @Test
    fun columnsAreReadOneAfterTheOtherWithTheTitlesFirst() {
        val left = paragraph("Francais", 0.40f, 6, x = 0.05f, w = 0.20f)
        val right = paragraph("Anglais", 0.40f, 6, x = 0.29f, w = 0.20f)
        val label = extract(
            line("TITRE DE L'EXPOSITION", 0.10f, 0.25f, 0.30f, h = 0.04f),
            line("un sous-titre qui surplombe", 0.12f, 0.31f, 0.25f, h = 0.03f),
            *(left + right).toTypedArray(),
        )
        assertEquals(2, label.columns.size, label.text)
        assertTrue(label.header.contains("TITRE") && label.header.contains("sous-titre"), label.header)
        assertTrue(label.columns[0].contains("Francais") && !label.columns[0].contains("Anglais"), label.columns[0])
        assertTrue(label.columns[1].contains("Anglais") && !label.columns[1].contains("Francais"), label.columns[1])
        assertTrue(label.text.indexOf("TITRE") < label.text.indexOf("Francais"))
        assertTrue(label.text.indexOf("Francais") < label.text.indexOf("Anglais"))
    }

    @Test
    fun aSingleColumnIsNotSplit() {
        val label = extract(*paragraph("Seul", 0.30f, 8).toTypedArray())
        assertEquals(1, label.columns.size)
        assertEquals("", label.header)
    }

    @Test
    fun emptyOrAllNoiseGivesAnEmptyLabel() {
        assertTrue(extract().isEmpty)
        assertTrue(extract(line("~~", 0.2f, 0.3f, 0.1f), line("03 20 36 05 05", 0.2f, 0.4f, 0.3f)).isEmpty)
    }

    @Test
    fun plainTextWithoutPositionsStillWorks() {
        val label = LabelExtractor.extract(RecognizedText.fromPlainText("Olympia\n1863\nHuile sur toile et un peu plus de texte pour la ligne"))
        assertTrue(label.text.contains("Olympia"), label.text)
    }

    @Test
    fun aHeadingInsideTheTextBecomesItsOwnParagraph() {
        val label = extract(
            line("Ils ne se dérèglent jamais puisqu'ils fonctionnent", 0.2f, 0.30f, 0.5f),
            line("grâce à l'ombre que projette le soleil.", 0.2f, 0.33f, 0.4f),
            line("La bonne heure dans le bon sens", 0.2f, 0.36f, 0.3f),
            line("En observant le ciel, on voit que le soleil", 0.2f, 0.39f, 0.5f),
            line("ne se trouve pas à la même place le soir.", 0.2f, 0.42f, 0.5f),
        )
        val paragraphs = label.text.split("\n\n")
        assertEquals(3, paragraphs.size, label.text)
        assertEquals("La bonne heure dans le bon sens", paragraphs[1])
    }
}
