package notesplus.data

class TagRepo(private val db: Db) {

    fun tagsFor(noteId: String): List<String> = db.read { c -> c.tagsOf(noteId) }

    /**
     * Replaces a Note's Tags. Blank names are dropped and duplicates are merged ignoring case.
     * Does nothing if another Instance deleted the Note meanwhile.
     */
    fun setTags(noteId: String, names: List<String>) = db.tx { c ->
        val exists = c.prepareStatement("SELECT 1 FROM note WHERE id = ?").use {
            it.setString(1, noteId)
            it.executeQuery().use { rs -> rs.next() }
        }
        if (!exists) return@tx
        c.replaceTags(noteId, names)
        c.deleteOrphanTags()
    }
}
