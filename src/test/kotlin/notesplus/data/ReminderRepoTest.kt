package notesplus.data

import java.nio.file.Files
import java.sql.SQLException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderRepoTest {
    private val dir = Files.createTempDirectory("notesplus-test")
    private val db = Db.open(dir.resolve("test.db"))
    private var now = 0L
    private val repo = ReminderRepo(db) { now }

    @AfterTest
    fun cleanup() {
        db.close()
        dir.toFile().deleteRecursively()
    }

    @Test
    fun listsSoonestFirst() {
        repo.create("later", "", 3_000)
        repo.create("sooner", "", 1_000)
        repo.create("middle", "", 2_000)
        assertEquals(listOf("sooner", "middle", "later"), repo.list().map { it.title })
    }

    @Test
    fun searchMatchesTitleAndDescriptionIgnoringCase() {
        repo.create("Dentist", "bring CARD", 1)
        repo.create("โทรหาแม่", "", 2)
        assertEquals(listOf("Dentist"), repo.list("card").map { it.title })
        assertEquals(listOf("โทรหาแม่"), repo.list("แม่").map { it.title })
    }

    @Test
    fun rejectsBlankTitleAndLongDescription() {
        assertFailsWith<IllegalArgumentException> { repo.create("  ", "", 1) }
        assertFailsWith<IllegalArgumentException> { repo.create("t", "x".repeat(300), 1) }
        repo.create("t", "x".repeat(299), 1)
    }

    @Test
    fun databaseAlsoEnforcesDescriptionLimit() {
        assertFailsWith<SQLException> {
            db.tx { c ->
                c.createStatement().use {
                    it.executeUpdate("INSERT INTO reminder (id, title, description, due_at) VALUES ('x', 't', '${"x".repeat(300)}', 1)")
                }
            }
        }
    }

    @Test
    fun aReminderCreatedInThePastNeverFires() {
        now = 10_000
        val past = repo.create("past", "", 5_000)
        assertTrue(past.notified)
        assertTrue(repo.takeDue().isEmpty())
    }

    @Test
    fun takeDueFiresEachReminderOnceWhenItsTimeComes() {
        repo.create("a", "", 1_000)
        repo.create("b", "", 2_000)
        now = 999
        assertTrue(repo.takeDue().isEmpty())
        now = 1_500
        assertEquals(listOf("a"), repo.takeDue().map { it.title })
        assertTrue(repo.takeDue().isEmpty(), "never fires twice")
        now = 5_000
        assertEquals(listOf("b"), repo.takeDue().map { it.title })
    }

    @Test
    fun markHandledUpToSilencesRemindersThatPassedWhileClosed() {
        repo.create("missed", "", 1_000)
        repo.create("upcoming", "", 9_000)
        now = 5_000
        repo.markHandledUpTo(now)
        assertTrue(repo.takeDue().isEmpty())
        now = 9_000
        assertEquals(listOf("upcoming"), repo.takeDue().map { it.title })
    }

    @Test
    fun reschedulingToTheFutureFiresAgainButEditingTextDoesNot() {
        val r = repo.create("t", "", 1_000)
        now = 2_000
        repo.takeDue()

        repo.update(r.id, "renamed", "d", 1_000)
        assertTrue(repo.get(r.id)!!.notified, "editing text keeps it handled")

        repo.update(r.id, "renamed", "d", 5_000)
        assertFalse(repo.get(r.id)!!.notified, "a new future time fires again")

        repo.update(r.id, "renamed", "d", 1_500)
        assertTrue(repo.get(r.id)!!.notified, "a new past time never fires")
    }

    @Test
    fun deleteRemoves() {
        val r = repo.create("t", "", 1)
        repo.delete(r.id)
        assertNull(repo.get(r.id))
    }

    @Test
    fun overdueMeansDueTimeHasPassed() {
        val r = repo.create("t", "", 1_000)
        assertTrue(r.isOverdue(1_000))
        assertFalse(r.isOverdue(999))
    }
}
