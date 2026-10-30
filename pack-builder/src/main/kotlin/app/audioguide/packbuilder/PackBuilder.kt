package app.audioguide.packbuilder

import app.audioguide.catalog.JocondeMapper
import app.audioguide.catalog.PackInfo
import app.audioguide.catalog.PackWriter
import app.audioguide.packs.PackManifest
import app.audioguide.packs.PackManifestEntry
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

class PackBuilder(
    private val source: RecordSource,
    private val outDir: Path,
    private val log: (String) -> Unit = ::println,
) {
    /** Fabrique le pack de [museum] et retourne son entrée de manifeste. */
    fun build(museum: MuseumSpec, sourceUpdatedAt: String?): PackManifestEntry {
        Files.createDirectories(outDir)
        val file = outDir.resolve("${museum.id}.pack.db")
        Files.deleteIfExists(file)

        log("${museum.name} : lecture des notices Joconde…")
        val records = source.records(museum) { loaded, total -> log("  $loaded${total?.let { "/$it" } ?: ""} notices") }

        var skipped = 0
        val version = LocalDate.now().toString()
        val count = PackWriter.create(file.toString()).use { writer ->
            for (record in records) {
                val draft = JocondeMapper.toDraft(record, museum.name)
                if (draft == null) skipped++ else writer.add(draft)
            }
            writer.finish(
                PackInfo(
                    museumId = museum.id,
                    museumName = museum.name,
                    city = museum.city,
                    version = version,
                    builtAt = Instant.now().toString(),
                    sourceName = JocondeSource.NAME,
                    sourceUrl = JocondeSource.DATASET_URL,
                    license = JocondeSource.LICENSE,
                    sourceUpdatedAt = sourceUpdatedAt,
                    recordCount = 0,
                ),
            )
        }
        log("  → $count œuvres écrites, $skipped notices inexploitables ignorées, ${Files.size(file) / 1024} Ko")

        return PackManifestEntry(
            id = museum.id,
            name = museum.name,
            city = museum.city,
            version = version,
            file = file.fileName.toString(),
            sizeBytes = Files.size(file),
            sha256 = sha256(file),
            recordCount = count,
            source = JocondeSource.NAME,
            license = JocondeSource.LICENSE,
        )
    }

    /** Fabrique les packs de [museums] et écrit `manifest.json` à côté. */
    fun buildAll(museums: List<MuseumSpec>): PackManifest {
        val updatedAt = source.sourceUpdatedAt()
        val manifest = PackManifest(packs = museums.map { build(it, updatedAt) })
        Files.writeString(outDir.resolve("manifest.json"), manifest.toJson())
        return manifest
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
