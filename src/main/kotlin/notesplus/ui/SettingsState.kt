package notesplus.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notesplus.data.SettingsRepo
import notesplus.domain.AppSettings
import notesplus.domain.Lang
import notesplus.domain.Languages
import notesplus.domain.ThemeMode
import notesplus.platform.Autostart
import notesplus.platform.Windows

/** Saved settings plus the Windows settings they can follow. Shared live across Instances via the database. */
class SettingsState(private val repo: SettingsRepo, private val scope: CoroutineScope) {
    var settings by mutableStateOf(AppSettings())
        private set

    // What Windows currently reports; re-read every 2 seconds so changes apply live.
    private var windowsDark by mutableStateOf<Boolean?>(null)
    private var windowsAccent by mutableStateOf<Color?>(null)
    var installedLanguages by mutableStateOf(emptyList<String>())
        private set
    private var displayLanguage by mutableStateOf<String?>(null)

    val autostartAvailable = Autostart.appPath != null

    val dark get() = when (settings.theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> windowsDark ?: false
    }

    val accentColor get() = settings.accent.paletteIndex?.let { ACCENT_PALETTE[it] } ?: windowsAccent ?: DEFAULT_ACCENT

    val language: Lang get() = Languages.resolve(settings.languageOverride, displayLanguage, installedLanguages)

    val languageChoices: List<Lang> get() = Languages.available(installedLanguages)

    suspend fun reload() {
        settings = io { repo.load() }
    }

    /** Runs in the composition's scope; only the registry reads move to the IO pool. */
    suspend fun followWindows() {
        while (true) {
            windowsDark = withContext(Dispatchers.IO) { Windows.appsUseDarkMode() }
            windowsAccent = withContext(Dispatchers.IO) { Windows.accentColor() }?.let { Color(0xFF000000 or it.toLong()) }
            installedLanguages = withContext(Dispatchers.IO) { Windows.installedLanguages() }
            displayLanguage = withContext(Dispatchers.IO) { Windows.displayLanguage() }
            delay(2000)
        }
    }

    fun update(change: (AppSettings) -> AppSettings) {
        val before = settings
        val after = change(before)
        settings = after
        scope.launch {
            io { repo.save(after) }
            if (after.startWithWindows != before.startWithWindows || after.backgroundAutostart != before.backgroundAutostart) {
                withContext(Dispatchers.IO) { Autostart.apply(after.startWithWindows, after.backgroundAutostart) }
            }
        }
    }
}
