package notesplus.data

import notesplus.domain.DAY_MS
import notesplus.domain.Note
import notesplus.domain.TRASH_DAYS
import notesplus.domain.TrashContents
import notesplus.domain.TrashedFolder
import java.sql.Connection

class TrashRepo(private val db: Db, private val clock: () -> Long = System::currentTimeMillis) {

    fun contents(): TrashContents = db.read { c ->
        val folders = c.createStatement().use { st ->
            st.executeQuery("SELECT * FROM folder WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC").use { rs ->
                buildList { while (rs.next()) add(rs.toFolder()) }
            }
        }
        val trashedFolders = folders.map { f ->
            val notes = c.prepareStatement("SELECT * FROM note WHERE folder_id = ? ORDER BY modified_at DESC").use {
                it.setString(1, f.id)
                it.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toNote()) } }
            }
            TrashedFolder(f, notes)
        }
        val loose = c.createStatement().use { st ->
            st.executeQuery(
                """
                SELECT n.* FROM note n LEFT JOIN folder f ON f.id = n.folder_id
                WHERE n.deleted_at IS NOT NULL AND f.deleted_at IS NULL
                ORDER BY n.deleted_at DESC
                """.trimIndent()
            ).use { rs -> buildList<Note> { while (rs.next()) add(rs.toNote()) } }
        }
        TrashContents(trashedFolders, loose)
    }

    /**
     * Restores one Note. If it sits in a trashed Folder it comes out with no Folder;
     * otherwise it returns to its own Folder.
     */
    fun restoreNote(id: String) = db.tx { c ->
        c.prepareStatement(
            """
            UPDATE note SET deleted_at = NULL,
                folder_id = CASE WHEN folder_id IN (SELECT id FROM folder WHERE deleted_at IS NOT NULL)
                                 THEN NULL ELSE folder_id END
            WHERE id = ?
            """.trimIndent()
        ).use {
            it.setString(1, id)
            it.executeUpdate()
        }
    }

    /** Restores a Folder with its Notes. Notes trashed on their own earlier stay in Trash. */
    fun restoreFolder(id: String) = db.tx { c ->
        c.prepareStatement("UPDATE folder SET deleted_at = NULL WHERE id = ?").use {
            it.setString(1, id)
            it.executeUpdate()
        }
    }

    fun deleteNote(id: String) = db.tx { c ->
        c.prepareStatement(
            """
            DELETE FROM note WHERE id = ? AND (deleted_at IS NOT NULL
                OR folder_id IN (SELECT id FROM folder WHERE deleted_at IS NOT NULL))
            """.trimIndent()
        ).use {
            it.setString(1, id)
            it.executeUpdate()
        }
        c.deleteOrphanTags()
    }

    /** Permanently deletes a trashed Folder; its Notes go with it. */
    fun deleteFolder(id: String) = db.tx { c ->
        c.prepareStatement("DELETE FROM folder WHERE id = ? AND deleted_at IS NOT NULL").use {
            it.setString(1, id)
            it.executeUpdate()
        }
        c.deleteOrphanTags()
    }

    fun empty() = db.tx { c -> deleteTrashedBefore(c, Long.MAX_VALUE) }

    /** Permanently deletes everything trashed more than 30 days ago. */
    fun purge() = db.tx { c -> deleteTrashedBefore(c, clock() - TRASH_DAYS * DAY_MS) }

    private fun deleteTrashedBefore(c: Connection, cutoff: Long) {
        for (table in listOf("folder", "note")) {
            c.prepareStatement("DELETE FROM $table WHERE deleted_at IS NOT NULL AND deleted_at < ?").use {
                it.setLong(1, cutoff)
                it.executeUpdate()
            }
        }
        c.deleteOrphanTags()
    }
}
