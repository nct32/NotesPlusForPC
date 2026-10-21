package notesplus.data

import notesplus.io.FolderDto
import notesplus.io.NoteDto
import notesplus.io.NotesBackup
import notesplus.io.ReminderDto
import notesplus.io.RemindersBackup
import java.sql.Connection
import java.util.UUID

/** How to handle an imported item whose ID already exists with different content. */
enum class Resolution { MERGE, SKIP, OVERWRITE }

/** An imported item that clashes with an existing one. */
data class Conflict(val id: String, val label: String)

data class ImportResult(val added: Int, val merged: Int, val overwritten: Int, val skipped: Int)

/**
 * Export and import of Notes and Reminders. Import is all-or-nothing: one transaction.
 * [importedSuffix] is appended to the title of the imported copy on Merge.
 */
class BackupRepo(private val db: Db, private val clock: () -> Long = System::currentTimeMillis) {

    // ---- Notes ----

    /** Live Notes (not Trash) with the Folders they use. */
    fun exportNotes(): NotesBackup = db.read { c ->
        val notes = c.createStatement().use { st ->
            st.executeQuery(
                """
                SELECT n.* FROM note n LEFT JOIN folder f ON f.id = n.folder_id
                WHERE n.deleted_at IS NULL AND f.deleted_at IS NULL ORDER BY n.created_at
                """.trimIndent()
            ).use { rs -> buildList { while (rs.next()) add(rs.toNote()) } }
        }
        val folders = c.createStatement().use { st ->
            st.executeQuery("SELECT * FROM folder WHERE deleted_at IS NULL ORDER BY name").use { rs ->
                buildList { while (rs.next()) add(rs.toFolder()) }
            }
        }
        NotesBackup(
            folders = folders.map { FolderDto(it.id, it.name) },
            notes = notes.map { n ->
                NoteDto(n.id, n.title, n.body, n.folderId, n.pinned, n.createdAt, n.modifiedAt, c.tagsOf(n.id))
            },
        )
    }

    fun noteConflicts(b: NotesBackup): List<Conflict> = db.read { c ->
        b.notes.mapNotNull { n ->
            val existing = c.noteRow(n.id) ?: return@mapNotNull null
            if (existing.first == n.title && existing.second == n.body) null else Conflict(n.id, n.title)
        }
    }

    fun importNotes(b: NotesBackup, resolve: (String) -> Resolution, importedSuffix: String): ImportResult = db.tx { c ->
        var added = 0
        var merged = 0
        var overwritten = 0
        var skipped = 0
        val folderMap = mutableMapOf<String, String>()
        val folderNames = b.folders.associate { it.id to it.name }
        fun folderFor(id: String?): String? = id?.let { fid -> folderMap.getOrPut(fid) { c.importFolder(fid, folderNames.getValue(fid)) } }

        for (n in b.notes) {
            val existing = c.noteRow(n.id)
            when {
                existing == null -> {
                    c.insertNote(n.id, n, folderFor(n.folderId))
                    added++
                }
                existing.first == n.title && existing.second == n.body -> skipped++
                else -> when (resolve(n.id)) {
                    Resolution.MERGE -> {
                        c.insertNote(UUID.randomUUID().toString(), n.copy(title = n.title + importedSuffix), folderFor(n.folderId))
                        merged++
                    }
                    Resolution.SKIP -> skipped++
                    Resolution.OVERWRITE -> {
                        c.overwriteNote(n, folderFor(n.folderId))
                        overwritten++
                    }
                }
            }
        }
        c.deleteOrphanTags()
        ImportResult(added, merged, overwritten, skipped)
    }

    /** Reuses a live Folder with this ID; one stuck in Trash gets a fresh copy so imported Notes stay visible. */
    private fun Connection.importFolder(id: String, name: String): String {
        val trashed = prepareStatement("SELECT deleted_at FROM folder WHERE id = ?").use {
            it.setString(1, id)
            it.executeQuery().use { rs -> if (rs.next()) rs.getObject(1) != null else null }
        }
        if (trashed == false) return id
        val newId = if (trashed == null) id else UUID.randomUUID().toString()
        prepareStatement("INSERT INTO folder (id, name, created_at) VALUES (?, ?, ?)").use {
            it.setString(1, newId)
            it.setString(2, name)
            it.setLong(3, clock())
            it.executeUpdate()
        }
        return newId
    }

