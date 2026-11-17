package app.audioguide.favorites

import app.audioguide.catalog.Artwork
import app.audioguide.catalog.SqliteDb
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class FavoritesStoreTest {
    private val root = Files.createTempDirectory("audioguide-favorites-store-test")
    private val dbPath = root.resolve("user.db").toString()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        repeat(5) {
            if (runCatching { root.deleteRecursively() }.isSuccess) return
            Thread.sleep(100)
        }
    }

    private val olympia = Artwork(
        id = 1, museumId = "orsay", reference = "M5060-1", inventoryNo = "RF 644", title = "Olympia", artist = null,
        dateText = null, domain = null, denomination = null, school = null, technique = null, heightCm = null,
        widthCm = null, location = null, provenance = null, description = null, hasImage = false,
    )

    @Test
    fun aDatabaseFromANewerAppDoesNotCrashAndIsLeftUntouched() = runBlocking {
        SqliteDb.open(dbPath).use { it.exec("PRAGMA user_version = 99") }
        val store = FavoritesStore(dbPath, { 1L }, scope)

        // Pas d'exception : favoris vides, ajouts sans effet.
        assertEquals(emptyList(), store.favorites.value)
        assertNull(store.addArtwork(olympia, "musée d'Orsay"))
        assertNull(store.addCartel("Un cartel", "Texte", "fr-FR"))
        store.toggleArtwork(olympia, "musée d'Orsay")
        store.remove(1)
        store.saveNote(1, "note")

        // La base n'a pas été modifiée.
        SqliteDb.open(dbPath).use { db ->
            assertEquals(99, db.query("PRAGMA user_version", map = { it.getLong(0).toInt() }).first())
        }
    }
}
