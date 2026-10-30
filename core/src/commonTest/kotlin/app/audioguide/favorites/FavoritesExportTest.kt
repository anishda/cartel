package app.audioguide.favorites

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FavoritesExportTest {
    private fun favorite(id: Long, title: String, museum: String?, kind: FavoriteKind, note: String? = null, createdAt: Long = id) = Favorite(
        id = id, kind = kind, museumId = museum?.lowercase(), museumName = museum, reference = if (museum != null) "r$id" else null,
        title = title, subtitle = if (museum != null) "Artiste · 1900" else null, body = "Texte lu de $title.", languageTag = "fr-FR",
        note = note, createdAt = createdAt,
    )

    @Test
    fun emptyExportHasOnlyTheHeader() {
        assertEquals("# Favoris — Audioguide\n\n_Exporté le 2026-10-12_\n", FavoritesExport.markdown(emptyList(), "2026-10-12"))
    }

    @Test
    fun groupsByMuseumInAlphabeticalOrderThenCartels() {
        val text = FavoritesExport.markdown(
            listOf(
                favorite(1, "Un cartel libre", null, FavoriteKind.Cartel),
                favorite(2, "La Joconde", "musée du Louvre", FavoriteKind.Artwork),
                favorite(3, "Olympia", "musée d'Orsay", FavoriteKind.Artwork),
            ),
            exportedOn = "2026-10-12",
        )
        val headings = text.lines().filter { it.startsWith("## ") }
        assertEquals(listOf("## musée d'Orsay", "## musée du Louvre", "## Cartels scannés"), headings)
    }

    @Test
    fun withinAMuseumOldestFirstSoAVisitReadsInOrder() {
        val text = FavoritesExport.markdown(
            listOf(
                favorite(1, "Second", "musée d'Orsay", FavoriteKind.Artwork, createdAt = 200),
                favorite(2, "Premier", "musée d'Orsay", FavoriteKind.Artwork, createdAt = 100),
            ),
            "2026-10-12",
        )
        assertTrue(text.indexOf("### Premier") < text.indexOf("### Second"), text)
    }

    @Test
    fun notesAreQuotedAndCartelTextIsFenced() {
        val text = FavoritesExport.markdown(
            listOf(
                favorite(1, "Olympia", "musée d'Orsay", FavoriteKind.Artwork, note = "Vu avec Maman\nsalle 14"),
                favorite(2, "Un cartel", null, FavoriteKind.Cartel),
            ),
            "2026-10-12",
        )
        assertTrue(text.contains("> Vu avec Maman\n> salle 14"), text)
        assertTrue(text.contains("```\nTexte lu de Un cartel.\n```"), text)
    }

    @Test
    fun labelsCanBeTranslated() {
        val text = FavoritesExport.markdown(
            listOf(favorite(1, "A label", null, FavoriteKind.Cartel)),
            "2026-10-12", title = "Favorites", exportedLabel = "Exported on", cartelsLabel = "Scanned labels",
        )
        assertTrue(text.startsWith("# Favorites\n\n_Exported on 2026-10-12_"), text)
        assertTrue(text.contains("## Scanned labels"))
    }
}
