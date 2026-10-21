package notesplus.ipc

import java.io.BufferedWriter
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** The Tray Host's side: tracks connected Instances, asks about unsaved changes, and shuts them down. */
class TrayServer(private val dir: Path, private val onInstanceCrashed: () -> Unit) : AutoCloseable {

    private class Conn(val socket: Socket, val pid: Long, private val writer: BufferedWriter) {
        @Volatile var saidBye = false
        @Volatile var pending: CompletableFuture<Boolean>? = null

        @Synchronized
        fun send(line: String) {
            try {
                writer.write(line)
                writer.write("\n")
                writer.flush()
            } catch (e: IOException) {
                // The reader side notices the broken connection.
            }
        }

        @Synchronized
        fun ask(): CompletableFuture<Boolean> {
            val f = CompletableFuture<Boolean>()
            pending = f
            send(Protocol.QUERY_UNSAVED)
            return f
        }
    }

    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val token = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val conns = CopyOnWriteArrayList<Conn>()
    @Volatile private var exiting = false

    val instanceCount: Int get() = conns.size

    fun start() {
        TrayFiles.writePort(dir, server.localPort, token)
        thread(isDaemon = true, name = "tray-accept") {
            while (!server.isClosed) {
                val s = try {
                    server.accept()
                } catch (e: IOException) {
                    return@thread
                }
                thread(isDaemon = true, name = "tray-conn") { handle(s) }
            }
        }
    }

    private fun handle(s: Socket) = s.use {
        val reader = s.getInputStream().bufferedReader(Charsets.UTF_8)
        // An unauthenticated peer gets 5 seconds to say hello.
        s.soTimeout = 5000
        val hello = try {
            reader.readLine()
        } catch (e: IOException) {
            null
        } ?: return
        val parts = hello.split(" ")
        if (parts.size != 3 || parts[0] != Protocol.HELLO) return
        if (!MessageDigest.isEqual(parts[1].toByteArray(), token.toByteArray())) return
        val pid = parts[2].toLongOrNull() ?: return
        s.soTimeout = 0

        val conn = Conn(s, pid, s.getOutputStream().bufferedWriter(Charsets.UTF_8))
        conns += conn
        TrayLog.log("Instance $pid connected")
        try {
            while (true) {
                val line = reader.readLine() ?: break
                when {
                    line == Protocol.BYE -> conn.saidBye = true
                    line.startsWith(Protocol.UNSAVED + " ") -> conn.pending?.complete(line.endsWith(" 1"))
                }
            }
        } catch (e: IOException) {
            // Treated like a closed connection below.
        } finally {
            conns -= conn
            conn.pending?.complete(false)
            when {
                conn.saidBye -> TrayLog.log("Instance $pid exited normally")
                exiting -> TrayLog.log("Instance $pid closed during Exit")
                else -> {
                    TrayLog.log("Instance $pid disconnected without saying goodbye: crashed")
                    onInstanceCrashed()
                }
            }
        }
    }

    /** True if any connected Instance reports unsaved changes. Silent Instances count as clean. */
    fun anyUnsaved(timeoutMs: Long = 2000): Boolean {
        val answers = conns.map { it.ask() }
        return answers.any { f ->
            try {
                f.get(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                false
            }
        }
    }

    /** Tells every Instance to exit, then force-kills any still running after [timeoutMs]. */
    fun exitAll(timeoutMs: Long = 5000) {
        exiting = true
        conns.forEach { it.send(Protocol.EXIT) }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (conns.isNotEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        val self = ProcessHandle.current().pid()
        conns.forEach { c ->
            if (c.pid != self) ProcessHandle.of(c.pid).ifPresent { it.destroyForcibly() }
            try {
                c.socket.close()
            } catch (e: IOException) {
                // Already gone.
            }
        }
    }

    override fun close() {
        server.close()
        TrayFiles.deletePort(dir)
    }
}
