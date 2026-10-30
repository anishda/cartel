package app.audioguide.catalog

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL

/**
 * Mince enveloppe sur `androidx.sqlite` (pilote embarqué : même SQLite sur Android, iOS et JVM).
 * Bloquante et non thread-safe : l'appelant sérialise les accès (voir [Pack]).
 */
class SqliteDb private constructor(private val connection: SQLiteConnection) : AutoCloseable {

    fun exec(sql: String) = connection.execSQL(sql)

    fun <T> query(sql: String, bind: (SQLiteStatement) -> Unit = {}, map: (SQLiteStatement) -> T): List<T> {
        val statement = connection.prepare(sql)
        try {
            bind(statement)
            val rows = mutableListOf<T>()
            while (statement.step()) rows += map(statement)
            return rows
        } finally {
            statement.close()
        }
    }

    fun update(sql: String, bind: (SQLiteStatement) -> Unit = {}) {
        val statement = connection.prepare(sql)
        try {
            bind(statement)
            statement.step()
        } finally {
            statement.close()
        }
    }

    /** Valide ou annule en bloc. */
    fun <T> transaction(block: () -> T): T {
        exec("BEGIN")
        try {
            val result = block()
            exec("COMMIT")
            return result
        } catch (e: Throwable) {
            exec("ROLLBACK")
            throw e
        }
    }

    override fun close() = connection.close()

    companion object {
        fun open(path: String): SqliteDb = SqliteDb(BundledSQLiteDriver().open(path))
    }
}

internal fun SQLiteStatement.textOrNull(index: Int): String? = if (isNull(index)) null else getText(index)

internal fun SQLiteStatement.doubleOrNull(index: Int): Double? = if (isNull(index)) null else getDouble(index)
