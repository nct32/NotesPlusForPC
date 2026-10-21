package notesplus.domain

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** UI languages Notes+ ships translations for. */
enum class Lang(val tag: String) {
    EN("en"),
    TH("th");

    companion object {
        fun fromTag(tag: String?) = entries.find { it.tag == tag }
    }
}

/** [paletteIndex] is null for Auto (follow the Windows accent color). */
data class Accent(val paletteIndex: Int?) {
    companion object {
        val AUTO = Accent(null)
    }
}

/** null [languageOverride] means "System Default": use the Windows display language. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.AUTO,
    val languageOverride: Lang? = null,
    val autosave: Boolean = true,
    val startWithWindows: Boolean = false,
    val backgroundAutostart: Boolean = false,
)

/** Language rules: the picker lists shipped languages that are installed in Windows. */
object Languages {
    private fun primary(tag: String) = tag.substringBefore('-').lowercase()

    fun available(installedWindowsLanguages: List<String>): List<Lang> {
        val installed = installedWindowsLanguages.map(::primary).toSet()
        return Lang.entries.filter { it.tag in installed }
    }

    /** The language actually used: the override if still installed, else the display language, else English. */
    fun resolve(override: Lang?, windowsDisplayLanguage: String?, installedWindowsLanguages: List<String>): Lang {
        if (override != null && override in available(installedWindowsLanguages)) return override
        return Lang.fromTag(windowsDisplayLanguage?.let(::primary)) ?: Lang.EN
    }
}
