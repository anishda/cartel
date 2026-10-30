package app.audioguide.feature.packs

import app.audioguide.packs.PackManifest
import app.audioguide.packs.PackManifestEntry
import app.audioguide.platform.PackConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import okio.FileSystem
import okio.Path

/** Le serveur de packs : un `manifest.json` et un fichier par musée. */
class PackRemote(
    private val client: HttpClient,
    private val config: PackConfig,
    private val fileSystem: FileSystem,
) {
    suspend fun manifest(): PackManifest {
        check(config.baseUrl.isNotBlank()) { "Aucun serveur de packs configuré" }
        val response = client.get(url("manifest.json"))
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        return PackManifest.fromJson(response.bodyAsText())
    }

    /** Télécharge le pack de [entry] dans [target]. [onProgress] reçoit une valeur entre 0 et 1. */
    suspend fun download(entry: PackManifestEntry, target: Path, onProgress: (Float) -> Unit) {
        val response = client.get(url(entry.file)) {
            onDownload { received, total ->
                val expected = total?.takeIf { it > 0 } ?: entry.sizeBytes
                if (expected > 0) onProgress((received.toFloat() / expected).coerceIn(0f, 1f))
            }
        }
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        val bytes: ByteArray = response.body()
        target.parent?.let(fileSystem::createDirectories)
        fileSystem.write(target) { write(bytes) }
    }

    private fun url(name: String) = config.baseUrl.trimEnd('/') + "/" + name
}
