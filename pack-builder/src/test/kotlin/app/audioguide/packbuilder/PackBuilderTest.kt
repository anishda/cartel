package app.audioguide.packbuilder

import app.audioguide.catalog.JocondeRecord
import app.audioguide.catalog.Pack
import app.audioguide.packs.PackManifest
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class PackBuilderTest {
    private val out = Files.createTempDirectory("pack-builder-test")

    @AfterTest
    fun cleanUp() = out.deleteRecursively()

    private class FakeSource(private val records: List<JocondeRecord>) : RecordSource {
        override fun records(museum: MuseumSpec, onPage: (Int, Int?) -> Unit) = records
        override fun sourceUpdatedAt() = "2026-10-07T01:39:01+00:00"
    }

    private val orsay = MuseumSpec("orsay", "musée d'Orsay", "Paris", "M5060")

    private val records = listOf(
        JocondeRecord(
            reference = "M5060000001", inventoryNumber = "RF 644", title = "Olympia", author = "Manet Édouard (1832-1883)",
            domain = "peinture", creationYear = "1863", measures = "H. 130.5 ; l. 191", hasImage = true,
            location = "Paris ; musée d'Orsay",
        ),
        JocondeRecord(reference = "M5060000002", inventoryNumber = "RF 2003", title = "BRAS DE SEINE PRES DE GIVERNY", author = "Monet Claude (1840-1926)", domain = "peinture"),
        JocondeRecord(reference = "M5060000003", inventoryNumber = null, title = "Notice sans inventaire"),
        JocondeRecord(reference = null, inventoryNumber = "RF 1", title = "Notice sans référence"),
    )

    @Test
    fun buildsAReadablePackAndManifest() = runBlocking {
        val manifest = PackBuilder(FakeSource(records), out, log = {}).buildAll(listOf(orsay))

        val entry = manifest.packs.single()
        assertEquals("orsay", entry.id)
        assertEquals(2, entry.recordCount, "les deux notices exploitables seulement")
        assertEquals("orsay.pack.db", entry.file)
        assertEquals(Files.size(out.resolve("orsay.pack.db")), entry.sizeBytes)
        assertEquals(64, entry.sha256.length)

        val pack = Pack.open(out.resolve("orsay.pack.db").toString())
        assertEquals("musée d'Orsay", pack.info.museumName)
        assertEquals("2026-10-07T01:39:01+00:00", pack.info.sourceUpdatedAt)
        val olympia = assertNotNull(pack.byInventory("rf644").firstOrNull())
        assertEquals("Édouard Manet", olympia.artist)
        assertEquals(130.5, olympia.heightCm)
        assertNull(olympia.location, "le musée lui-même n'est pas une localisation utile")
        assertEquals("Bras de seine pres de giverny", pack.search("giverny").single().artwork.title)
        pack.close()

        val written = PackManifest.fromJson(Files.readString(out.resolve("manifest.json")))
        assertEquals(manifest, written)
    }

    @Test
    fun rebuildingReplacesThePreviousFile() {
        val builder = PackBuilder(FakeSource(records), out, log = {})
        builder.build(orsay, null)
        val second = builder.build(orsay, null)
        assertEquals(2, second.recordCount)
    }

    @Test
    fun parsesATabularApiPage() {
        val body = """
            {"data":[{"Reference":"09880004639","Titre":"Olympia","Numero_inventaire":"RF 644","Presence_image":true,"Champ_inconnu":1,"Auteur":null}],
             "links":{"profile":"x","swagger":"y","next":"https://tabular-api.data.gouv.fr/api/resources/r/data/?page=2","prev":null},
             "meta":{"page":1,"page_size":200,"total":4111}}
        """.trimIndent()
        val page = JocondeTabularApi.parsePage(body)
        assertEquals(1, page.data.size)
        assertEquals("Olympia", page.data.first().title)
        assertEquals(true, page.data.first().hasImage)
        assertNull(page.data.first().author)
        assertTrue(page.links!!.next!!.endsWith("page=2"))
        assertEquals(4111, page.meta!!.total)
    }

    @Test
    fun lastPageHasNoNextLink() {
        val page = JocondeTabularApi.parsePage("""{"data":[],"links":{"next":null},"meta":{"total":0}}""")
        assertNull(page.links!!.next)
    }
}
