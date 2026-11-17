package app.audioguide.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtworkNarrationTest {
    private val joconde = Artwork(
        id = 1, museumId = "louvre", reference = "r", inventoryNo = "INV 779",
        title = "La Joconde", artist = "Léonard de Vinci", dateText = "1er quart XVIe siècle",
        domain = "peinture", denomination = "tableau", school = "Italie", technique = "peinture à l'huile, bois",
        heightCm = 77.0, widthCm = 53.0, location = "département des Peintures",
        provenance = "commandé par le florentin Francesco del Giocondo", description = null, hasImage = true,
    )

    @Test
    fun readsTheEssentialsInOrder() {
        val text = ArtworkNarration.build(joconde, "musée du Louvre")
        val lines = text.lines()
        assertEquals("La Joconde.", lines[0])
        assertEquals("Par Léonard de Vinci.", lines[1])
        assertTrue(text.contains("Dimensions : 77 centimètres de haut sur 53 de large."), text)
        assertTrue(text.contains("Conservé au musée du Louvre, département des Peintures."), text)
        assertTrue(text.contains("Historique : commandé par le florentin Francesco del Giocondo."), text)
    }

    @Test
    fun readsTheStoryBeforeTheTechnicalSheetWithItsSource() {
        val story = ArtworkStory(
            text = "La Joconde est un portrait peint par Léonard de Vinci.\nC'est l'un des tableaux les plus célèbres au monde",
            articleTitle = "La Joconde",
            articleUrl = "https://fr.wikipedia.org/wiki/La_Joconde",
        )
        val text = ArtworkNarration.build(joconde.copy(story = story), "musée du Louvre")
        val storyAt = text.indexOf("portrait peint")
        assertTrue(storyAt > text.indexOf("Par Léonard de Vinci."), text)
        assertTrue(storyAt < text.indexOf("Technique"), text)
        assertTrue(text.contains("les plus célèbres au monde."), text)
        assertTrue(text.contains("Récit d'après l'article Wikipédia « La Joconde »."), text)
    }

    @Test
    fun centuriesAreSpelledOut() {
        assertTrue(ArtworkNarration.build(joconde, "musée du Louvre").contains("seizième siècle"))
    }

    @Test
    fun neverReadsTheInventoryNumber() {
        assertFalse(ArtworkNarration.build(joconde, "musée du Louvre").contains("779"))
    }

    @Test
    fun skipsMissingParts() {
        val bare = joconde.copy(artist = null, dateText = null, school = null, technique = null, heightCm = null, widthCm = null, location = null, provenance = null)
        assertEquals("La Joconde.\nConservé au musée du Louvre.", ArtworkNarration.build(bare, "musée du Louvre"))
    }

    @Test
    fun decimalsUseACommaAndOneSideAlone() {
        assertEquals("Dimensions : 17 centimètres de haut sur 30,5 de large", ArtworkNarration.dimensions(17.0, 30.5))
        assertEquals("Hauteur : 41 centimètres", ArtworkNarration.dimensions(41.0, null))
        assertEquals(null, ArtworkNarration.dimensions(null, null))
    }
}
