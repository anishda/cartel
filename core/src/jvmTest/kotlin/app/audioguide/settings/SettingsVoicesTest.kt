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
class SettingsVoicesTest {
    private val root = Files.createTempDirectory("audioguide-voices-test")
    private val file = root.resolve("settings.json").toOkioPath()

    @AfterTest
    fun cleanUp() = root.deleteRecursively()

    @Test
    fun preferredVoicesArePersistedPerLanguage() = runTest {
        SettingsStore(FileSystem.SYSTEM, file).update { it.copy(preferredVoices = it.preferredVoices + ("fr-FR" to "voix-amelie")) }
        val reloaded = SettingsStore(FileSystem.SYSTEM, file).apply { load() }
        assertEquals(mapOf("fr-FR" to "voix-amelie"), reloaded.settings.value.preferredVoices)
    }

    @Test
    fun anOlderSettingsFileWithoutVoicesStillLoads() = runTest {
        FileSystem.SYSTEM.write(file) { writeUtf8("""{"speechRate": 1.2}""") }
        val store = SettingsStore(FileSystem.SYSTEM, file).apply { load() }
        assertEquals(1.2f, store.settings.value.speechRate)
        assertEquals(emptyMap(), store.settings.value.preferredVoices)
    }
}
