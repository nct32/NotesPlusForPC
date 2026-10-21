package notesplus.ipc

import java.io.BufferedWriter
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Path
import kotlin.concurrent.thread

/**
 * An Instance's link to the Tray Host. Keeps one connection open and, if it can't connect,
 * starts a Tray Host (at most once every [spawnBackoffMs]).
 */
class TrayClient(
    private val dir: Path,
    private val handler: Handler,
    private val spawnTray: () -> Unit,
    private val spawnBackoffMs: Long = 10_000,
) {
    interface Handler {
        /** Called from a background thread. */
        fun hasUnsaved(): Boolean

        /** The Tray Host's Exit. Called from a background thread; must end by calling [bye] and exiting. */
        fun onExitRequested()
    }

    @Volatile private var closed = false
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var lastSpawn = 0L

    @Volatile var connected = false
        private set

    fun start() {
        thread(isDaemon = true, name = "tray-client") {
            while (!closed) {
                val s = connect()
                if (s == null) {
                    maybeSpawn()
                    Thread.sleep(500)
                    continue
                }
                connected = true
                try {
                    serve(s)
                } catch (e: IOException) {
                    // Tray Host went away; reconnect (and relaunch it) on the next turn.
                } finally {
                    connected = false
                    closeSocket()
                }
            }
        }
    }

    private fun connect(): Socket? {
        val (port, token) = TrayFiles.readPort(dir) ?: return null
        return try {
            val s = Socket()
            s.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1000)
            synchronized(this) {
                socket = s
                writer = s.getOutputStream().bufferedWriter(Charsets.UTF_8)
            }
            send("${Protocol.HELLO} $token ${ProcessHandle.current().pid()}")
            s
        } catch (e: IOException) {
            null
        }
    }

    private fun serve(s: Socket) {
        val reader = s.getInputStream().bufferedReader(Charsets.UTF_8)
        while (!closed) {
            when (reader.readLine() ?: return) {
                Protocol.QUERY_UNSAVED -> send("${Protocol.UNSAVED} ${if (handler.hasUnsaved()) 1 else 0}")
                Protocol.EXIT -> handler.onExitRequested()
            }
        }
    }

    private fun maybeSpawn() {
        val now = System.currentTimeMillis()
        if (now - lastSpawn < spawnBackoffMs) return
        lastSpawn = now
        try {
            spawnTray()
        } catch (e: Exception) {
            // Try again after the backoff.
        }
    }

    @Synchronized
    private fun send(line: String) {
        try {
            writer?.apply {
                write(line)
                write("\n")
                flush()
            }
        } catch (e: IOException) {
            // The reader loop notices the broken connection.
        }
    }

    @Synchronized
    private fun closeSocket() {
        try {
            socket?.close()
        } catch (e: IOException) {
            // Already closed.
        }
        socket = null
        writer = null
    }

    /** Normal exit: tells the Tray Host this was not a crash, then disconnects for good. */
    fun bye() {
        closed = true
        send(Protocol.BYE)
        closeSocket()
    }
}
