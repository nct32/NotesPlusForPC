package notesplus.platform

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/** "Start with Windows" through the per-user Run key. */
object Autostart {
    private const val RUN = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE = "Notes+"

    /** The installed Notes+.exe, or null when not running from the installed app (e.g. gradlew run). */
    val appPath: String? get() = System.getProperty("jpackage.app-path")

    /** What Windows runs at login; Background Autostart starts only the Tray Host. */
    fun commandLine(exe: String, background: Boolean) = "\"$exe\"" + if (background) " --tray" else ""

    /** Writes or removes the Run entry. Returns false when not possible (not installed, or registry error). */
    fun apply(startWithWindows: Boolean, background: Boolean): Boolean {
        val exe = appPath ?: return false
        return runCatching {
            if (startWithWindows) {
                Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN, VALUE, commandLine(exe, background))
            } else if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN, VALUE)) {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN, VALUE)
            }
            true
        }.getOrDefault(false)
    }
}
