package notesplus.data

import notesplus.domain.SortOrder
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteRepoTest {
    private val dir = Files.createTempDirectory("notesplus-test")
    private val db = Db.open(dir.resolve("test.db"))
    private var now = 1_000L
    private val repo = NoteRepo(db) { now }

    @AfterTest
    fun cleanup() {
        db.close()
        dir.toFile().deleteRecursively()
    }

    private fun note(title: String, body: String = ""): String {
        val id = repo.create().id
        now += 10
        repo.save(id, title, body)
        return id
    }

    @Test
    fun saveMovesModifiedDateOnlyWhenContentChanges() {
        val id = note("A", "x")
        val first = repo.get(id)!!.modifiedAt
        now += 100
        assertFalse(repo.save(id, "A", "x"))
        assertEquals(first, repo.get(id)!!.modifiedAt)
        assertTrue(repo.save(id, "A", "y"))
        assertEquals(now, repo.get(id)!!.modifiedAt)
    }

    @Test
    fun searchIsCaseInsensitiveAcrossScripts() {
        note("Shopping", "buy MILK")
        note("ภาษาไทย", "สวัสดีครับ")
        note("ÁGUA", "português")
        note("日本語", "こんにちは世界")

        assertEquals(listOf("Shopping"), repo.list("milk").map { it.title })
        assertEquals(listOf("ภาษาไทย"), repo.list("ดีคร").map { it.title })
        assertEquals(listOf("ÁGUA"), repo.list("água").map { it.title })
        assertEquals(listOf("日本語"), repo.list("世界").map { it.title })
    }

    @Test
    fun searchTreatsWildcardsLiterally() {
        note("100% done")
        note("1000 done")
        assertEquals(listOf("100% done"), repo.list("0%").map { it.title })
        assertEquals(emptyList(), repo.list("_").map { it.title })
    }

    @Test
    fun pinnedNotesStayOnTopOfEverySort() {
        note("b", "xx")
        val pinned = note("z", "")
        note("a", "xxxxxx")
        repo.setPinned(pinned, true)

        assertEquals(listOf("z", "a", "b"), repo.list(sort = SortOrder.NEWEST).map { it.title })
        assertEquals(listOf("z", "b", "a"), repo.list(sort = SortOrder.OLDEST).map { it.title })
        assertEquals(listOf("z", "a", "b"), repo.list(sort = SortOrder.TITLE_AZ).map { it.title })
        assertEquals(listOf("z", "a", "b"), repo.list(sort = SortOrder.SIZE).map { it.title })
    }

    @Test
    fun trashedNotesAreHiddenAndCannotBeEdited() {
        val id = note("gone", "body")
        repo.trash(id)
        assertTrue(repo.list().isEmpty())
        assertTrue(repo.list("gone").isEmpty())
        assertFalse(repo.save(id, "gone", "edited"))
        assertEquals("body", repo.get(id)!!.body)
    }

    @Test
    fun settingsDefaultAndPersist() {
        val settings = SettingsRepo(db)
        assertTrue(settings.autosave)
        assertEquals(SortOrder.NEWEST, settings.sortOrder)
        settings.autosave = false
        settings.sortOrder = SortOrder.SIZE
        assertFalse(settings.autosave)
        assertEquals(SortOrder.SIZE, settings.sortOrder)
    }
}
