package app.audioguide.platform

import io.ktor.client.HttpClient
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Le modèle de la voix Piper SIWIS (français, int8) : `model.onnx` et `tokens.txt` dans `filesDir/voices/piper-fr`,
 * téléchargés d'une archive zip dont l'empreinte est vérifiée.
 */
class PiperVoiceModel(
    filesDir: File,
    private val client: HttpClient,
    private val url: String = MODEL_URL,
    private val sha256: String = MODEL_SHA256,
) : NaturalVoice {
    private val dir = File(filesDir, "voices/piper-fr")
    val modelFile get() = File(dir, "model.onnx")
    val tokensFile get() = File(dir, "tokens.txt")

    override val available = true
    override val downloadSizeMb = MODEL_SIZE_MB

    private val _state = MutableStateFlow(if (installed()) NaturalVoiceState.Ready else NaturalVoiceState.NotInstalled)
    override val state: StateFlow<NaturalVoiceState> = _state.asStateFlow()

    private fun installed() = modelFile.exists() && tokensFile.exists()

    override suspend fun download() {
        if (installed() || _state.value is NaturalVoiceState.Downloading) return
        _state.value = NaturalVoiceState.Downloading(0f)
        val result = runCatching {
            withContext(Dispatchers.IO) {
                dir.mkdirs()
                val archive = File(dir, "download.zip")
                try {
                    fetch(archive)
                    check(sha256Of(archive) == sha256) { "Empreinte inattendue pour le modèle de voix" }
                    unzip(archive)
                } finally {
                    archive.delete()
                }
                check(installed()) { "Archive incomplète" }
            }
        }
        _state.value = if (result.isSuccess) NaturalVoiceState.Ready else NaturalVoiceState.Failed
    }

    private suspend fun fetch(target: File) {
        client.prepareGet(url) {
            onDownload { received, total ->
                if (total != null && total > 0) _state.value = NaturalVoiceState.Downloading(received.toFloat() / total)
            }
        }.execute { response ->
            check(response.status.value in 200..299) { "Téléchargement impossible : ${response.status}" }
            val channel = response.bodyAsChannel()
            target.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = channel.readAvailable(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
            }
        }
    }

    private fun unzip(archive: File) {
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                // Seuls les deux fichiers attendus, à plat : jamais de chemin venu de l'archive.
                val name = entry.name.substringAfterLast('/')
                if (entry.isDirectory || name !in setOf("model.onnx", "tokens.txt")) continue
                File(dir, "$name.part").outputStream().use { zip.copyTo(it) }
                File(dir, "$name.part").renameTo(File(dir, name))
            }
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun delete() {
        dir.deleteRecursively()
        _state.value = NaturalVoiceState.NotInstalled
    }

    companion object {
        /** Archive publiée avec les packs : voir README (« Voix neuronale »). */
        const val MODEL_URL = "https://github.com/anishda/cartel/releases/download/voices-1/piper-fr_FR-siwis-medium-int8.zip"
        const val MODEL_SHA256 = "c60e7341e4543a7d79c843edd107c27ef4897bde00243d7cfa1961f20d25b733"
        const val MODEL_SIZE_MB = 13
    }
}
