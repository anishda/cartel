package app.audioguide.catalog

import kotlin.test.Test
import kotlin.test.assertEquals

class NormalizationTest {
    @Test
    fun removesAccentsAndCase() {
        assertEquals("le dejeuner sur l herbe", normalizeForSearch("Le Déjeuner sur l'herbe"))
        assertEquals("coeur", normalizeForSearch("Cœur"))
        assertEquals("ecole francaise", normalizeForSearch("ÉCOLE FRANÇAISE"))
    }

    @Test
    fun collapsesPunctuationAndSpaces() {
        assertEquals("rf 644", normalizeForSearch("  RF   644 "))
        assertEquals("manet edouard 1832 1883", normalizeForSearch("Manet, Édouard (1832-1883)"))
        assertEquals("", normalizeForSearch("  ...  "))
    }

    @Test
    fun inventoryIgnoresSeparators() {
        assertEquals("rf195423", normalizeInventory("RF 1954-23"))
        assertEquals("rf195423", normalizeInventory("rf195423"))
        assertEquals("inv20400f", normalizeInventory("INV 20400 F"))
    }

    @Test
    fun artistNamesAreReordered() {
        assertEquals("Édouard Manet", ArtistFormatter.format("Manet Édouard (1832-1883)"))
        assertEquals("Jules Bastien-Lepage", ArtistFormatter.format("BASTIEN-LEPAGE Jules"))
        assertEquals("Albert Besnard", ArtistFormatter.format("BESNARD Albert"))
    }

    @Test
    fun artistParticlesFollowGivenName() {
        assertEquals(
            "Léonard de Vinci",
            ArtistFormatter.format("LEONARDO DI SER PIERO DA VINCI;VINCI Léonard de (dit)"),
        )
    }

    @Test
    fun artistRomanSuffixGoesLast() {
        assertEquals("Claude Audran II", ArtistFormatter.format("AUDRAN Claude II"))
    }

    @Test
    fun artistEdgeCases() {
        assertEquals(null, ArtistFormatter.format(null))
        assertEquals(null, ArtistFormatter.format("  "))
        assertEquals(null, ArtistFormatter.format("(inconnu)"))
        assertEquals("Anonyme", ArtistFormatter.format("ANONYME"))
    }
}
