package app.audioguide.packs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Catalogue des packs publiés sur le serveur (`manifest.json`). */
@Serializable
data class PackManifest(
    val formatVersion: Int = 1,
    val packs: List<PackManifestEntry> = emptyList(),
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
        fun fromJson(text: String): PackManifest = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class PackManifestEntry(
    val id: String,
    val name: String,
    val city: String? = null,
    val version: String,
    /** Nom du fichier, relatif à l'adresse du manifeste. */
    val file: String,
    val sizeBytes: Long,
    val sha256: String,
    val recordCount: Int,
    val source: String,
    val license: String,
)
