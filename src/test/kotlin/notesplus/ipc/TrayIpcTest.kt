package notesplus.ipc

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrayIpcTest {
    private val dir = Files.createTempDirectory("notesplus-ipc")
    private val crashes = AtomicInteger()
    private val server = TrayServer(dir) { crashes.incrementAndGet() }

    @AfterTest
    fun cleanup() {
        server.close()
        dir.toFile().deleteRecursively()
    }

    private class FakeInstance(var unsaved: Boolean = false) : TrayClient.Handler {
        lateinit var client: TrayClient
        val exitRequested = CountDownLatch(1)
        override fun hasUnsaved() = unsaved
        override fun onExitRequested() {
            client.bye()
            exitRequested.countDown()
        }
    }

    private fun connect(handler: FakeInstance, spawn: () -> Unit = {}): TrayClient {
        val client = TrayClient(dir, handler, spawn)
        handler.client = client
        client.start()
        return client
    }

    private fun waitFor(timeoutMs: Long = 5000, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(20)
        }
    }

    @Test
    fun clientStartsTrayWhenNoneIsRunningThenConnects() {
        val spawned = AtomicBoolean(false)
        val instance = FakeInstance()
        // No server yet: the client must ask to spawn one.
        connect(instance) {
            spawned.set(true)
            server.start()
        }
        waitFor { spawned.get() && server.instanceCount == 1 }
        instance.client.bye()
    }

    @Test
    fun reportsUnsavedChangesFromAnyInstance() {
        server.start()
        val clean = FakeInstance(unsaved = false)
        val dirty = FakeInstance(unsaved = false)
        connect(clean)
        connect(dirty)
        waitFor { server.instanceCount == 2 }

        assertFalse(server.anyUnsaved())
        dirty.unsaved = true
        assertTrue(server.anyUnsaved())
        clean.client.bye()
        dirty.client.bye()
    }

    @Test
    fun normalExitIsNotACrashButADroppedConnectionIs() {
        server.start()
        val polite = FakeInstance()
        connect(polite)
        waitFor { server.instanceCount == 1 }
        polite.client.bye()
        waitFor { server.instanceCount == 0 }
        Thread.sleep(100)
        assertEquals(0, crashes.get())

        // A raw connection that says hello and then just vanishes.
        val (port, token) = TrayFiles.readPort(dir)!!
        java.net.Socket(java.net.InetAddress.getLoopbackAddress(), port).use { s ->
            s.getOutputStream().write("HELLO $token 999999\n".toByteArray())
            s.getOutputStream().flush()
            waitFor { server.instanceCount == 1 }
        }
        waitFor { crashes.get() == 1 }
    }

    @Test
    fun wrongTokenIsRejected() {
        server.start()
        val (port, _) = TrayFiles.readPort(dir)!!
        java.net.Socket(java.net.InetAddress.getLoopbackAddress(), port).use { s ->
            s.getOutputStream().write("HELLO not-the-token 1\n".toByteArray())
            s.getOutputStream().flush()
            // The server closes the connection without registering it.
            assertEquals(-1, s.getInputStream().read())
        }
        assertEquals(0, server.instanceCount)
        assertEquals(0, crashes.get())
    }

    @Test
    fun exitAllAsksEveryInstanceToExitWithoutCrashDialogs() {
        server.start()
        val a = FakeInstance()
        val b = FakeInstance()
        connect(a)
        connect(b)
        waitFor { server.instanceCount == 2 }

        server.exitAll()
        assertTrue(a.exitRequested.await(5, TimeUnit.SECONDS))
        assertTrue(b.exitRequested.await(5, TimeUnit.SECONDS))
        assertEquals(0, server.instanceCount)
        assertEquals(0, crashes.get())
    }

    @Test
    fun onlyOneTrayHostCanHoldTheLock() {
        val first = TrayFiles.tryLock(dir)!!
        // A second lock from this same JVM throws OverlappingFileLockException in Java, so check
        // from another process instead.
        val p = ProcessBuilder(
            java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", System.getProperty("java.class.path"),
            "notesplus.ipc.LockProbeKt", dir.toString(),
        ).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor(30, TimeUnit.SECONDS)
        assertEquals("locked-out", out)
        first.release()
        first.channel().close()
    }
}
