package notesplus.data

import notesplus.domain.Folder
import java.util.UUID

class FolderRepo(private val db: Db, private val clock: () -> Long = System::currentTimeMillis) {

    /** Live Folders, A–Z. */
    fun list(): List<Folder> = db.read { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT * FROM folder WHERE deleted_at IS NULL ORDER BY np_lower(name), id").use { rs ->
                buildList { while (rs.next()) add(rs.toFolder()) }
            }
        }
    }

    fun create(name: String): Folder {
        val folder = Folder(UUID.randomUUID().toString(), name.trim())
        require(folder.name.isNotEmpty()) { "Folder name is empty" }
        db.tx { c ->
            c.prepareStatement("INSERT INTO folder (id, name, created_at) VALUES (?, ?, ?)").use {
                it.setString(1, folder.id)
                it.setString(2, folder.name)
                it.setLong(3, clock())
                it.executeUpdate()
            }
        }
        return folder
    }

    fun rename(id: String, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Folder name is empty" }
        db.tx { c ->
            c.prepareStatement("UPDATE folder SET name = ? WHERE id = ?").use {
                it.setString(1, trimmed)
                it.setString(2, id)
                it.executeUpdate()
            }
        }
    }

    /** Live Notes inside the Folder (what goes to Trash with it). */
    fun noteCount(id: String): Int = db.read { c ->
        c.prepareStatement("SELECT COUNT(*) FROM note WHERE folder_id = ? AND deleted_at IS NULL").use {
            it.setString(1, id)
            it.executeQuery().use { rs -> rs.getInt(1) }
        }
    }

    /** Sends the Folder to Trash; its Notes go with it, staying inside it. */
    fun trash(id: String) = db.tx { c ->
        c.prepareStatement("UPDATE folder SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL").use {
            it.setLong(1, clock())
            it.setString(2, id)
            it.executeUpdate()
        }
    }
}
