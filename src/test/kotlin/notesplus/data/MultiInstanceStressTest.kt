package notesplus.data

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Several Instances share one SQLite file (ADR 0002). Three real processes write at once;
 * none may fail, and the database must stay intact.
 * Duration: -Pstress.seconds=N (default 15).
 */
class MultiInstanceStressTest {
    private val dir = Files.createTempDirectory("notesplus-stress")

    @AfterTest
    fun cleanup() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun dataVersionChangesOnlyForOtherConnectionsCommits() {
        val file = dir.resolve("dv.db")
        Db.open(file).use { a ->
            Db.open(file).use { b ->
                val start = a.dataVersion()
                NoteRepo(a).create()
                assertEquals(start, a.dataVersion(), "own commits don't count")
                NoteRepo(b).create()
                assertNotEquals(start, a.dataVersion(), "another connection's commit does")
            }
        }
    }

    @Test
    fun threeProcessesWritingAtOnceStayConsistent() {
        val seconds = System.getProperty("stress.seconds")?.toLongOrNull() ?: 15
        val file = dir.resolve("stress.db")
        Db.open(file).close() // Create the schema first, like the first Instance would.

        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val argFile = dir.resolve("cp.args")
        Files.writeString(argFile, "-cp \"" + System.getProperty("java.class.path").replace('\\', '/') + "\"")
        val procs = (1..3).map { seed ->
            ProcessBuilder(java, "@$argFile", "notesplus.data.StressWriterKt", file.toString(), seconds.toString(), seed.toString())
                .redirectErrorStream(true)
                .start()
        }
        val outputs = procs.map { p ->
            val out = p.inputStream.bufferedReader().readText()
            assertTrue(p.waitFor(seconds + 120, TimeUnit.SECONDS), "process hung")
            p.exitValue() to out.trim()
        }
        outputs.forEachIndexed { i, (code, out) ->
            println("writer ${i + 1}: $out".lines().first())
            assertEquals(0, code, "writer ${i + 1} failed:\n$out")
        }

        Db.open(file).use { db ->
            db.read { c ->
                c.createStatement().use { st ->
                    st.executeQuery("PRAGMA integrity_check").use { rs -> assertEquals("ok", rs.getString(1)) }
                    st.executeQuery("PRAGMA foreign_key_check").use { rs -> assertTrue(!rs.next(), "foreign key violations") }
                }
            }
        }
    }
}
