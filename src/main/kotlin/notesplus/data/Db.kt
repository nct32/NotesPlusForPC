package notesplus.data

import org.sqlite.Function
import org.sqlite.SQLiteConfig
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * One SQLite connection per Instance. Several Instances share the same file (ADR 0002),
 * so WAL mode and a busy timeout are required. All access is serialized on this object.
 */
class Db private constructor(private val conn: Connection) : AutoCloseable {

    @Synchronized
    fun <T> read(block: (Connection) -> T): T = block(conn)

    @Synchronized
    fun <T> tx(block: (Connection) -> T): T {
        conn.autoCommit = false
        try {
            val result = block(conn)
            conn.commit()
            return result
        } catch (e: Throwable) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    /** Changes whenever another connection (another Instance or the Tray Host) commits. */
    @Synchronized
    fun dataVersion(): Long = conn.createStatement().use { st ->
        st.executeQuery("PRAGMA data_version").use { rs -> rs.getLong(1) }
    }

    @Synchronized
    override fun close() = conn.close()

    companion object {
        private const val SCHEMA_VERSION = 1

        fun defaultFile(): Path =
            Path.of(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Notes+", "notes.db")

        fun open(file: Path = defaultFile()): Db {
            file.toAbsolutePath().parent?.let { Files.createDirectories(it) }
            val config = SQLiteConfig().apply {
                setBusyTimeout(10_000)
                setJournalMode(SQLiteConfig.JournalMode.WAL)
                enforceForeignKeys(true)
                // Write transactions take the write lock up front. With the default DEFERRED mode,
                // a transaction that reads and then writes fails at once (busy timeout or not)
                // if another process wrote in between.
                setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
            }
            val conn = DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}", config.toProperties())
            // SQLite's lower() only folds ASCII; this one folds all of Unicode for search.
            Function.create(conn, "np_lower", object : Function() {
                override fun xFunc() {
                    val v = value_text(0)
                    if (v == null) result() else result(v.lowercase())
                }
            })
            migrate(conn)
            return Db(conn)
        }

        private fun migrate(conn: Connection) {
            val version = conn.createStatement().use { it.executeQuery("PRAGMA user_version").use { rs -> rs.getInt(1) } }
            if (version >= SCHEMA_VERSION) return
            conn.autoCommit = false
            try {
                conn.createStatement().use { st ->
                    SCHEMA_V1.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { st.execute(it) }
                    st.execute("PRAGMA user_version = $SCHEMA_VERSION")
                }
                conn.commit()
            } catch (e: Throwable) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }

        private val SCHEMA_V1 = """
            CREATE TABLE IF NOT EXISTS folder (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                deleted_at INTEGER
            );
            CREATE TABLE IF NOT EXISTS note (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL DEFAULT '',
                body TEXT NOT NULL DEFAULT '',
                folder_id TEXT REFERENCES folder(id) ON DELETE CASCADE,
                pinned INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                modified_at INTEGER NOT NULL,
                deleted_at INTEGER
            );
            CREATE TABLE IF NOT EXISTS tag (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL UNIQUE
            );
            CREATE TABLE IF NOT EXISTS note_tag (
                note_id TEXT NOT NULL REFERENCES note(id) ON DELETE CASCADE,
                tag_id TEXT NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
                PRIMARY KEY (note_id, tag_id)
            );
            CREATE TABLE IF NOT EXISTS reminder (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '' CHECK (length(description) <= 299),
                due_at INTEGER NOT NULL,
                notified INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS setting (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
        """.trimIndent()
    }
}
