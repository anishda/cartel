package app.audioguide.favorites

import app.audioguide.catalog.Artwork
import app.audioguide.catalog.ArtworkNarration
import app.audioguide.catalog.IoDispatcher
import app.audioguide.catalog.SqliteDb
import app.audioguide.catalog.textOrNull
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Base des données de l'utilisateur (`user.db`), séparée des packs : les packs se remplacent, elle reste. */
internal object UserSchema {
    const val VERSION = 1

    private val v1 = listOf(
        """
        CREATE TABLE favorite (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            kind TEXT NOT NULL,
            museum_id TEXT,
            museum_name TEXT,
            reference TEXT,
            title TEXT NOT NULL,
            subtitle TEXT,
            body TEXT NOT NULL,
            language_tag TEXT NOT NULL,
            note TEXT,
            created_at INTEGER NOT NULL
        )
        """.trimIndent(),
        // Une œuvre ne peut être favorite qu'une fois ; les cartels libres (colonnes nulles) peuvent se répéter.
        "CREATE UNIQUE INDEX idx_favorite_artwork ON favorite(museum_id, reference)",
    )

    /** Amène la base à la dernière version. Refuse une base écrite par une version plus récente de l'app. */
    fun migrate(db: SqliteDb) {
        val current = db.query("PRAGMA user_version", map = { it.getLong(0).toInt() }).first()
        require(current <= VERSION) { "Base utilisateur trop récente : version $current" }
        if (current < 1) db.transaction { v1.forEach(db::exec) }
        db.exec("PRAGMA user_version = $VERSION")
    }
}

class FavoritesRepository private constructor(
    private val db: SqliteDb,
    private val now: () -> Long,
) : AutoCloseable {
    private val mutex = Mutex()
    private val _favorites = MutableStateFlow<List<Favorite>>(emptyList())

    /** Tous les favoris, les plus récents d'abord. Se met à jour à chaque modification. */
    val favorites: StateFlow<List<Favorite>> = _favorites.asStateFlow()

    private suspend fun <T> access(block: SqliteDb.() -> T): T =
        mutex.withLock { withContext(IoDispatcher) { db.block() } }

    private suspend fun refresh() {
        _favorites.value = access { query("SELECT $COLUMNS FROM favorite ORDER BY created_at DESC, id DESC", map = { it.toFavorite() }) }
    }

    fun isArtworkFavorite(museumId: String, reference: String): Boolean =
        _favorites.value.any { it.museumId == museumId && it.reference == reference }

    /** Ajoute l'œuvre ; sans effet si elle est déjà favorite. Retourne le favori. */
    suspend fun addArtwork(artwork: Artwork, museumName: String): Favorite {
        val subtitle = listOfNotNull(artwork.artist, artwork.dateText, artwork.inventoryNo).joinToString(" · ").ifEmpty { null }
        access {
            update(
                """
                INSERT OR IGNORE INTO favorite (kind, museum_id, museum_name, reference, title, subtitle, body, language_tag, created_at)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)
                """,
            ) { s ->
                s.bindText(1, FavoriteKind.Artwork.name)
                s.bindText(2, artwork.museumId)
                s.bindText(3, museumName)
                s.bindText(4, artwork.reference)
                s.bindText(5, artwork.title)
                if (subtitle != null) s.bindText(6, subtitle) else s.bindNull(6)
                s.bindText(7, ArtworkNarration.build(artwork, museumName))
                s.bindText(8, ArtworkNarration.LANGUAGE_TAG)
                s.bindLong(9, now())
            }
        }
        refresh()
        return _favorites.value.first { it.museumId == artwork.museumId && it.reference == artwork.reference }
    }

    /** Garde le texte d'un cartel qui ne correspond à aucune œuvre connue. */
    suspend fun addCartel(title: String, body: String, languageTag: String): Favorite {
        val id = access {
            update(
                """
                INSERT INTO favorite (kind, title, body, language_tag, created_at)
                VALUES (?1, ?2, ?3, ?4, ?5)
                """,
            ) { s ->
                s.bindText(1, FavoriteKind.Cartel.name)
                s.bindText(2, title)
                s.bindText(3, body)
                s.bindText(4, languageTag)
                s.bindLong(5, now())
            }
            query("SELECT last_insert_rowid()", map = { it.getLong(0) }).first()
        }
        refresh()
        return _favorites.value.first { it.id == id }
    }

    suspend fun remove(id: Long) {
        access { update("DELETE FROM favorite WHERE id = ?1") { it.bindLong(1, id) } }
        refresh()
    }

    suspend fun removeArtwork(museumId: String, reference: String) {
        access {
            update("DELETE FROM favorite WHERE museum_id = ?1 AND reference = ?2") {
                it.bindText(1, museumId)
                it.bindText(2, reference)
            }
        }
        refresh()
    }

    /** Une note vide efface la note. */
    suspend fun setNote(id: Long, note: String?) {
        val text = note?.trim()?.ifEmpty { null }
        access {
            update("UPDATE favorite SET note = ?1 WHERE id = ?2") { s ->
                if (text != null) s.bindText(1, text) else s.bindNull(1)
                s.bindLong(2, id)
            }
        }
        refresh()
    }

    override fun close() = db.close()

    companion object {
        private const val COLUMNS =
            "id, kind, museum_id, museum_name, reference, title, subtitle, body, language_tag, note, created_at"

        /** Ouvre (et crée ou met à jour) la base [path], puis charge les favoris. [now] : millisecondes depuis 1970. */
        suspend fun open(path: String, now: () -> Long): FavoritesRepository = withContext(IoDispatcher) {
            val db = SqliteDb.open(path)
            try {
                UserSchema.migrate(db)
            } catch (e: Throwable) {
                db.close()
                throw e
            }
            FavoritesRepository(db, now).also { it.refresh() }
        }

        private fun SQLiteStatement.toFavorite() = Favorite(
            id = getLong(0),
            kind = FavoriteKind.valueOf(getText(1)),
            museumId = textOrNull(2),
            museumName = textOrNull(3),
            reference = textOrNull(4),
            title = getText(5),
            subtitle = textOrNull(6),
            body = getText(7),
            languageTag = getText(8),
            note = textOrNull(9),
            createdAt = getLong(10),
        )
    }
}
