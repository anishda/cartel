package app.audioguide.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JocondeMapperTest {
    private val louvre = "musée du Louvre"

    @Test
    fun mapsAFullRecord() {
        val draft = assertNotNull(
            JocondeMapper.toDraft(
                JocondeRecord(
                    reference = "00000050119",
                    inventoryNumber = "INV 779",
                    title = "Portrait de Lisa Gherardini, épouse de Francesco del Giocondo, dit La Joconde ou Monna Lisa",
                    author = "LEONARDO DI SER PIERO DA VINCI;VINCI Léonard de (dit)",
                    denomination = "tableau",
                    domain = "peinture;portrait",
                    school = "Italie",
                    creationPeriod = "1er quart 16e siècle",
                    materials = "peinture à l'huile;bois",
                    measures = "77 H ; 53 L",
                    location = "Paris ; musée du Louvre département des Peintures",
                    history = "commandé par le florentin Francesco del Giocondo ; gravé par Fauchery",
                    hasImage = true,
                ),
                louvre,
            ),
        )
        assertEquals("INV 779", draft.inventoryNo)
        assertEquals("Léonard de Vinci", draft.artist)
        assertEquals("1er quart XVIe siècle", draft.dateText)
        assertEquals("peinture", draft.domain)
        assertEquals("peinture à l'huile, bois", draft.technique)
        assertEquals(77.0, draft.heightCm)
        assertEquals(53.0, draft.widthCm)
        assertEquals("département des Peintures", draft.location)
        assertEquals("commandé par le florentin Francesco del Giocondo; gravé par Fauchery", draft.provenance)
        assertTrue(draft.hasImage)
    }

    @Test
    fun skipsRecordsWithoutTitleOrInventory() {
        assertNull(JocondeMapper.toDraft(JocondeRecord(reference = "1", inventoryNumber = "RF 1", title = "  "), louvre))
        assertNull(JocondeMapper.toDraft(JocondeRecord(reference = "1", inventoryNumber = null, title = "Olympia"), louvre))
        assertNull(JocondeMapper.toDraft(JocondeRecord(reference = null, inventoryNumber = "RF 1", title = "Olympia"), louvre))
    }

    @Test
    fun keepsOnlyFirstInventoryNumber() {
        val draft = assertNotNull(
            JocondeMapper.toDraft(JocondeRecord(reference = "1", inventoryNumber = "RF 644;MR 1234", title = "Olympia"), "musée d'Orsay"),
        )
        assertEquals("RF 644", draft.inventoryNo)
    }

    @Test
    fun uppercaseTitlesBecomeSentenceCase() {
        assertEquals("Le verre de vin", JocondeMapper.formatTitle("LE VERRE DE VIN"))
        assertEquals("Olympia", JocondeMapper.formatTitle("Olympia"))
        assertEquals("La Joconde", JocondeMapper.formatTitle("La Joconde"))
    }

    @Test
    fun datesPreferAYearThenAPeriod() {
        assertEquals("1863", JocondeMapper.formatDate("1863", "3e quart 19e siècle"))
        assertEquals("2e quart XVIIe siècle", JocondeMapper.formatDate("1630 vers,1635 ou", "2e quart 17e siècle"))
        assertEquals("1re moitié XIXe siècle", JocondeMapper.formatDate(null, "1ère moitié 19e siècle;3e quart 19e siècle"))
        assertNull(JocondeMapper.formatDate(null, null))
    }

    @Test
    fun parsesBothMeasureStyles() {
        assertEquals(41.0 to 52.0, JocondeMapper.parseDimensions("41 H ; 52 L"))
        assertEquals(17.0 to 30.5, JocondeMapper.parseDimensions("H. 17, l. 30.5"))
        assertEquals(400.0 to 545.0, JocondeMapper.parseDimensions("H. 400, l. 545"))
        assertEquals(null to null, JocondeMapper.parseDimensions("D. 2.9"))
        assertEquals(null to null, JocondeMapper.parseDimensions(null))
    }

    @Test
    fun locationDropsTheMuseumItself() {
        assertNull(JocondeMapper.formatLocation("Paris ; musée d'Orsay", "musée d'Orsay"))
        assertEquals("département des Sculptures", JocondeMapper.formatLocation("Paris ; musée du Louvre département des Sculptures", louvre))
        assertNull(JocondeMapper.formatLocation(null, louvre))
    }
}
