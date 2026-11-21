package app.audioguide.packbuilder

import app.audioguide.catalog.ArtworkDraft
import app.audioguide.catalog.JocondeMapper
import app.audioguide.catalog.NarrationPhonemes
import app.audioguide.catalog.toArtwork
import app.audioguide.catalog.PackInfo
import app.audioguide.catalog.PackWriter
import app.audioguide.packs.PackManifest
import app.audioguide.packs.PackManifestEntry
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class PackBuilder(
    private val source: RecordSource,
    private val outDir: Path,
    private val log: (String) -> Unit = ::println,
    /** Source des liens Wikidata et des récits Wikipedia ; `null` : pack sans récits. */
    private val wikimedia: WikimediaSource? = null,
    /** Phonèmes des textes lus pour la voix neuronale ; `null` : packs sans phonèmes (voix du système seulement). */
    private val phonemizer: Phonemizer? = null,
    /** Reçoit les œuvres de chaque pack fabriqué (vocabulaire du lexique de la voix). */
    private val onDrafts: (List<ArtworkDraft>) -> Unit = {},
) {
    /** Fabrique le pack de [museum] et retourne son entrée de manifeste. */
    fun build(museum: MuseumSpec, sourceUpdatedAt: String?): PackManifestEntry {
        Files.createDirectories(outDir)
        val file = outDir.resolve("${museum.id}.pack.db")
        Files.deleteIfExists(file)

        log("${museum.name} : lecture des notices Joconde…")
        val records = source.records(museum) { loaded, total -> log("  $loaded${total?.let { "/$it" } ?: ""} notices") }

        val mapped = records.map { JocondeMapper.toDraft(it, museum.name) }
        val skipped = mapped.count { it == null }
        var drafts = mapped.filterNotNull()
        var storyLicense: String? = null
        if (wikimedia != null) {
            log("${museum.name} : liaison Wikidata et récits Wikipedia…")
            val result = StoryEnricher(wikimedia, log).enrich(museum, drafts)
            drafts = result.drafts
            if (result.withStory > 0) storyLicense = StoryEnricher.LICENSE
            log("  → ${result.linked} œuvres reliées à Wikidata, ${result.withStory} avec un récit, ${result.withImage} avec une image libre")
        }

        // Date et heure (UTC) : l'app propose la mise à jour quand cette valeur change, même pour deux builds le même jour.
        val version = DateTimeFormatter.ofPattern("yyyy-MM-dd.HHmm").format(LocalDateTime.now(ZoneOffset.UTC))
        val phonemes = phonemizer?.let { phonemize(museum, drafts, it) }.orEmpty()
        onDrafts(drafts)
        val count = PackWriter.create(file.toString()).use { writer ->
            drafts.forEach(writer::add)
            phonemes.forEach { (key, value) -> writer.addPhonemes(key, value) }
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
                    storyLicense = storyLicense,
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

    /**
     * Phonèmes des textes lus des œuvres qui ont un récit (clé de segment → phonèmes). Les autres œuvres n'ont que
     * l'en-tête et la technique, que la voix du système lit très bien : elles ne grossissent pas le pack.
     */
    internal fun phonemize(museum: MuseumSpec, drafts: List<ArtworkDraft>, phonemizer: Phonemizer): Map<String, String> {
        val segments = drafts.filter { it.story != null }
            .flatMap { NarrationPhonemes.segments(it.toArtwork(museum.id), museum.name) }
            .distinct()
        log("${museum.name} : phonétisation de ${segments.size} segments de lecture…")
        val phonemes = phonemizer.phonemize(segments.map(NarrationPhonemes::textToPhonemize))
        return segments.indices.associate { NarrationPhonemes.key(segments[it]) to phonemes[it] }.filterValues { it.isNotBlank() }
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
