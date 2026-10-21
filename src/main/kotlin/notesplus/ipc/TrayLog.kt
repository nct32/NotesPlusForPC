package notesplus.ipc

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime

/** Small append-only log at %APPDATA%\Notes+\tray.log, for investigating crashes. Capped at 1 MB. */
object TrayLog {
    private const val MAX_BYTES = 1_000_000L
    @Volatile var file: Path? = null

    @Synchronized
    fun log(message: String, error: Throwable? = null) {
        val f = file ?: return
        try {
            if (Files.exists(f) && Files.size(f) > MAX_BYTES) Files.delete(f)
            val line = "${LocalDateTime.now()} [pid ${ProcessHandle.current().pid()}] $message" +
                (error?.let { " — ${it::class.simpleName}: ${it.message}" } ?: "") + "\n"
            Files.writeString(f, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        } catch (e: Exception) {
            // Logging must never break the app.
        }
    }
}
