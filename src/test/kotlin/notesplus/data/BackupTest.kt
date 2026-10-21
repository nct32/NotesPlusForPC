package notesplus.data

import notesplus.domain.Accent
import notesplus.domain.AppSettings
import notesplus.domain.FolderFilter
import notesplus.domain.Lang
import notesplus.domain.ThemeMode
import notesplus.io.BackupException
import notesplus.io.BackupFiles
import notesplus.io.SettingsBackup
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupTest {
    private val dir = Files.createTempDirectory("notesplus-backup")
    private val source = Db.open(dir.resolve("source.db"))
    private val target = Db.open(dir.resolve("target.db"))
    private var now = 1_000_000L

    @AfterTest
    fun cleanup() {
        source.close()
        target.close()
        dir.toFile().deleteRecursively()
    }

    private fun seedSource(): Pair<String, String> {
        val notes = NoteRepo(source) { now }
        val folder = FolderRepo(source) { now }.create("Work")
        val a = notes.create(folder.id).id
        notes.save(a, "Plan", "## steps")
        notes.setPinned(a, true)
        TagRepo(source).setTags(a, listOf("q4", "urgent"))
        val b = notes.create().id
        notes.save(b, "Loose", "text")
        val trashed = notes.create().id
        notes.save(trashed, "In trash", "")
        notes.trash(trashed)
        return a to b
    }

    private fun exportImportNotes(resolve: (String) -> Resolution = { error("no conflicts expected") }): ImportResult {
        val file = dir.resolve("n.nptxt")
        BackupFiles.writeNotes(file, BackupRepo(source).exportNotes())
        val b = BackupFiles.readNotes(file)
        return BackupRepo(target) { now }.importNotes(b, resolve, " (imported)")
    }

    @Test
    fun notesRoundTripWithFoldersTagsAndPinsButNotTrash() {
        val (a, _) = seedSource()
        val r = exportImportNotes()
        assertEquals(ImportResult(added = 2, merged = 0, overwritten = 0, skipped = 0), r)

        val imported = NoteRepo(target).get(a)!!
        assertEquals("Plan", imported.title)
        assertTrue(imported.pinned)
        assertEquals(listOf("Work"), FolderRepo(target).list().map { it.name })
        assertEquals(imported.folderId, FolderRepo(target).list().single().id)
        assertEquals(listOf("q4", "urgent"), TagRepo(target).tagsFor(a))
        assertEquals(listOf("Loose", "Plan"), NoteRepo(target).list().map { it.title }.sorted())
    }

    @Test
    fun importingTheSameFileTwiceSkipsIdenticalNotesWithoutAsking() {
        seedSource()
        exportImportNotes()
        assertEquals(ImportResult(0, 0, 0, 2), exportImportNotes())
    }

    @Test
    fun conflictResolutions() {
        val (a, b) = seedSource()
        exportImportNotes()
        // Change both in the target so the next import conflicts.
        NoteRepo(target).save(a, "Plan (mine)", "mine")
        NoteRepo(target).save(b, "Loose (mine)", "mine")

        val file = dir.resolve("n.nptxt")
        val backup = BackupFiles.readNotes(file)
        val conflicts = BackupRepo(target).noteConflicts(backup)
        assertEquals(setOf(a, b), conflicts.map { it.id }.toSet())

        val r = BackupRepo(target).importNotes(backup, { if (it == a) Resolution.MERGE else Resolution.OVERWRITE }, " (imported)")
        assertEquals(ImportResult(0, 1, 1, 0), r)
        val titles = NoteRepo(target).list().map { it.title }.sorted()
        assertEquals(listOf("Loose", "Plan (imported)", "Plan (mine)"), titles)
    }

    @Test
    fun skipLeavesYoursAlone() {
        val (a, _) = seedSource()
        exportImportNotes()
        NoteRepo(target).save(a, "mine", "mine")
        val r = BackupRepo(target).importNotes(BackupFiles.readNotes(dir.resolve("n.nptxt")), { Resolution.SKIP }, " (imported)")
        assertEquals(2, r.skipped, "one identical note plus the skipped conflict")
        assertEquals("mine", NoteRepo(target).get(a)!!.title)
    }

    @Test
    fun overwriteBringsATrashedNoteBack() {
        val (_, b) = seedSource()
        exportImportNotes()
        NoteRepo(target).save(b, "changed", "x")
        NoteRepo(target).trash(b)
        BackupRepo(target).importNotes(BackupFiles.readNotes(dir.resolve("n.nptxt")), { Resolution.OVERWRITE }, "")
        assertNull(NoteRepo(target).get(b)!!.deletedAt)
        assertEquals("Loose", NoteRepo(target).get(b)!!.title)
    }

    @Test
    fun notesWhoseFolderIsInTrashGetAFreshFolderInsteadOfVanishing() {
        val (a, _) = seedSource()
        exportImportNotes()
        val folderId = NoteRepo(target).get(a)!!.folderId!!
        FolderRepo(target).trash(folderId)
        TrashRepo(target).restoreNote(a) // a now has no folder; the folder (with nothing else) stays in Trash
        NoteRepo(target).save(a, "different", "")

        BackupRepo(target).importNotes(BackupFiles.readNotes(dir.resolve("n.nptxt")), { Resolution.MERGE }, " (imported)")
        val copy = NoteRepo(target).list().single { it.title == "Plan (imported)" }
        val folder = FolderRepo(target).list().single()
        assertEquals("Work", folder.name)
        assertEquals(folder.id, copy.folderId)
        assertTrue(folder.id != folderId)
        assertEquals(listOf("Plan (imported)"), NoteRepo(target).list(folder = FolderFilter.In(folder.id)).map { it.title })
    }

    @Test
    fun remindersRoundTripAndPastOnesNeverFire() {
        val src = ReminderRepo(source) { 0 }
        src.create("future", "d", 10_000_000)
        src.create("past", "", 10)
        val file = dir.resolve("r.nprmd")
        BackupFiles.writeReminders(file, BackupRepo(source).exportReminders())

        val r = BackupRepo(target) { 5_000_000 }.importReminders(BackupFiles.readReminders(file), { Resolution.SKIP }, "")
        assertEquals(2, r.added)
        val repo = ReminderRepo(target) { 5_000_000 }
        assertTrue(repo.list().single { it.title == "past" }.notified)
        assertEquals(false, repo.list().single { it.title == "future" }.notified)
    }

    @Test
    fun settingsFileAppliesPresentValuesAndKeepsMissingOnes() {
        val file = dir.resolve("s.npcfg")
        val s = AppSettings(ThemeMode.DARK, Accent(3), Lang.TH, autosave = false)
        BackupFiles.writeSettings(file, SettingsBackup.from(s))
        assertEquals(s, BackupFiles.readSettings(file).applyTo(AppSettings()))

        Files.writeString(file, """{ "format": "notesplus-settings", "schemaVersion": 1, "theme": "LIGHT" }""")
        val current = AppSettings(ThemeMode.DARK, Accent(5), Lang.TH, autosave = false)
        assertEquals(current.copy(theme = ThemeMode.LIGHT), BackupFiles.readSettings(file).applyTo(current))
    }

    @Test
    fun badFilesAreRefusedBeforeAnythingIsWritten() {
        fun reason(text: String, read: (java.nio.file.Path) -> Any): BackupException.Reason {
            val f = dir.resolve("bad")
            Files.writeString(f, text)
            return assertFailsWith<BackupException> { read(f) }.reason
        }
        assertEquals(BackupException.Reason.CORRUPT, reason("not json") { BackupFiles.readNotes(it) })
        assertEquals(BackupException.Reason.WRONG_TYPE, reason("""{"format":"notesplus-reminders","schemaVersion":1}""") { BackupFiles.readNotes(it) })
        assertEquals(BackupException.Reason.NEWER_VERSION, reason("""{"format":"notesplus-notes","schemaVersion":2}""") { BackupFiles.readNotes(it) })
        assertEquals(BackupException.Reason.CORRUPT, reason("""{"format":"notesplus-notes","schemaVersion":1,"surprise":1}""") { BackupFiles.readNotes(it) })
        assertEquals(
            BackupException.Reason.INVALID,
            reason("""{"format":"notesplus-notes","schemaVersion":1,"notes":[{"id":"x","title":"t","body":"","folderId":"missing","createdAt":1,"modifiedAt":1}]}""") { BackupFiles.readNotes(it) },
        )
        assertEquals(
            BackupException.Reason.INVALID,
            reason("""{"format":"notesplus-reminders","schemaVersion":1,"reminders":[{"id":"x","title":"t","description":"${"d".repeat(300)}","dueAt":1}]}""") { BackupFiles.readReminders(it) },
        )
        assertEquals(BackupException.Reason.INVALID, reason("""{"format":"notesplus-settings","schemaVersion":1,"accent":"99"}""") { BackupFiles.readSettings(it) })
    }
}
