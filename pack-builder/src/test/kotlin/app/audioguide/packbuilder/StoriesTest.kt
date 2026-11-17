package app.audioguide.packbuilder

import app.audioguide.catalog.ArtworkDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoriesTest {
    private val olympia = WikidataWork("Q152509", "RF 644", "Olympia", listOf("Édouard Manet"), 60, "Olympia (Manet)")
    private val bust = ArtworkDraft(reference = "b", inventoryNo = "RF 644", title = "Henri Adrien Prévost de Longpérier", artist = "Jean Jules Cambos")
    private val painting = ArtworkDraft(reference = "p", inventoryNo = "RF 644", title = "Olympia", artist = "Édouard Manet")

    @Test
    fun theInventoryNumberAloneIsNotEnough() {
        assertNull(WorkMatcher.match(bust, listOf(olympia)))
        assertEquals("Q152509", WorkMatcher.match(painting, listOf(olympia))?.id)
    }

    @Test
    fun aCloseTitleConfirmsWhenTheArtistIsUnknown() {
        val draft = ArtworkDraft(reference = "r", inventoryNo = "Ma 399", title = "Vénus de Milo")
        val venus = WikidataWork("Q151952", "Ma 399", "Vénus de Milo", emptyList(), 90, "Vénus de Milo")
        assertNotNull(WorkMatcher.match(draft, listOf(venus)))
        val other = venus.copy(label = "Le Prophète Isaïe")
        assertNull(WorkMatcher.match(draft, listOf(other)))
    }

    @Test
    fun theArtistNameMatchesWhateverTheOrder() {
        val draft = painting.copy(title = "Femme nue couchée", artist = "Manet Édouard")
        assertNotNull(WorkMatcher.match(draft, listOf(olympia)))
    }

    @Test
    fun aDuplicateWikidataItemResolvesToTheBestKnown() {
        val duplicate = olympia.copy(id = "Q999", sitelinks = 2)
        assertEquals("Q152509", WorkMatcher.match(painting, listOf(duplicate, olympia))?.id)
    }

    @Test
    fun formatsTheIntroForListening() {
        val intro = "Olympia () est un tableau d'Édouard Manet peint en 1863.\n\nCourt.\n" +
            "Le tableau fit scandale au Salon de 1865, où il fut exposé pour la première fois."
        assertEquals(
            "Olympia est un tableau d'Édouard Manet peint en 1863.\n" +
                "Le tableau fit scandale au Salon de 1865, où il fut exposé pour la première fois.",
            StoryFormatter.format(intro),
        )
        assertNull(StoryFormatter.format("Trop court."))
        assertEquals(
            "La Joconde (en italien : La Gioconda ou Monna Lisa) est un tableau de Léonard de Vinci.",
            StoryFormatter.format("La Joconde (en italien : La Gioconda [la dʒoˈkonda] ou Monna Lisa [ˈmɔnna ˈliːza]) est un tableau de Léonard de Vinci."),
        )
    }

    @Test
    fun cutsALongIntroAtASentence() {
        val sentence = "Ceci est une phrase assez longue pour remplir le récit d'une œuvre célèbre. "
        val story = assertNotNull(StoryFormatter.format(sentence.repeat(60)))
        assertTrue(story.length <= StoryFormatter.MAX_CHARS, "${story.length}")
        assertTrue(story.endsWith("célèbre."), story)
    }

    @Test
    fun followsRedirectsAndNormalizedTitles() {
        val body = """
            {"batchcomplete":true,"query":{
              "normalized":[{"fromencoded":false,"from":"olympia (Manet)","to":"Olympia (Manet)"}],
              "redirects":[{"from":"Olympia (Manet)","to":"Olympia (tableau)"}],
              "pages":[{"pageid":1,"ns":0,"title":"Olympia (tableau)","extract":"Olympia est un tableau."},
                       {"ns":0,"title":"Absent","missing":true}]}}
        """.trimIndent()
        assertEquals(
            mapOf("olympia (Manet)" to "Olympia est un tableau."),
            WikimediaApi.parseIntros(body, listOf("olympia (Manet)", "Absent")),
        )
    }

    @Test
    fun parsesSparqlResults() {
        val body = """
            {"head":{"vars":[]},"results":{"bindings":[
              {"item":{"type":"uri","value":"http://www.wikidata.org/entity/Q152509"},"inv":{"type":"literal","value":"RF 644"},
               "label":{"type":"literal","value":"Olympia"},"links":{"type":"literal","value":"60"},
               "title":{"type":"literal","value":"Olympia (Manet)"},"creators":{"type":"literal","value":"Édouard Manet"}},
              {"item":{"type":"uri","value":"http://www.wikidata.org/entity/Q1"},"inv":{"type":"literal","value":"RF 1"},
               "links":{"type":"literal","value":"0"},"creators":{"type":"literal","value":""}}]}}
        """.trimIndent()
        val works = WikimediaApi.parseWorks(body)
        assertEquals(olympia, works[0])
        assertEquals(WikidataWork("Q1", "RF 1", null, emptyList(), 0, null), works[1])
    }

    @Test
    fun articleUrlsAreEncoded() {
        assertEquals("https://fr.wikipedia.org/wiki/Olympia_%28Manet%29", StoryEnricher.articleUrl("Olympia (Manet)"))
    }
}
