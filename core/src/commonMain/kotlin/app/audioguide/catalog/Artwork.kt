package app.audioguide.catalog

/** Une œuvre telle que décrite dans un pack musée. */
data class Artwork(
    /** Identifiant dans le pack : ne survit pas à une nouvelle version du pack. */
    val id: Long,
    val museumId: String,
    /** Référence de la notice source (Joconde) : stable d'une version à l'autre, sert de clé aux favoris. */
    val reference: String,
    val inventoryNo: String,
    val title: String,
    val artist: String?,
    val dateText: String?,
    val domain: String?,
    val denomination: String?,
    val school: String?,
    val technique: String?,
    val heightCm: Double?,
    val widthCm: Double?,
    val location: String?,
    val provenance: String?,
    val description: String?,
    val hasImage: Boolean,
)

/** Œuvre prête à être insérée dans un pack (sans identifiant, ni musée : fixés par le pack). */
data class ArtworkDraft(
    val reference: String,
    val inventoryNo: String,
    val title: String,
    val artist: String? = null,
    val dateText: String? = null,
    val domain: String? = null,
    val denomination: String? = null,
    val school: String? = null,
    val technique: String? = null,
    val heightCm: Double? = null,
    val widthCm: Double? = null,
    val location: String? = null,
    val provenance: String? = null,
    val description: String? = null,
    val hasImage: Boolean = false,
)

/** Description d'un pack, lue dans sa table `meta`. */
data class PackInfo(
    val museumId: String,
    val museumName: String,
    val city: String?,
    val version: String,
    val builtAt: String,
    val sourceName: String,
    val sourceUrl: String?,
    val license: String,
    val sourceUpdatedAt: String?,
    val recordCount: Int,
)
