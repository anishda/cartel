package app.audioguide.catalog

import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Façon de comparer un titre à celui d'une œuvre. */
enum class TitleMatch { Exact, Prefix, Contains }

/** Une œuvre trouvée par la recherche, avec son rang (0 = meilleure correspondance). */
data class SearchHit(val artwork: Artwork, val rank: Int)

/** Pack musée ouvert en lecture. Les accès sont sérialisés et exécutés hors du fil principal. */
class Pack private constructor(private val db: SqliteDb, val info: PackInfo, formatVersion: Int) : AutoCloseable {
    private val mutex = Mutex()
    private val columns = PackSchema.columns(formatVersion)

    private suspend fun <T> access(block: SqliteDb.() -> T): T =
        mutex.withLock { withContext(IoDispatcher) { db.block() } }

    suspend fun count(): Int = access { query("SELECT COUNT(*) FROM artwork", map = { it.getLong(0).toInt() }).first() }

    /** Tous les mots de [query] doivent apparaître dans le titre, l'artiste ou l'inventaire. */
    suspend fun search(query: String, limit: Int = 50): List<SearchHit> {
        val normalized = normalizeForSearch(query)
        val tokens = normalized.split(' ').filter { it.isNotEmpty() }.take(MAX_TOKENS)
        if (tokens.isEmpty()) return emptyList()

        val conditions = tokens.indices.joinToString(" AND ") { "search_text LIKE ?${it + 4}" }
        val sql = """
            SELECT $columns,
                CASE WHEN title_norm = ?1 THEN 0
                     WHEN inventory_norm = ?2 THEN 0
                     WHEN title_norm LIKE ?3 THEN 1
                     WHEN artist_norm LIKE ?3 THEN 2
                     ELSE 3 END AS rank
            FROM artwork WHERE $conditions
            ORDER BY rank, notability DESC, has_image DESC, title_norm LIMIT $limit
        """
        return access {
            query(sql, bind = { s ->
                s.bindText(1, normalized)
                s.bindText(2, normalizeInventory(query))
                s.bindText(3, "$normalized%")
                tokens.forEachIndexed { i, token -> s.bindText(i + 4, "%$token%") }
            }, map = { SearchHit(it.toArtwork(info.museumId), it.getLong(PackSchema.COLUMN_COUNT).toInt()) })
        }
    }

    suspend fun byId(id: Long): Artwork? = access {
        query("SELECT $columns FROM artwork WHERE id = ?1", { it.bindLong(1, id) }, { it.toArtwork(info.museumId) })
            .firstOrNull()
    }

    suspend fun byReference(reference: String): Artwork? = access {
        query("SELECT $columns FROM artwork WHERE reference = ?1", { it.bindText(1, reference) }, { it.toArtwork(info.museumId) })
            .firstOrNull()
    }

    /** [inventoryNorm] : voir [normalizeInventory]. */
    suspend fun byInventory(inventoryNorm: String): List<Artwork> = access {
        query("SELECT $columns FROM artwork WHERE inventory_norm = ?1", { it.bindText(1, inventoryNorm) }, { it.toArtwork(info.museumId) })
    }

    /** [titleNorm] : voir [normalizeForSearch]. */
    suspend fun byTitle(titleNorm: String, mode: TitleMatch = TitleMatch.Exact, limit: Int = 10): List<Artwork> = access {
        val (condition, argument) = when (mode) {
            TitleMatch.Exact -> "title_norm = ?1" to titleNorm
            TitleMatch.Prefix -> "title_norm LIKE ?1" to "$titleNorm%"
            TitleMatch.Contains -> "title_norm LIKE ?1" to "%$titleNorm%"
        }
        query("SELECT $columns FROM artwork WHERE $condition LIMIT $limit",
            { it.bindText(1, argument) }, { it.toArtwork(info.museumId) })
    }

    override fun close() = db.close()

    companion object {
        private const val MAX_TOKENS = 6

        /** Ouvre un pack et vérifie qu'il est lisible par cette version de l'app. */
        suspend fun open(path: String): Pack = withContext(IoDispatcher) {
            val db = SqliteDb.open(path)
            try {
                val version = db.query("PRAGMA user_version", map = { it.getLong(0).toInt() }).first()
                require(version in PackSchema.MIN_READABLE_VERSION..PackSchema.VERSION) { "Version de pack non supportée : $version" }
                val meta = db.query("SELECT key, value FROM meta", map = { it.getText(0) to it.getText(1) }).toMap()
                fun need(key: String) = requireNotNull(meta[key]) { "Pack incomplet : métadonnée « $key » absente" }
                Pack(
                    db,
                    PackInfo(
                        museumId = need(PackSchema.Meta.MUSEUM_ID),
                        museumName = need(PackSchema.Meta.MUSEUM_NAME),
                        city = meta[PackSchema.Meta.CITY],
                        version = need(PackSchema.Meta.VERSION),
                        builtAt = need(PackSchema.Meta.BUILT_AT),
                        sourceName = need(PackSchema.Meta.SOURCE_NAME),
                        sourceUrl = meta[PackSchema.Meta.SOURCE_URL],
                        license = need(PackSchema.Meta.LICENSE),
                        sourceUpdatedAt = meta[PackSchema.Meta.SOURCE_UPDATED_AT],
                        recordCount = need(PackSchema.Meta.RECORD_COUNT).toInt(),
                        storyLicense = meta[PackSchema.Meta.STORY_LICENSE],
                    ),
                    version,
                )
            } catch (e: Throwable) {
                db.close()
                throw e
            }
        }
    }
}

private fun SQLiteStatement.toArtwork(museumId: String) = Artwork(
    id = getLong(0),
    museumId = museumId,
    reference = getText(1),
    inventoryNo = getText(2),
    title = getText(3),
    artist = textOrNull(4),
    dateText = textOrNull(5),
    domain = textOrNull(6),
    denomination = textOrNull(7),
    school = textOrNull(8),
    technique = textOrNull(9),
    heightCm = doubleOrNull(10),
    widthCm = doubleOrNull(11),
    location = textOrNull(12),
    provenance = textOrNull(13),
    description = textOrNull(14),
    hasImage = getLong(15) != 0L,
    wikidataId = textOrNull(16),
    notability = getLong(17).toInt(),
    story = textOrNull(18)?.let { text ->
        ArtworkStory(text = text, articleTitle = textOrNull(19) ?: "", articleUrl = textOrNull(20) ?: "")
    },
)
