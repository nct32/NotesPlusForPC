package notesplus.data

import notesplus.domain.DAY_MS
import notesplus.domain.FolderFilter
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrashAndFoldersTest {
    private val dir = Files.createTempDirectory("notesplus-test")
    private val db = Db.open(dir.resolve("test.db"))
    private var now = 1_000_000L
    private val clock = { now }
    private val notes = NoteRepo(db, clock)
    private val folders = FolderRepo(db, clock)
    private val tags = TagRepo(db)
    private val trash = TrashRepo(db, clock)

    @AfterTest
    fun cleanup() {
        db.close()
        dir.toFile().deleteRecursively()
    }

    private fun note(title: String, folderId: String? = null): String {
        val id = notes.create(folderId).id
        notes.save(id, title, "")
        return id
    }

    private fun titles(filter: FolderFilter = FolderFilter.All) = notes.list(folder = filter).map { it.title }.sorted()

    @Test
    fun folderFilterShowsOnlyMatchingNotes() {
        val f = folders.create("Work")
        note("in work", f.id)
        note("loose")
        assertEquals(listOf("in work", "loose"), titles())
        assertEquals(listOf("loose"), titles(FolderFilter.NoFolder))
        assertEquals(listOf("in work"), titles(FolderFilter.In(f.id)))
    }

    @Test
    fun searchFindsNotesByTagAndFolderName() {
        val f = folders.create("Recipes")
        val a = note("pasta", f.id)
        val b = note("meeting")
        tags.setTags(b, listOf("Urgent", "urgent", " ", "Q4"))
        assertEquals(listOf("Q4", "Urgent"), tags.tagsFor(b))
        assertEquals(listOf("pasta"), notes.list("recipes").map { it.title })
        assertEquals(listOf("meeting"), notes.list("urg").map { it.title })
        tags.setTags(a, emptyList())
    }

    @Test
    fun unusedTagsAreRemoved() {
        val id = note("x")
        tags.setTags(id, listOf("temp"))
        tags.setTags(id, emptyList())
        val count = db.read { c -> c.createStatement().use { it.executeQuery("SELECT COUNT(*) FROM tag").use { rs -> rs.getInt(1) } } }
        assertEquals(0, count)
    }

    @Test
    fun trashedFolderHidesItsNotesAndHoldsThemInTrash() {
        val f = folders.create("Old")
        val id = note("inside", f.id)
        assertEquals(1, folders.noteCount(f.id))
        folders.trash(f.id)

        assertTrue(folders.list().isEmpty())
        assertTrue(notes.list().isEmpty())
        assertFalse(notes.save(id, "inside", "edited"), "notes in a trashed folder can't be edited")
        val contents = trash.contents()
        assertEquals(listOf("inside"), contents.folders.single().notes.map { it.title })
        assertTrue(contents.notes.isEmpty())
    }

    @Test
    fun restoringAFolderRestoresItsNotesButNotOnesTrashedEarlier() {
        val f = folders.create("F")
        note("stays", f.id)
        val earlier = note("trashed first", f.id)
        notes.trash(earlier)
        now += DAY_MS
        folders.trash(f.id)

        trash.restoreFolder(f.id)
        assertEquals(listOf("stays"), titles(FolderFilter.In(f.id)))
        assertEquals(listOf("trashed first"), trash.contents().notes.map { it.title })
    }

    @Test
    fun restoringOneNoteFromATrashedFolderLeavesItWithNoFolder() {
        val f = folders.create("F")
        val id = note("rescued", f.id)
        note("left behind", f.id)
        folders.trash(f.id)

        trash.restoreNote(id)
        assertNull(notes.get(id)!!.folderId)
        assertEquals(listOf("rescued"), titles(FolderFilter.NoFolder))
        assertEquals(listOf("left behind"), trash.contents().folders.single().notes.map { it.title })
    }

    @Test
    fun restoringANoteReturnsItToItsLiveFolder() {
        val f = folders.create("F")
        val id = note("n", f.id)
        notes.trash(id)
        trash.restoreNote(id)
        assertEquals(f.id, notes.get(id)!!.folderId)
    }

    @Test
    fun permanentlyDeletingAFolderDeletesItsNotes() {
        val f = folders.create("F")
        val id = note("n", f.id)
        folders.trash(f.id)
        trash.deleteFolder(f.id)
        assertNull(notes.get(id))
        assertTrue(trash.contents().folders.isEmpty())
    }

    @Test
    fun purgeDeletesOnlyItemsOlderThan30Days() {
        val old = note("old")
        notes.trash(old)
        val oldFolder = folders.create("old folder")
        val inOldFolder = note("in old folder", oldFolder.id)
        folders.trash(oldFolder.id)

        now += 29 * DAY_MS
        val recent = note("recent")
        notes.trash(recent)

        now += 1 * DAY_MS + 1
        trash.purge()
        assertNull(notes.get(old))
        assertNull(notes.get(inOldFolder))
        assertEquals(listOf("recent"), trash.contents().notes.map { it.title })
        assertTrue(trash.contents().folders.isEmpty())
    }

    @Test
    fun emptyTrashRemovesEverythingTrashedOnly() {
        val keep = note("keep")
        val gone = note("gone")
        notes.trash(gone)
        val f = folders.create("F")
        note("in f", f.id)
        folders.trash(f.id)

        trash.empty()
        val contents = trash.contents()
        assertTrue(contents.notes.isEmpty() && contents.folders.isEmpty())
        assertEquals(listOf("keep"), titles())
        assertEquals("keep", notes.get(keep)!!.title)
    }

    @Test
    fun noteCreatedOrMovedIntoAFolderGoneElsewhereGetsNoFolder() {
        val trashed = folders.create("trashed elsewhere")
        val deleted = folders.create("deleted elsewhere")
        folders.trash(trashed.id)
        folders.trash(deleted.id)
        trash.deleteFolder(deleted.id)

        val a = notes.create(trashed.id)
        val b = notes.create(deleted.id)
        assertNull(a.folderId)
        assertNull(notes.get(b.id)!!.folderId)
        assertEquals(2, notes.list().size, "both stay visible instead of vanishing into Trash")

        notes.setFolder(a.id, deleted.id)
        assertNull(notes.get(a.id)!!.folderId)
    }

    @Test
    fun settingTagsOnANoteDeletedElsewhereDoesNothing() {
        val id = note("gone")
        notes.trash(id)
        trash.deleteNote(id)
        tags.setTags(id, listOf("x")) // must not throw
        assertTrue(tags.tagsFor(id).isEmpty())
    }

    @Test
    fun deleteNoteOnlyAffectsTrashedNotes() {
        val live = note("live")
        trash.deleteNote(live)
        assertEquals("live", notes.get(live)!!.title)
    }
}
