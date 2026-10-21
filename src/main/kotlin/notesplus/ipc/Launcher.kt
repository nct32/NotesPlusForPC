package notesplus.ipc

import java.nio.file.Files
import java.nio.file.Path

/** Starts another Notes+ process: a new Instance, or the Tray Host with `--tray`. */
object Launcher {

    fun spawn(vararg args: String) {
        ProcessBuilder(command(args.toList()))
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    }

    private fun command(args: List<String>): List<String> {
        // Set by the jpackage launcher in the installed app: just run Notes+.exe again.
        System.getProperty("jpackage.app-path")?.let { return listOf(it) + args }

        // Development (gradlew run): same JVM and classpath. The classpath goes in an
        // argument file because it can exceed the Windows command-line limit.
        val isWindows = System.getProperty("os.name").startsWith("Windows")
        val java = Path.of(System.getProperty("java.home"), "bin", if (isWindows) "javaw.exe" else "java").toString()
        val argFile = Files.createTempFile("notesplus", ".args")
        Files.writeString(argFile, "-cp \"" + System.getProperty("java.class.path").replace('\\', '/') + "\"")
        argFile.toFile().deleteOnExit()
        return listOf(java, "@$argFile", "notesplus.MainKt") + args
    }
}
