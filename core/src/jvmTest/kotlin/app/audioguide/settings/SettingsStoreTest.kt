package app.audioguide.settings

import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class SettingsStoreTest {
    private val root = Files.createTempDirectory("audioguide-settings-test")
    private val file = root.resolve("sub/settings.json").toOkioPath()

    @AfterTest
    fun cleanUp() = root.deleteRecursively()

    private fun store() = SettingsStore(FileSystem.SYSTEM, file)

    @Test
    fun defaultsWhenNoFile() = runTest {
        val store = store().apply { load() }
        assertEquals(1.0f, store.settings.value.speechRate)
    }

    @Test
    fun updatesArePersisted() = runTest {
        store().update { it.copy(speechRate = 1.25f) }
        val reloaded = store().apply { load() }
        assertEquals(1.25f, reloaded.settings.value.speechRate)
    }

    @Test
    fun rateIsClamped() = runTest {
        val store = store()
        store.update { it.copy(speechRate = 9f) }
        assertEquals(Settings.MAX_RATE, store.settings.value.speechRate)
        store.update { it.copy(speechRate = 0f) }
        assertEquals(Settings.MIN_RATE, store.settings.value.speechRate)
    }

    @Test
    fun corruptFileFallsBackToDefaults() = runTest {
        FileSystem.SYSTEM.createDirectories(file.parent!!)
        FileSystem.SYSTEM.write(file) { writeUtf8("{ pas du json") }
        val store = store().apply { load() }
        assertEquals(1.0f, store.settings.value.speechRate)
    }
}
