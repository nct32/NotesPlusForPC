package notesplus.data

import notesplus.domain.Accent
import notesplus.domain.AppSettings
import notesplus.domain.Lang
import notesplus.domain.SortOrder
import notesplus.domain.ThemeMode

class SettingsRepo(private val db: Db) {

    fun get(key: String): String? = db.read { c ->
        c.prepareStatement("SELECT value FROM setting WHERE key = ?").use {
            it.setString(1, key)
            it.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    fun set(key: String, value: String) = db.tx { c ->
        c.prepareStatement("INSERT INTO setting (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value").use {
            it.setString(1, key)
            it.setString(2, value)
            it.executeUpdate()
        }
    }

    var autosave: Boolean
        get() = get(AUTOSAVE)?.toBooleanStrictOrNull() ?: true
        set(v) { set(AUTOSAVE, v.toString()) }

    var sortOrder: SortOrder
        get() = SortOrder.entries.find { it.name == get(SORT) } ?: SortOrder.NEWEST
        set(v) { set(SORT, v.name) }

    fun load() = AppSettings(
        theme = ThemeMode.entries.find { it.name == get(THEME) } ?: ThemeMode.SYSTEM,
        accent = get(ACCENT)?.toIntOrNull()?.takeIf { it in 0..15 }?.let { Accent(it) } ?: Accent.AUTO,
        languageOverride = Lang.fromTag(get(LANGUAGE)),
        autosave = autosave,
        startWithWindows = get(START_WITH_WINDOWS)?.toBooleanStrictOrNull() ?: false,
        backgroundAutostart = get(BACKGROUND_AUTOSTART)?.toBooleanStrictOrNull() ?: false,
    )

    fun save(s: AppSettings) = db.tx { c ->
        val values = mapOf(
            THEME to s.theme.name,
            ACCENT to (s.accent.paletteIndex?.toString() ?: "auto"),
            LANGUAGE to (s.languageOverride?.tag ?: "system"),
            AUTOSAVE to s.autosave.toString(),
            START_WITH_WINDOWS to s.startWithWindows.toString(),
            BACKGROUND_AUTOSTART to s.backgroundAutostart.toString(),
        )
        c.prepareStatement("INSERT INTO setting (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value").use { st ->
            values.forEach { (k, v) ->
                st.setString(1, k)
                st.setString(2, v)
                st.executeUpdate()
            }
        }
    }

    private companion object {
        const val AUTOSAVE = "autosave"
        const val SORT = "sort"
        const val THEME = "theme"
        const val ACCENT = "accent"
        const val LANGUAGE = "language"
        const val START_WITH_WINDOWS = "start_with_windows"
        const val BACKGROUND_AUTOSTART = "background_autostart"
    }
}
