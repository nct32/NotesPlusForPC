package notesplus.ipc

import java.nio.file.Path

/** Run in a separate process by TrayIpcTest: prints whether the tray lock is free. */
fun main(args: Array<String>) {
    val lock = TrayFiles.tryLock(Path.of(args[0]))
    println(if (lock == null) "locked-out" else "acquired")
}
