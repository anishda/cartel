package app.audioguide.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NarrationPhonemesTest {
    @Test
    fun keyIsAStableSixteenDigitFnv1aHash() {
        // Valeurs de référence FNV-1a 64 bits.
        assertEquals("cbf29ce484222325", NarrationPhonemes.key(""))
        assertEquals("af63dc4c8601ec8c", NarrationPhonemes.key("a"))
        assertEquals("85944171f73967e8", NarrationPhonemes.key("foobar"))
        assertNotEquals(NarrationPhonemes.key("Méduse"), NarrationPhonemes.key("Meduse"))
    }

    private val artwork = Artwork(
        id = 1, museumId = "louvre", reference = "r", inventoryNo = "INV 1", title = "Le Radeau de la Méduse",
        artist = "Théodore Géricault", dateText = "1819", domain = null, denomination = null, school = null,
        technique = "huile sur toile", heightCm = 491.0, widthCm = 716.0, location = null, provenance = null,
        description = null, hasImage = false,
        story = ArtworkStory("Le Radeau de la Méduse est une peinture de 1819. Il est célèbre.", "Le Radeau de la Méduse", ""),
    )

    @Test
    fun segmentsCoverBothListeningsAndStaySpoken() {
        val segments = NarrationPhonemes.segments(artwork, "musée du Louvre")
        assertTrue(segments.isNotEmpty())
        // Les nombres sont écrits en lettres : le phonétiseur ne reçoit pas de chiffres.
        assertTrue(segments.none { segment -> segment.any { it.isDigit() } }, segments.toString())
        assertTrue(segments.any { "mille huit cent dix-neuf" in it }, segments.toString())
    }

    @Test
    fun theTextToPhonemizeCarriesThePronunciationFixes() {
        assertEquals("la frégate Méduse, a marqué", NarrationPhonemes.textToPhonemize("la frégate Méduse a marqué"))
    }
}