    private fun Connection.noteRow(id: String): Pair<String, String>? =
        prepareStatement("SELECT title, body FROM note WHERE id = ?").use {
            it.setString(1, id)
            it.executeQuery().use { rs -> if (rs.next()) rs.getString(1) to rs.getString(2) else null }
        }

    private fun Connection.insertNote(id: String, n: NoteDto, folderId: String?) {
        prepareStatement(
            "INSERT INTO note (id, title, body, folder_id, pinned, created_at, modified_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
        ).use {
            it.setString(1, id)
            it.setString(2, n.title)
            it.setString(3, n.body)
            it.setString(4, folderId)
            it.setInt(5, if (n.pinned) 1 else 0)
            it.setLong(6, n.createdAt)
            it.setLong(7, n.modifiedAt)
            it.executeUpdate()
        }
        replaceTags(id, n.tags)
    }

    /** Replaces the existing Note with the imported one; a trashed Note comes back. */
    private fun Connection.overwriteNote(n: NoteDto, folderId: String?) {
        prepareStatement(
            "UPDATE note SET title = ?, body = ?, folder_id = ?, pinned = ?, created_at = ?, modified_at = ?, deleted_at = NULL WHERE id = ?"
        ).use {
            it.setString(1, n.title)
            it.setString(2, n.body)
            it.setString(3, folderId)
            it.setInt(4, if (n.pinned) 1 else 0)
            it.setLong(5, n.createdAt)
            it.setLong(6, n.modifiedAt)
            it.setString(7, n.id)
            it.executeUpdate()
        }
        replaceTags(n.id, n.tags)
    }

    // ---- Reminders ----

    fun exportReminders(): RemindersBackup = db.read { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT id, title, description, due_at FROM reminder ORDER BY due_at").use { rs ->
                RemindersBackup(reminders = buildList {
                    while (rs.next()) add(ReminderDto(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4)))
                })
            }
        }
    }

    fun reminderConflicts(b: RemindersBackup): List<Conflict> = db.read { c ->
        b.reminders.mapNotNull { r ->
            val existing = c.reminderRow(r.id) ?: return@mapNotNull null
            if (existing == r) null else Conflict(r.id, r.title)
        }
    }

    fun importReminders(b: RemindersBackup, resolve: (String) -> Resolution, importedSuffix: String): ImportResult = db.tx { c ->
        var added = 0
        var merged = 0
        var overwritten = 0
        var skipped = 0
        for (r in b.reminders) {
            val existing = c.reminderRow(r.id)
            when {
                existing == null -> {
                    c.insertReminder(r.id, r)
                    added++
                }
                existing == r -> skipped++
                else -> when (resolve(r.id)) {
                    Resolution.MERGE -> {
                        c.insertReminder(UUID.randomUUID().toString(), r.copy(title = r.title + importedSuffix))
                        merged++
                    }
                    Resolution.SKIP -> skipped++
                    Resolution.OVERWRITE -> {
                        c.prepareStatement("DELETE FROM reminder WHERE id = ?").use {
                            it.setString(1, r.id)
                            it.executeUpdate()
                        }
                        c.insertReminder(r.id, r)
                        overwritten++
                    }
                }
            }
        }
        ImportResult(added, merged, overwritten, skipped)
    }

    private fun Connection.reminderRow(id: String): ReminderDto? =
        prepareStatement("SELECT id, title, description, due_at FROM reminder WHERE id = ?").use {
            it.setString(1, id)
            it.executeQuery().use { rs -> if (rs.next()) ReminderDto(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4)) else null }
        }

    /** Like creating one: a Reminder whose time has already passed never fires. */
    private fun Connection.insertReminder(id: String, r: ReminderDto) {
        prepareStatement("INSERT INTO reminder (id, title, description, due_at, notified) VALUES (?, ?, ?, ?, ?)").use {
            it.setString(1, id)
            it.setString(2, r.title)
            it.setString(3, r.description)
            it.setLong(4, r.dueAt)
            it.setInt(5, if (r.dueAt <= clock()) 1 else 0)
            it.executeUpdate()
        }
    }
}
