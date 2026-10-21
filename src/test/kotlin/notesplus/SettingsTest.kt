package notesplus

import notesplus.data.Db
import notesplus.data.SettingsRepo
import notesplus.domain.Accent
import notesplus.domain.AppSettings
import notesplus.domain.Lang
import notesplus.domain.Languages
import notesplus.domain.ThemeMode
import notesplus.platform.Autostart
import notesplus.ui.ACCENT_PALETTE
import notesplus.ui.colorSchemeFor
import notesplus.ui.contrast
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsTest {
    private val dir = Files.createTempDirectory("notesplus-settings")
    private val db = Db.open(dir.resolve("test.db"))

    @AfterTest
    fun cleanup() {
        db.close()
        dir.toFile().deleteRecursively()
    }

    @Test
    fun defaultsAreSystemThemeAutoAccentSystemLanguageAndAutosaveOn() {
        assertEquals(AppSettings(), SettingsRepo(db).load())
        assertEquals(ThemeMode.SYSTEM, AppSettings().theme)
        assertEquals(Accent.AUTO, AppSettings().accent)
    }

    @Test
    fun settingsRoundTripAndAreSharedWithOtherConnections() {
        val s = AppSettings(ThemeMode.DARK, Accent(7), Lang.TH, autosave = false, startWithWindows = true, backgroundAutostart = true)
        SettingsRepo(db).save(s)
        Db.open(dir.resolve("test.db")).use { other -> assertEquals(s, SettingsRepo(other).load()) }
        SettingsRepo(db).save(s.copy(accent = Accent.AUTO, languageOverride = null))
        assertEquals(Accent.AUTO, SettingsRepo(db).load().accent)
    }

    @Test
    fun languagePickerOnlyListsShippedLanguagesInstalledInWindows() {
        assertEquals(listOf(Lang.EN, Lang.TH), Languages.available(listOf("th-TH", "en-US", "ja-JP")))
        assertEquals(listOf(Lang.EN), Languages.available(listOf("en-GB", "pt-BR")))
    }

    @Test
    fun languageResolution() {
        val both = listOf("th-TH", "en-US")
        assertEquals(Lang.TH, Languages.resolve(Lang.TH, "en-US", both), "override wins")
        assertEquals(Lang.TH, Languages.resolve(null, "th-TH", both), "System Default uses the display language")
        assertEquals(Lang.EN, Languages.resolve(null, "ja-JP", both), "unshipped display language falls back to English")
        assertEquals(Lang.EN, Languages.resolve(Lang.TH, "en-US", listOf("en-US")), "an uninstalled override is ignored")
        assertEquals(Lang.EN, Languages.resolve(null, null, emptyList()))
    }

    @Test
    fun autostartCommandLine() {
        assertEquals("\"C:\\Apps\\Notes+\\Notes+.exe\"", Autostart.commandLine("C:\\Apps\\Notes+\\Notes+.exe", background = false))
        assertEquals("\"C:\\Apps\\Notes+\\Notes+.exe\" --tray", Autostart.commandLine("C:\\Apps\\Notes+\\Notes+.exe", background = true))
    }

    @Test
    fun windowsSettingsCanBeRead() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        val dark = notesplus.platform.Windows.appsUseDarkMode()
        val accent = notesplus.platform.Windows.accentColor()
        val installed = notesplus.platform.Windows.installedLanguages()
        val display = notesplus.platform.Windows.displayLanguage()
        println("Windows: dark=$dark accent=${accent?.let { "#%06X".format(it) }} installed=$installed display=$display")
        assertTrue(installed.isNotEmpty(), "installed languages")
        assertTrue(display != null && display.contains('-'), "display language like en-US")
    }

    @Test
    fun everyAccentHasReadableTextInLightAndDark() {
        assertEquals(16, ACCENT_PALETTE.size)
        for (accent in ACCENT_PALETTE) {
            for (dark in listOf(false, true)) {
                val scheme = colorSchemeFor(accent, dark)
                assertTrue(contrast(scheme.primary, scheme.onPrimary) >= 4.5f, "$accent dark=$dark primary")
                assertTrue(contrast(scheme.secondaryContainer, scheme.onSecondaryContainer) >= 4.5f, "$accent dark=$dark container")
            }
        }
    }
}
