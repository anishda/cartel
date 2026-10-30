package app.audioguide.catalog

/** Écrit un pack musée. Utilisé par `:pack-builder` et par les tests. */
class PackWriter private constructor(private val db: SqliteDb) : AutoCloseable {
    private var count = 0

    init {
        db.transaction { PackSchema.statements.forEach(db::exec) }
        db.exec("BEGIN")
    }

    fun add(draft: ArtworkDraft) {
        val titleNorm = normalizeForSearch(draft.title)
        val artistNorm = draft.artist?.let(::normalizeForSearch)
        val inventoryNorm = normalizeInventory(draft.inventoryNo)
        val searchText = listOfNotNull(
            titleNorm,
            artistNorm,
            inventoryNorm,
            normalizeForSearch(draft.inventoryNo),
        ).joinToString(" ")

        db.update(INSERT) { s ->
            s.bindLong(1, (++count).toLong())
            s.bindText(2, draft.reference)
            s.bindText(3, draft.inventoryNo)
            s.bindText(4, inventoryNorm)
            s.bindText(5, draft.title)
            s.bindText(6, titleNorm)
            s.bindTextOrNull(7, draft.artist)
            s.bindTextOrNull(8, artistNorm)
            s.bindTextOrNull(9, draft.dateText)
            s.bindTextOrNull(10, draft.domain)
            s.bindTextOrNull(11, draft.denomination)
            s.bindTextOrNull(12, draft.school)
            s.bindTextOrNull(13, draft.technique)
            if (draft.heightCm != null) s.bindDouble(14, draft.heightCm) else s.bindNull(14)
            if (draft.widthCm != null) s.bindDouble(15, draft.widthCm) else s.bindNull(15)
            s.bindTextOrNull(16, draft.location)
            s.bindTextOrNull(17, draft.provenance)
            s.bindTextOrNull(18, draft.description)
            s.bindLong(19, if (draft.hasImage) 1 else 0)
            s.bindText(20, searchText)
        }
    }

    /** Écrit les métadonnées, ferme l'écriture et compacte le fichier. Retourne le nombre d'œuvres. */
    fun finish(info: PackInfo): Int {
        val meta = mapOf(
            PackSchema.Meta.MUSEUM_ID to info.museumId,
            PackSchema.Meta.MUSEUM_NAME to info.museumName,
            PackSchema.Meta.CITY to info.city,
            PackSchema.Meta.VERSION to info.version,
            PackSchema.Meta.BUILT_AT to info.builtAt,
            PackSchema.Meta.SOURCE_NAME to info.sourceName,
            PackSchema.Meta.SOURCE_URL to info.sourceUrl,
            PackSchema.Meta.LICENSE to info.license,
            PackSchema.Meta.SOURCE_UPDATED_AT to info.sourceUpdatedAt,
            PackSchema.Meta.RECORD_COUNT to count.toString(),
        )
        for ((key, value) in meta) {
            if (value == null) continue
            db.update("INSERT INTO meta(key, value) VALUES (?1, ?2)") { s ->
                s.bindText(1, key)
                s.bindText(2, value)
            }
        }
        db.exec("COMMIT")
        db.exec("PRAGMA user_version = ${PackSchema.VERSION}")
        db.exec("VACUUM")
        return count
    }

    override fun close() = db.close()

    companion object {
        fun create(path: String): PackWriter = PackWriter(SqliteDb.open(path))

        private const val INSERT = """
            INSERT INTO artwork (id, reference, inventory_no, inventory_norm, title, title_norm, artist, artist_norm,
                date_text, domain, denomination, school, technique, height_cm, width_cm, location, provenance,
                description, has_image, search_text)
            VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17, ?18, ?19, ?20)
        """
    }
}

private fun androidx.sqlite.SQLiteStatement.bindTextOrNull(index: Int, value: String?) {
    if (value == null) bindNull(index) else bindText(index, value)
}
