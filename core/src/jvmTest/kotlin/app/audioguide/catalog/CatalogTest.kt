package app.audioguide.catalog

import app.audioguide.packs.PackStore
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class CatalogTest {
    private val root = Files.createTempDirectory("audioguide-core-test")

    @AfterTest
    fun cleanUp() = root.deleteRecursively()

    private fun buildPack(file: String, museumId: String, museumName: String, vararg drafts: ArtworkDraft): String {
        val path = root.resolve(file).toString()
        PackWriter.create(path).use { writer ->
            drafts.forEach(writer::add)
            writer.finish(
                PackInfo(
                    museumId = museumId, museumName = museumName, city = "Paris", version = "2026-10-11",
                    builtAt = "2026-10-11T10:00:00Z", sourceName = "Joconde", sourceUrl = null,
                    license = "Licence Ouverte 2.0", sourceUpdatedAt = "2026-10-07", recordCount = 0,
                ),
            )
        }
        return path
    }

    private val olympia = ArtworkDraft(
        reference = "M5060-1", inventoryNo = "RF 644", title = "Olympia", artist = "Édouard Manet",
        dateText = "1863", domain = "peinture", heightCm = 130.5, widthCm = 191.0,
    )
    private val bust = ArtworkDraft(reference = "M5060-2", inventoryNo = "RF 644", title = "Henri Adrien Prévost de Longpérier", artist = "Jean Jules Cambos", domain = "sculpture")
    private val dejeuner = ArtworkDraft(reference = "M5060-3", inventoryNo = "RF 1668", title = "Le Déjeuner sur l'herbe", artist = "Édouard Manet", domain = "peinture")
    private val joconde = ArtworkDraft(reference = "M5031-1", inventoryNo = "INV 779", title = "La Joconde", artist = "Léonard de Vinci", domain = "peinture")

    @Test
    fun keepsWikidataLinkAndStory() = runTest {
        val story = ArtworkStory("Olympia est un tableau d'Édouard Manet.", "Olympia (Manet)", "https://fr.wikipedia.org/wiki/Olympia_(Manet)")
        val path = root.resolve("v2.pack.db").toString()
        PackWriter.create(path).use { writer ->
            writer.add(olympia.copy(wikidataId = "Q152509", notability = 60, story = story))
            writer.finish(
                PackInfo(
                    museumId = "orsay", museumName = "musée d'Orsay", city = "Paris", version = "v",
                    builtAt = "b", sourceName = "Joconde", sourceUrl = null, license = "LO 2.0",
                    sourceUpdatedAt = null, recordCount = 0, storyLicense = "CC BY-SA 4.0",
                ),
            )
        }
        val pack = Pack.open(path)
        assertEquals("CC BY-SA 4.0", pack.info.storyLicense)
        val found = assertNotNull(pack.byReference("M5060-1"))
        assertEquals("Q152509", found.wikidataId)
        assertEquals(60, found.notability)
        assertEquals(story, found.story)
        pack.close()
    }

    @Test
    fun stillReadsVersionOnePacks() = runTest {
        val path = buildPack("v1.pack.db", "orsay", "musée d'Orsay", olympia, dejeuner)
        SqliteDb.open(path).use { db ->
            listOf("wikidata_id", "notability", "story_text", "story_title", "story_url", "image_file", "image_author", "image_license")
                .forEach { db.exec("ALTER TABLE artwork DROP COLUMN $it") }
            db.exec("PRAGMA user_version = 1")
        }
        val pack = Pack.open(path)
        val found = assertNotNull(pack.byReference("M5060-1"))
        assertEquals(null, found.story)
        assertEquals(0, found.notability)
        assertEquals(2, pack.search("manet").size)
        pack.close()
    }

    @Test
    fun keepsTheCommonsImageCredit() = runTest {
        val image = ArtworkImage("Olympia by Manet.jpg", "Édouard Manet", "Public domain")
        val pack = Pack.open(buildPack("img.pack.db", "orsay", "musée d'Orsay", olympia.copy(image = image), dejeuner))
        assertEquals(image, pack.byReference("M5060-1")?.image)
        assertEquals(null, pack.byReference("M5060-3")?.image)
        pack.close()
    }

    @Test
    fun stillReadsVersionTwoPacks() = runTest {
        val path = buildPack("v2old.pack.db", "orsay", "musée d'Orsay", olympia.copy(story = ArtworkStory("Un tableau de Manet.", "Olympia", "u")), dejeuner)
        SqliteDb.open(path).use { db ->
            listOf("image_file", "image_author", "image_license").forEach { db.exec("ALTER TABLE artwork DROP COLUMN $it") }
            db.exec("PRAGMA user_version = 2")
        }
        val pack = Pack.open(path)
        val found = assertNotNull(pack.byReference("M5060-1"))
        assertEquals("Olympia", found.story?.articleTitle)
        assertEquals(null, found.image)
        pack.close()
    }

    @Test
    fun famousWorksComeFirstAmongEqualMatches() = runTest {
        val obscure = ArtworkDraft(reference = "M5060-9", inventoryNo = "RF 9", title = "Aaa portrait", artist = "Édouard Manet")
        val pack = Pack.open(buildPack("rank.pack.db", "orsay", "musée d'Orsay", obscure, olympia.copy(notability = 60), dejeuner.copy(hasImage = true)))
        assertEquals(listOf("Olympia", "Le Déjeuner sur l'herbe", "Aaa portrait"), pack.search("manet").map { it.artwork.title })
        pack.close()
    }

    @Test
    fun writesAndReadsAPack() = runTest {
        val pack = Pack.open(buildPack("orsay.pack.db", "orsay", "musée d'Orsay", olympia, dejeuner))
        assertEquals("orsay", pack.info.museumId)
        assertEquals(2, pack.info.recordCount)
        assertEquals(2, pack.count())

        val found = assertNotNull(pack.byReference("M5060-1"))
        assertEquals("Olympia", found.title)
        assertEquals("Édouard Manet", found.artist)
        assertEquals(130.5, found.heightCm)
        assertEquals("orsay", found.museumId)
        pack.close()
    }

    @Test
    fun rejectsAFileThatIsNotAPack() = runTest {
        val path = root.resolve("garbage.db").toString()
        SqliteDb.open(path).use { it.exec("CREATE TABLE x(a)") }
        assertFailsWith<IllegalArgumentException> { Pack.open(path) }
    }

    @Test
    fun searchIgnoresAccentsCaseAndWordOrder() = runTest {
        val pack = Pack.open(buildPack("orsay.pack.db", "orsay", "musée d'Orsay", olympia, dejeuner, bust))
        assertEquals(listOf("Le Déjeuner sur l'herbe"), pack.search("dejeuner herbe").map { it.artwork.title })
        assertEquals(listOf("Olympia", "Le Déjeuner sur l'herbe").sorted(), pack.search("MANET").map { it.artwork.title }.sorted())
        assertEquals(listOf("Olympia"), pack.search("manet olympia").map { it.artwork.title })
        assertTrue(pack.search("introuvable").isEmpty())
        assertTrue(pack.search("   ").isEmpty())
        pack.close()
    }

    @Test
    fun searchByInventoryNumberInAnyWriting() = runTest {
        val pack = Pack.open(buildPack("orsay.pack.db", "orsay", "musée d'Orsay", olympia, dejeuner))
        assertEquals("Olympia", pack.search("RF 644").first().artwork.title)
        assertEquals("Olympia", pack.search("rf644").first().artwork.title)
        assertEquals("Le Déjeuner sur l'herbe", pack.search("rf 1668").first().artwork.title)
        pack.close()
    }

    @Test
    fun exactTitleRanksFirst() = runTest {
        val pack = Pack.open(
            buildPack(
                "orsay.pack.db", "orsay", "musée d'Orsay",
                ArtworkDraft(reference = "a", inventoryNo = "RF 1", title = "Olympia, étude"),
                olympia,
            ),
        )
        assertEquals(listOf("Olympia", "Olympia, étude"), pack.search("olympia").map { it.artwork.title })
        pack.close()
    }

    @Test
    fun catalogSearchesAcrossPacksAndFiltersByMuseum() = runTest {
        val store = PackStore(FileSystem.SYSTEM, root.resolve("packs").toOkioPath())
        val orsay = buildPack("src-orsay.db", "orsay", "musée d'Orsay", olympia)
        val louvre = buildPack("src-louvre.db", "louvre", "musée du Louvre", joconde)
        store.install("orsay", orsay.toOkio(), expectedSha256 = null)
        store.install("louvre", louvre.toOkio(), expectedSha256 = null)

        // Triés par nom : « musée d'Orsay » précède « musée du Louvre ».
        assertEquals(listOf("orsay", "louvre"), store.installed.value.map { it.museumId })
        val catalog = Catalog(store)
        assertEquals(setOf("Olympia", "La Joconde"), catalog.search("o").map { it.title }.toSet())
        assertEquals(listOf("La Joconde"), catalog.search("joconde", museumId = "louvre").map { it.title })
        assertTrue(catalog.search("joconde", museumId = "orsay").isEmpty())
        assertEquals("Olympia", catalog.artwork("orsay", "M5060-1")?.title)
    }

    @Test
    fun installRejectsABadChecksum() = runTest {
        val store = PackStore(FileSystem.SYSTEM, root.resolve("packs").toOkioPath())
        val source = buildPack("src.db", "orsay", "musée d'Orsay", olympia)
        assertFailsWith<IllegalArgumentException> { store.install("orsay", source.toOkio(), expectedSha256 = "00".repeat(32)) }
        assertTrue(store.installed.value.isEmpty())
    }

    @Test
    fun removeDeletesThePack() = runTest {
        val store = PackStore(FileSystem.SYSTEM, root.resolve("packs").toOkioPath())
        store.install("orsay", buildPack("src.db", "orsay", "musée d'Orsay", olympia).toOkio(), null)
        assertEquals(1, store.installed.value.size)
        store.remove("orsay")
        assertTrue(store.installed.value.isEmpty())
    }

    @Test
    fun matchesACartelByInventoryTitleAndArtist() = runTest {
        val store = PackStore(FileSystem.SYSTEM, root.resolve("packs").toOkioPath())
        store.install("orsay", buildPack("src.db", "orsay", "musée d'Orsay", olympia, bust, dejeuner).toOkio(), null)
        val catalog = Catalog(store)

        val matches = catalog.match("Édouard MANET (1832-1883)\nOlympia\n1863\nHuile sur toile\nParis, musée d'Orsay. Inv. RF 644")
        assertEquals("Olympia", matches.first().artwork.title)
        assertTrue(matches.first().score >= 150, matches.first().score.toString())
    }

    @Test
    fun matchesByTitleAloneWhenNoInventory() = runTest {
        val store = PackStore(FileSystem.SYSTEM, root.resolve("packs").toOkioPath())
        store.install("orsay", buildPack("src.db", "orsay", "musée d'Orsay", olympia, dejeuner).toOkio(), null)
        val matches = Catalog(store).match("Le déjeuner sur l'herbe\n1863\nHuile sur toile")
        assertEquals("Le Déjeuner sur l'herbe", matches.first().artwork.title)
    }

    @Test
    fun matchesALongOfficialTitleFromTheShortNameOnTheCartel() = runTest {
        val store = PackStore(FileSystem.SYSTEM, root.resolve("packs").toOkioPath())
        val longTitle = joconde.copy(title = "Portrait de Lisa Gherardini, épouse de Francesco del Giocondo, dit La Joconde ou Monna Lisa")
        store.install("louvre", buildPack("src.db", "louvre", "musée du Louvre", longTitle).toOkio(), null)
        val matches = Catalog(store).match("La Joconde\nLéonard de Vinci\n1503-1519")
        assertEquals(longTitle.title, matches.first().artwork.title)
    }

    @Test
    fun noMatchForUnknownCartelOrEmptyCatalog() = runTest {
        val store = PackStore(FileSystem.SYSTEM, root.resolve("packs").toOkioPath())
        assertTrue(Catalog(store).match("Olympia").isEmpty())
        store.install("orsay", buildPack("src.db", "orsay", "musée d'Orsay", olympia).toOkio(), null)
        assertTrue(Catalog(store).match("Une œuvre inconnue\nRF 99999").isEmpty())
    }

    private fun String.toOkio() = java.nio.file.Path.of(this).toOkioPath()
}
