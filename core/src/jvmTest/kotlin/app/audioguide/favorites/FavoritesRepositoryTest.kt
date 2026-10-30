package app.audioguide.favorites

import app.audioguide.catalog.Artwork
import app.audioguide.catalog.SqliteDb
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class FavoritesRepositoryTest {
    private val root = Files.createTempDirectory("audioguide-favorites-test")
    private val dbPath = root.resolve("user.db").toString()
    private var clock = 1_000L

    @AfterTest
    fun cleanUp() = root.deleteRecursively()

    private suspend fun open() = FavoritesRepository.open(dbPath) { clock++ }

    private val olympia = Artwork(
        id = 1, museumId = "orsay", reference = "M5060-1", inventoryNo = "RF 644", title = "Olympia",
        artist = "Édouard Manet", dateText = "1863", domain = "peinture", denomination = "tableau", school = "France",
        technique = "peinture à l'huile, toile", heightCm = 130.5, widthCm = 190.0, location = null,
        provenance = null, description = null, hasImage = true,
    )

    @Test
    fun startsEmpty() = runTest {
        val repository = open()
        assertTrue(repository.favorites.value.isEmpty())
        repository.close()
    }

    @Test
    fun addingAnArtworkKeepsASnapshot() = runTest {
        val repository = open()
        val favorite = repository.addArtwork(olympia, "musée d'Orsay")

        assertEquals(FavoriteKind.Artwork, favorite.kind)
        assertEquals("Olympia", favorite.title)
        assertEquals("Édouard Manet · 1863 · RF 644", favorite.subtitle)
        assertEquals("musée d'Orsay", favorite.museumName)
        assertEquals("M5060-1", favorite.reference)
        assertTrue(favorite.body.startsWith("Olympia."), favorite.body)
        assertEquals("fr-FR", favorite.languageTag)
        assertTrue(repository.isArtworkFavorite("orsay", "M5060-1"))
        assertFalse(repository.isArtworkFavorite("louvre", "M5060-1"))
        repository.close()
    }

    @Test
    fun anArtworkCanOnlyBeFavoriteOnce() = runTest {
        val repository = open()
        val first = repository.addArtwork(olympia, "musée d'Orsay")
        val second = repository.addArtwork(olympia, "musée d'Orsay")
        assertEquals(first.id, second.id)
        assertEquals(1, repository.favorites.value.size)
        repository.close()
    }

    @Test
    fun cartelsCanRepeat() = runTest {
        val repository = open()
        repository.addCartel("Un cartel", "Texte du cartel.", "fr-FR")
        repository.addCartel("Un cartel", "Texte du cartel.", "fr-FR")
        assertEquals(2, repository.favorites.value.size)
        assertTrue(repository.favorites.value.all { it.kind == FavoriteKind.Cartel && it.museumId == null })
        repository.close()
    }

    @Test
    fun mostRecentComesFirst() = runTest {
        val repository = open()
        repository.addCartel("Premier", "a", "fr-FR")
        repository.addArtwork(olympia, "musée d'Orsay")
        repository.addCartel("Dernier", "b", "fr-FR")
        assertEquals(listOf("Dernier", "Olympia", "Premier"), repository.favorites.value.map { it.title })
        repository.close()
    }

    @Test
    fun removeByIdAndByArtwork() = runTest {
        val repository = open()
        val cartel = repository.addCartel("Un cartel", "x", "fr-FR")
        repository.addArtwork(olympia, "musée d'Orsay")

        repository.remove(cartel.id)
        assertEquals(listOf("Olympia"), repository.favorites.value.map { it.title })
        repository.removeArtwork("orsay", "M5060-1")
        assertTrue(repository.favorites.value.isEmpty())
        assertFalse(repository.isArtworkFavorite("orsay", "M5060-1"))
        repository.close()
    }

    @Test
    fun notesAreTrimmedAndCanBeCleared() = runTest {
        val repository = open()
        val favorite = repository.addArtwork(olympia, "musée d'Orsay")
        repository.setNote(favorite.id, "  Vu avec Maman, salle 14  ")
        assertEquals("Vu avec Maman, salle 14", repository.favorites.value.single().note)
        repository.setNote(favorite.id, "   ")
        assertNull(repository.favorites.value.single().note)
        repository.close()
    }

    @Test
    fun favoritesSurviveReopening() = runTest {
        open().apply {
            addArtwork(olympia, "musée d'Orsay")
            setNote(favorites.value.single().id, "à revoir")
            close()
        }
        val reopened = open()
        val favorite = reopened.favorites.value.single()
        assertEquals("Olympia", favorite.title)
        assertEquals("à revoir", favorite.note)
        reopened.close()
    }

    @Test
    fun refusesADatabaseFromANewerApp() = runTest {
        SqliteDb.open(dbPath).use { it.exec("PRAGMA user_version = 99") }
        assertFailsWith<IllegalArgumentException> { open() }
    }
}
