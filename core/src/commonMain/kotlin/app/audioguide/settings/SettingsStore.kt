package app.audioguide.settings

import app.audioguide.catalog.IoDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

@Serializable
data class Settings(
    /** Vitesse de lecture : 1,0 = normale. */
    val speechRate: Float = 1.0f,
    /** Voix choisie par langue (étiquette BCP 47 → identifiant de voix). Absente : la meilleure voix installée. */
    val preferredVoices: Map<String, String> = emptyMap(),
    /** Utiliser la voix neuronale quand elle est installée (Android). */
    val naturalVoice: Boolean = true,
    /** Date (ms depuis 1970) à laquelle le cache des images a été vidé pour la dernière fois. */
    val imageCacheSince: Long? = null,
) {
    companion object {
        const val MIN_RATE = 0.6f
        const val MAX_RATE = 1.6f
    }
}

/** Réglages de l'app, gardés dans un petit fichier JSON. Un fichier absent ou illisible redonne les valeurs par défaut. */
class SettingsStore(
    private val fileSystem: FileSystem,
    private val path: Path,
) {
    private val mutex = Mutex()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; coerceInputValues = true }
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    suspend fun load() = mutex.withLock {
        _settings.value = withContext(IoDispatcher) {
            runCatching {
                if (fileSystem.exists(path)) json.decodeFromString(Settings.serializer(), fileSystem.read(path) { readUtf8() }) else Settings()
            }.getOrDefault(Settings())
        }.sanitized()
    }

    suspend fun update(transform: (Settings) -> Settings) = mutex.withLock {
        val next = transform(_settings.value).sanitized()
        _settings.value = next
        withContext(IoDispatcher) {
            runCatching {
                path.parent?.let(fileSystem::createDirectories)
                fileSystem.write(path) { writeUtf8(json.encodeToString(Settings.serializer(), next)) }
            }
        }
    }

    private fun Settings.sanitized() = copy(speechRate = speechRate.coerceIn(Settings.MIN_RATE, Settings.MAX_RATE))
}
