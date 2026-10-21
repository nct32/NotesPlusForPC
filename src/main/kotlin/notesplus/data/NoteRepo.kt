package notesplus.data

import notesplus.domain.FolderFilter
import notesplus.domain.Note
import notesplus.domain.SortOrder
import java.sql.Connection
import java.util.UUID

class NoteRepo(private val db: Db, private val clock: () -> Long = System::currentTimeMillis) {

    /**
     * Creates an empty Note in [folderId]. If another Instance trashed or deleted that Folder
     * meanwhile, the Note gets no Folder instead.
     */
    fun create(folderId: String? = null): Note = db.tx { c ->
        val now = clock()
        val note = Note(UUID.randomUUID().toString(), "", "", c.liveFolderOrNull(folderId), false, now, now)
        c.prepareStatement("INSERT INTO note (id, title, body, folder_id, created_at, modified_at) VALUES (?, '', '', ?, ?, ?)").use {
            it.setString(1, note.id)
            it.setString(2, note.folderId)
            it.setLong(3, now)
            it.setLong(4, now)
            it.executeUpdate()
        }
        note
    }

    fun get(id: String): Note? = db.read { c ->
        c.prepareStatement("SELECT * FROM note WHERE id = ?").use {
            it.setString(1, id)
            it.executeQuery().use { rs -> if (rs.next()) rs.toNote() else null }
        }
    }

    /** Live Notes (not in Trash, not in a trashed Folder) matching [query], pinned first. */
    fun list(
        query: String = "",
        sort: SortOrder = SortOrder.NEWEST,
        folder: FolderFilter = FolderFilter.All,
    ): List<Note> {
        val order = when (sort) {
            SortOrder.NEWEST -> "n.modified_at DESC"
            SortOrder.OLDEST -> "n.modified_at ASC"
            SortOrder.TITLE_AZ -> "np_lower(n.title) ASC"
            SortOrder.SIZE -> "length(n.body) DESC"
        }
        val folderClause = when (folder) {
            FolderFilter.All -> ""
            FolderFilter.NoFolder -> "AND n.folder_id IS NULL"
            is FolderFilter.In -> "AND n.folder_id = ?"
        }
        val q = query.trim()
        val sql = """
            SELECT n.* FROM note n LEFT JOIN folder f ON f.id = n.folder_id
            WHERE n.deleted_at IS NULL AND f.deleted_at IS NULL $folderClause
              AND (? = ''
                   OR np_lower(n.title) LIKE ? ESCAPE '\'
                   OR np_lower(n.body) LIKE ? ESCAPE '\'
                   OR np_lower(COALESCE(f.name, '')) LIKE ? ESCAPE '\'
                   OR EXISTS (SELECT 1 FROM note_tag nt JOIN tag t ON t.id = nt.tag_id
                              WHERE nt.note_id = n.id AND np_lower(t.name) LIKE ? ESCAPE '\'))
            ORDER BY n.pinned DESC, $order, n.id
        """.trimIndent()
        val pattern = "%" + escapeLike(q.lowercase()) + "%"
        return db.read { c ->
            c.prepareStatement(sql).use { st ->
                var i = 1
                if (folder is FolderFilter.In) st.setString(i++, folder.folderId)
                st.setString(i++, q)
                repeat(4) { st.setString(i++, pattern) }
                st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toNote()) } }
            }
        }
    }

    /**
     * Saves title and body. The modified date only moves when the content actually changed.
     * Trashed Notes are never written. Returns true when a change was saved.
     */
    fun save(id: String, title: String, body: String): Boolean = db.tx { c ->
        c.prepareStatement(
            """
            UPDATE note SET title = ?, body = ?, modified_at = ?
            WHERE id = ? AND deleted_at IS NULL AND (title <> ? OR body <> ?)
              AND NOT EXISTS (SELECT 1 FROM folder f WHERE f.id = note.folder_id AND f.deleted_at IS NOT NULL)
            """.trimIndent()
        ).use {
            it.setString(1, title)
            it.setString(2, body)
            it.setLong(3, clock())
            it.setString(4, id)
            it.setString(5, title)
            it.setString(6, body)
            it.executeUpdate() > 0
        }
    }

    fun setPinned(id: String, pinned: Boolean) = db.tx { c ->
        c.prepareStatement("UPDATE note SET pinned = ? WHERE id = ?").use {
            it.setInt(1, if (pinned) 1 else 0)
            it.setString(2, id)
            it.executeUpdate()
        }
    }

    /** Moves a live Note into [folderId] (or out of any Folder when null, or when that Folder is gone). */
    fun setFolder(id: String, folderId: String?) = db.tx { c ->
        c.prepareStatement("UPDATE note SET folder_id = ? WHERE id = ? AND deleted_at IS NULL").use {
            it.setString(1, c.liveFolderOrNull(folderId))
            it.setString(2, id)
            it.executeUpdate()
        }
    }

    private fun Connection.liveFolderOrNull(folderId: String?): String? {
        if (folderId == null) return null
        return prepareStatement("SELECT id FROM folder WHERE id = ? AND deleted_at IS NULL").use {
            it.setString(1, folderId)
            it.executeQuery().use { rs -> if (rs.next()) folderId else null }
        }
    }

    fun trash(id: String) = db.tx { c ->
        c.prepareStatement("UPDATE note SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL").use {
            it.setLong(1, clock())
            it.setString(2, id)
            it.executeUpdate()
        }
    }

    private fun escapeLike(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
