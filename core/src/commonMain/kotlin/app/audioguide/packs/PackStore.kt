package app.audioguide.packs

import app.audioguide.catalog.Pack
import app.audioguide.catalog.PackInfo
import app.audioguide.catalog.IoDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.HashingSource
import okio.Path
import okio.Path.Companion.toPath
import okio.blackholeSink
import okio.buffer
import okio.use

/** Les packs musée installés sur l'appareil : un fichier `<musée>.pack.db` par musée. */
class PackStore(
    private val fileSystem: FileSystem,
    private val directory: Path,
) {
    private val mutex = Mutex()
    private val open = mutableMapOf<String, Pack>()
    private val _installed = MutableStateFlow<List<PackInfo>>(emptyList())

    /** Packs installés, triés par nom. Mis à jour par [refresh], [install] et [remove]. */
    val installed: StateFlow<List<PackInfo>> = _installed.asStateFlow()

    /** Où écrire un téléchargement avant de l'installer. */
    fun downloadPath(museumId: String): Path = directory / "$museumId.download"

    /** Supprime un téléchargement inachevé ou refusé. */
    suspend fun discardDownload(museumId: String) = withContext(IoDispatcher) {
        fileSystem.delete(downloadPath(museumId), mustExist = false)
    }

    /** Relit le dossier : ouvre les packs valides, ignore (et supprime) les fichiers corrompus. */
    suspend fun refresh() = mutex.withLock { scan() }

    suspend fun packs(): List<Pack> = mutex.withLock { open.values.sortedBy { it.info.museumName } }

    suspend fun pack(museumId: String): Pack? = mutex.withLock { open[museumId] }

    /**
     * Installe le fichier [downloaded] comme pack de [museumId], après vérification de son empreinte
     * ([expectedSha256]) et de son contenu. Remplace une version précédente.
     */
    suspend fun install(museumId: String, downloaded: Path, expectedSha256: String?) = mutex.withLock {
        withContext(IoDispatcher) {
            if (expectedSha256 != null) {
                val actual = sha256(downloaded)
                require(actual.equals(expectedSha256, ignoreCase = true)) { "Empreinte du pack incorrecte" }
            }
            // Vérifie que le fichier est un pack lisible avant de toucher à l'existant.
            Pack.open(downloaded.toString()).close()

            open.remove(museumId)?.close()
            fileSystem.createDirectories(directory)
            fileSystem.atomicMove(downloaded, packPath(museumId))
        }
        scan()
    }

    suspend fun remove(museumId: String) = mutex.withLock {
        withContext(IoDispatcher) {
            open.remove(museumId)?.close()
            fileSystem.delete(packPath(museumId), mustExist = false)
        }
        scan()
    }

    private suspend fun scan() = withContext(IoDispatcher) {
        fileSystem.createDirectories(directory)
        val found = fileSystem.list(directory).filter { it.name.endsWith(SUFFIX) }
        val keep = mutableMapOf<String, Pack>()
        for (path in found) {
            val id = path.name.removeSuffix(SUFFIX)
            val existing = open[id]
            if (existing != null) {
                keep[id] = existing
                continue
            }
            try {
                keep[id] = Pack.open(path.toString())
            } catch (e: Exception) {
                // Pack illisible (téléchargement interrompu, format trop récent) : on le retire.
                fileSystem.delete(path, mustExist = false)
            }
        }
        (open.keys - keep.keys).forEach { open.remove(it)?.close() }
        open.putAll(keep)
        _installed.value = open.values.map { it.info }.sortedBy { it.museumName }
    }

    private fun packPath(museumId: String): Path = directory / "$museumId$SUFFIX"

    private fun sha256(path: Path): String =
        fileSystem.source(path).use { source ->
            val hashing = HashingSource.sha256(source)
            hashing.buffer().readAll(blackholeSink())
            hashing.hash.hex()
        }

    companion object {
        const val SUFFIX = ".pack.db"

        /** Pratique pour les tests et les plateformes qui fournissent un chemin sous forme de chaîne. */
        fun pathOf(directory: String): Path = directory.toPath()
    }
}
