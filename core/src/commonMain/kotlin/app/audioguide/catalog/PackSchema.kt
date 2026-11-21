package app.audioguide.catalog

/**
 * Format SQLite d'un pack musée. Toute modification du format incrémente [VERSION].
 * v2 : liaison Wikidata, notoriété et récit Wikipedia. v3 : image libre sur Wikimedia Commons (nom du fichier,
 * auteur, licence ; l'image n'est pas dans le pack). v4 : phonèmes précalculés des textes lus, pour la voix neuronale
 * (table `narration_phoneme`). Les packs v1, v2 et v3 restent lisibles.
 */
object PackSchema {
    const val VERSION = 4
    const val MIN_READABLE_VERSION = 1

    object Meta {
        const val MUSEUM_ID = "museum_id"
        const val MUSEUM_NAME = "museum_name"
        const val CITY = "city"
        const val VERSION = "version"
        const val BUILT_AT = "built_at"
        const val SOURCE_NAME = "source_name"
        const val SOURCE_URL = "source_url"
        const val LICENSE = "license"
        const val SOURCE_UPDATED_AT = "source_updated_at"
        const val RECORD_COUNT = "record_count"
        const val STORY_LICENSE = "story_license"
    }

    val statements = listOf(
        """CREATE TABLE meta (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)""",
        """
        CREATE TABLE artwork (
            id INTEGER PRIMARY KEY NOT NULL,
            reference TEXT NOT NULL,
            inventory_no TEXT NOT NULL,
            inventory_norm TEXT NOT NULL,
            title TEXT NOT NULL,
            title_norm TEXT NOT NULL,
            artist TEXT,
            artist_norm TEXT,
            date_text TEXT,
            domain TEXT,
            denomination TEXT,
            school TEXT,
            technique TEXT,
            height_cm REAL,
            width_cm REAL,
            location TEXT,
            provenance TEXT,
            description TEXT,
            has_image INTEGER NOT NULL,
            search_text TEXT NOT NULL,
            wikidata_id TEXT,
            notability INTEGER NOT NULL DEFAULT 0,
            story_text TEXT,
            story_title TEXT,
            story_url TEXT,
            image_file TEXT,
            image_author TEXT,
            image_license TEXT
        )
        """.trimIndent(),
        """CREATE UNIQUE INDEX idx_artwork_reference ON artwork(reference)""",
        """CREATE INDEX idx_artwork_inventory ON artwork(inventory_norm)""",
        """CREATE INDEX idx_artwork_title ON artwork(title_norm)""",
        // Un segment de texte lu (voir NarrationPhonemes.key) et ses phonèmes, une phrase par ligne.
        """CREATE TABLE narration_phoneme (key TEXT PRIMARY KEY NOT NULL, phonemes TEXT NOT NULL) WITHOUT ROWID""",
    )

    private const val BASE_COLUMNS = "id, reference, inventory_no, title, artist, date_text, domain, denomination, school, " +
        "technique, height_cm, width_cm, location, provenance, description, has_image"

    private const val STORY_COLUMNS = ", wikidata_id, notability, story_text, story_title, story_url"
    private const val NO_STORY_COLUMNS =
        ", NULL AS wikidata_id, 0 AS notability, NULL AS story_text, NULL AS story_title, NULL AS story_url"
    private const val IMAGE_COLUMNS = ", image_file, image_author, image_license"
    private const val NO_IMAGE_COLUMNS = ", NULL AS image_file, NULL AS image_author, NULL AS image_license"

    /** Colonnes lues pour une œuvre, dans l'ordre attendu par la lecture ; un ancien pack rend des valeurs vides. */
    fun columns(version: Int): String = BASE_COLUMNS +
        (if (version >= 2) STORY_COLUMNS else NO_STORY_COLUMNS) +
        (if (version >= 3) IMAGE_COLUMNS else NO_IMAGE_COLUMNS)

    /** Nombre de colonnes renvoyées par [columns]. */
    const val COLUMN_COUNT = 24
}
