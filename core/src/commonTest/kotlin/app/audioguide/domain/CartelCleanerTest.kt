package app.audioguide.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CartelCleanerTest {
    @Test
    fun removesInventoryOnlyLine() {
        val raw = "La Joconde\nLéonard de Vinci\nINV 779"
        assertEquals("La Joconde\nLéonard de Vinci", CartelCleaner.clean(raw))
    }

    @Test
    fun removesInlineInventoryWithPrefix() {
        val cleaned = CartelCleaner.clean("Olympia, 1863. Paris, musée d'Orsay. Inv. RF 644")
        assertFalse(cleaned.contains("RF"), cleaned)
        assertFalse(cleaned.contains("644"), cleaned)
    }

    @Test
    fun removesCompositeInventoryNumbers() {
        assertEquals("Le repas", CartelCleaner.clean("Le repas\nRF 1954 27"))
        assertEquals("Portrait", CartelCleaner.clean("Portrait\nINV 20400 F"))
        assertEquals("Fruits", CartelCleaner.clean("Fruits\nRF 1960-10"))
    }

    @Test
    fun joinsHyphenatedLineBreak() {
        assertEquals("une belle peinture à l'huile", CartelCleaner.clean("une belle pein-\nture à l'huile"))
    }

    @Test
    fun reflowsLinesWithoutSentenceEnd() {
        assertEquals(
            "Commandé par le marchand florentin pour sa femme.\nExposé au Salon.",
            CartelCleaner.clean("Commandé par le marchand\nflorentin pour sa femme.\nExposé au Salon."),
        )
    }

    @Test
    fun expandsYearRanges() {
        assertEquals("Léonard de Vinci (de 1452 à 1519)", CartelCleaner.clean("Léonard de Vinci (1452-1519)"))
        assertEquals("vers de 1503 à 1519", CartelCleaner.clean("vers 1503–1519"))
    }

    @Test
    fun doesNotDuplicateLeadingDe() {
        assertEquals("peint de 1503 à 1519", CartelCleaner.clean("peint de 1503-1519"))
    }

    @Test
    fun leavesNonYearRangesAlone() {
        assertEquals("pages 12-14", CartelCleaner.clean("pages 12-14"))
        assertEquals("de 1519-1503", CartelCleaner.clean("de 1519-1503"))
    }

    @Test
    fun expandsCenturies() {
        assertEquals("seizième siècle", CartelCleaner.clean("XVIe siècle"))
        assertEquals("dix-neuvième siècle", CartelCleaner.clean("XIXème siècle"))
        assertEquals("premier siècle", CartelCleaner.clean("Ier siècle"))
        assertEquals("vingtième et vingt et unième siècles", CartelCleaner.clean("XXe et XXIe siècles"))
    }

    @Test
    fun expandsDimensions() {
        assertEquals(
            "hauteur 77 centimètres ; largeur 53 centimètres",
            CartelCleaner.clean("H. 77 cm ; L. 53 cm"),
        )
    }

    @Test
    fun fixesZeroReadAsLetterInMeasures() {
        assertEquals("largeur 190 centimètres", CartelCleaner.clean("L. 19o cm"))
        assertEquals("hauteur 130 centimètres", CartelCleaner.clean("H. 13O cm"))
    }

    @Test
    fun expandsAbbreviations() {
        assertEquals("environ 1503", CartelCleaner.clean("env. 1503"))
        assertEquals("44 avant Jésus-Christ", CartelCleaner.clean("44 av. J.-C."))
    }

    @Test
    fun collapsesWhitespace() {
        assertEquals("Titre de l'œuvre\nAuteur", CartelCleaner.clean("  Titre   de  l'œuvre \n  Auteur  "))
    }

    @Test
    fun blankLinesStayAsParagraphBreaks() {
        assertEquals("Titre de l'œuvre\n\nAuteur", CartelCleaner.clean("  Titre   de  l'œuvre \n\n\n  Auteur  "))
        assertEquals("Premier paragraphe.\n\nSecond.", CartelCleaner.clean("Premier paragraphe.\n \nSecond."))
    }

    @Test
    fun linesAreNotReflowedAcrossParagraphs() {
        assertEquals("fin sans point\n\nsuite en minuscule", CartelCleaner.clean("fin sans point\n\nsuite en minuscule"))
    }

    @Test
    fun aParagraphEmptiedByCleaningDisappears() {
        assertEquals("Texte", CartelCleaner.clean("Texte\n\nINV 779\n\n"))
    }

    @Test
    fun jocondeDatesReadAsSentences() {
        assertEquals("premier quart du seizième siècle", CartelCleaner.clean("1er quart XVIe siècle"))
        assertEquals("troisième quart du dix-septième siècle", CartelCleaner.clean("3e quart XVIIe siècle"))
        assertEquals("deuxième tiers du dix-neuvième siècle", CartelCleaner.clean("2e tiers XIXe siècle"))
        assertEquals("première moitié du dix-huitième siècle", CartelCleaner.clean("1ère moitié XVIIIe siècle"))
        assertEquals("deuxième moitié du dix-neuvième siècle", CartelCleaner.clean("2e moitié du XIXe siècle"))
        assertEquals("début du vingtième siècle", CartelCleaner.clean("début XXe siècle"))
        assertEquals("milieu du dix-septième siècle", CartelCleaner.clean("milieu XVIIe siècle"))
        assertEquals("fin du siècle dernier", CartelCleaner.clean("fin du siècle dernier"))
    }
}

