package notesplus.data

import notesplus.domain.Folder
import notesplus.domain.Note
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

internal fun ResultSet.toNote() = Note(
    id = getString("id"),
    title = getString("title"),
    body = getString("body"),
    folderId = getString("folder_id"),
    pinned = getInt("pinned") != 0,
    createdAt = getLong("created_at"),
    modifiedAt = getLong("modified_at"),
    deletedAt = getLong("deleted_at").takeUnless { wasNull() },
)

internal fun ResultSet.toFolder() = Folder(
    id = getString("id"),
    name = getString("name"),
    deletedAt = getLong("deleted_at").takeUnless { wasNull() },
)

/** Replaces a Note's Tags. Blank names are dropped and duplicates are merged ignoring case. */
internal fun Connection.replaceTags(noteId: String, names: List<String>) {
    prepareStatement("DELETE FROM note_tag WHERE note_id = ?").use {
        it.setString(1, noteId)
        it.executeUpdate()
    }
    for (name in names.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }) {
        prepareStatement("INSERT OR IGNORE INTO tag (id, name) VALUES (?, ?)").use {
            it.setString(1, UUID.randomUUID().toString())
            it.setString(2, name)
            it.executeUpdate()
        }
        prepareStatement("INSERT OR IGNORE INTO note_tag (note_id, tag_id) SELECT ?, id FROM tag WHERE name = ?").use {
            it.setString(1, noteId)
            it.setString(2, name)
            it.executeUpdate()
        }
    }
}

internal fun Connection.tagsOf(noteId: String): List<String> =
    prepareStatement(
        "SELECT t.name FROM note_tag nt JOIN tag t ON t.id = nt.tag_id WHERE nt.note_id = ? ORDER BY np_lower(t.name)"
    ).use {
        it.setString(1, noteId)
        it.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
    }

internal fun Connection.deleteOrphanTags() {
    createStatement().use { it.executeUpdate("DELETE FROM tag WHERE id NOT IN (SELECT tag_id FROM note_tag)") }
}
