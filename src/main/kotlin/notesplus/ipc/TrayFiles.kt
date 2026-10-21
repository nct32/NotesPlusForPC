package notesplus.ipc

import notesplus.data.Db
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** Files in %APPDATA%\Notes+ that coordinate the Tray Host and the Instances. */
object TrayFiles {
    val defaultDir: Path get() = Db.defaultFile().toAbsolutePath().parent

    /** Held by the running Tray Host for its whole life; only one can hold it. */
    fun tryLock(dir: Path): FileLock? {
        Files.createDirectories(dir)
        val channel = FileChannel.open(dir.resolve("tray.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val lock = try {
            channel.tryLock()
        } catch (e: IOException) {
            null
        }
        if (lock == null) channel.close()
        return lock
    }

    fun writePort(dir: Path, port: Int, token: String) = writeAtomic(dir.resolve("tray.port"), "$port $token")

    fun readPort(dir: Path): Pair<Int, String>? = try {
        val parts = Files.readString(dir.resolve("tray.port")).trim().split(" ")
        if (parts.size == 2) parts[0].toIntOrNull()?.let { it to parts[1] } else null
    } catch (e: IOException) {
        null
    }

    fun deletePort(dir: Path) {
        Files.deleteIfExists(dir.resolve("tray.port"))
    }

    /** Heartbeat so a relaunched Tray Host knows whether it was down only briefly. */
    fun writeAlive(dir: Path, now: Long) = writeAtomic(dir.resolve("tray.alive"), now.toString())

    fun readAlive(dir: Path): Long? = try {
        Files.readString(dir.resolve("tray.alive")).trim().toLongOrNull()
    } catch (e: IOException) {
        null
    }

    private fun writeAtomic(target: Path, text: String) {
        val tmp = target.resolveSibling(target.fileName.toString() + ".tmp")
        Files.writeString(tmp, text)
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
