package notesplus.data

import notesplus.domain.FolderFilter
import notesplus.domain.SortOrder
import java.nio.file.Path
import kotlin.random.Random
import kotlin.system.exitProcess

/**
 * Run as a separate process by MultiInstanceStressTest: hammers a shared database with random
 * Instance-like work for a number of seconds. Prints "OK <ops>" or the first error, exit code 1.
 */
fun main(args: Array<String>) {
    val dbPath = Path.of(args[0])
    val seconds = args[1].toLong()
    val random = Random(args[2].toLong())

    val db = Db.open(dbPath)
    val notes = NoteRepo(db)
    val folders = FolderRepo(db)
    val tags = TagRepo(db)
    val trash = TrashRepo(db)
    val reminders = ReminderRepo(db)

    val deadline = System.currentTimeMillis() + seconds * 1000
    var ops = 0
    try {
        while (System.currentTimeMillis() < deadline) {
            val live = notes.list(sort = SortOrder.entries.random(random))
            val note = live.randomOrNull(random)
            when (random.nextInt(14)) {
                0, 1 -> notes.create(folders.list().randomOrNull(random)?.id)
                2, 3, 4 -> note?.let { notes.save(it.id, "title ${random.nextInt(1000)}", "body ".repeat(random.nextInt(50))) }
                5 -> note?.let { notes.setPinned(it.id, random.nextBoolean()) }
                6 -> note?.let { tags.setTags(it.id, List(random.nextInt(4)) { "tag${random.nextInt(8)}" }) }
                7 -> note?.let { notes.trash(it.id) }
                8 -> trash.contents().notes.randomOrNull(random)?.let { trash.restoreNote(it.id) }
                9 -> if (random.nextInt(4) == 0) folders.create("folder ${random.nextInt(100)}")
                10 -> if (random.nextInt(8) == 0) folders.list().randomOrNull(random)?.let { folders.trash(it.id) }
                11 -> trash.contents().folders.randomOrNull(random)?.let {
                    if (random.nextBoolean()) trash.restoreFolder(it.folder.id) else trash.deleteFolder(it.folder.id)
                }
                12 -> reminders.create("r", "d", System.currentTimeMillis() + random.nextLong(-5000, 5000))
                13 -> {
                    // What the Tray Host does every second, plus the Instances' change polling.
                    reminders.takeDue()
                    db.dataVersion()
                    notes.list("title", folder = FolderFilter.NoFolder)
                }
            }
            ops++
        }
    } catch (e: Throwable) {
        println("ERROR after $ops ops: ${e::class.qualifiedName}: ${e.message}")
        e.printStackTrace(System.out)
        exitProcess(1)
    }
    db.close()
    println("OK $ops")
}
