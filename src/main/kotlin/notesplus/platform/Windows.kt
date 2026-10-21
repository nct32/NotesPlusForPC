package notesplus.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/** Reads Windows personalization and language settings. Every call fails soft (returns null/empty). */
object Windows {
    private const val PERSONALIZE = "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
    private const val DWM = "Software\\Microsoft\\Windows\\DWM"
    private const val USER_PROFILE = "Control Panel\\International\\User Profile"

    private interface Kernel32Ui : Library {
        fun GetUserDefaultUILanguage(): Short
        fun LCIDToLocaleName(lcid: Int, name: CharArray, size: Int, flags: Int): Int
    }

    private val kernel32: Kernel32Ui? by lazy { runCatching { Native.load("kernel32", Kernel32Ui::class.java) }.getOrNull() }

    /** Windows "app mode": true = dark, false = light, null = unknown. */
    fun appsUseDarkMode(): Boolean? = runCatching {
        Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, PERSONALIZE, "AppsUseLightTheme") == 0
    }.getOrNull()

    /** The Windows accent color as 0xRRGGBB, or null. */
    fun accentColor(): Int? = runCatching {
        val abgr = Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, DWM, "AccentColor")
        val r = abgr and 0xFF
        val g = (abgr shr 8) and 0xFF
        val b = (abgr shr 16) and 0xFF
        (r shl 16) or (g shl 8) or b
    }.getOrNull()

    /** Languages installed for this user, e.g. ["th-TH", "en-US"]. */
    fun installedLanguages(): List<String> = runCatching {
        Advapi32Util.registryGetStringArray(WinReg.HKEY_CURRENT_USER, USER_PROFILE, "Languages").toList()
    }.getOrDefault(emptyList())

    /** The Windows display language, e.g. "th-TH", or null. */
    fun displayLanguage(): String? = runCatching {
        val k = kernel32 ?: return null
        val langId = k.GetUserDefaultUILanguage().toInt() and 0xFFFF
        val buf = CharArray(85)
        val len = k.LCIDToLocaleName(langId, buf, buf.size, 0)
        if (len > 1) String(buf, 0, len - 1) else null
    }.getOrNull()
}
