package app.audioguide.catalog

/** Format SQLite d'un pack musée. Toute modification incompatible incrémente [VERSION]. */
object PackSchema {
    const val VERSION = 1

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
            search_text TEXT NOT NULL
        )
        """.trimIndent(),
        """CREATE UNIQUE INDEX idx_artwork_reference ON artwork(reference)""",
        """CREATE INDEX idx_artwork_inventory ON artwork(inventory_norm)""",
        """CREATE INDEX idx_artwork_title ON artwork(title_norm)""",
    )

    const val COLUMNS = "id, reference, inventory_no, title, artist, date_text, domain, denomination, school, " +
        "technique, height_cm, width_cm, location, provenance, description, has_image"
}
