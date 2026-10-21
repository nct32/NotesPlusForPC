package notesplus.io

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import notesplus.domain.Accent
import notesplus.domain.AppSettings
import notesplus.domain.Lang
import notesplus.domain.REMINDER_DESCRIPTION_MAX
import notesplus.domain.ThemeMode
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

const val SCHEMA_VERSION = 1

@Serializable
data class FolderDto(val id: String, val name: String)

@Serializable
data class NoteDto(
    val id: String,
    val title: String,
    val body: String,
    val folderId: String? = null,
    val pinned: Boolean = false,
    val createdAt: Long,
    val modifiedAt: Long,
    val tags: List<String> = emptyList(),
)

/** A .nptxt file. */
@Serializable
data class NotesBackup(
    val format: String = FORMAT,
    val schemaVersion: Int = SCHEMA_VERSION,
    val folders: List<FolderDto> = emptyList(),
    val notes: List<NoteDto> = emptyList(),
) {
    companion object {
        const val FORMAT = "notesplus-notes"
    }
}

@Serializable
data class ReminderDto(val id: String, val title: String, val description: String = "", val dueAt: Long)

/** A .nprmd file. */
@Serializable
data class RemindersBackup(
    val format: String = FORMAT,
    val schemaVersion: Int = SCHEMA_VERSION,
    val reminders: List<ReminderDto> = emptyList(),
) {
    companion object {
        const val FORMAT = "notesplus-reminders"
    }
}

/** A .npcfg file. A missing (null) setting keeps the current value on import. */
@Serializable
data class SettingsBackup(
    val format: String = FORMAT,
    val schemaVersion: Int = SCHEMA_VERSION,
    val theme: String? = null,
    val accent: String? = null,
    val language: String? = null,
    val autosave: Boolean? = null,
    val startWithWindows: Boolean? = null,
    val backgroundAutostart: Boolean? = null,
) {
    fun applyTo(current: AppSettings) = current.copy(
        theme = theme?.let { ThemeMode.valueOf(it) } ?: current.theme,
        accent = accent?.let { if (it == "auto") Accent.AUTO else Accent(it.toInt()) } ?: current.accent,
        languageOverride = if (language == null) current.languageOverride else Lang.fromTag(language),
        autosave = autosave ?: current.autosave,
        startWithWindows = startWithWindows ?: current.startWithWindows,
        backgroundAutostart = backgroundAutostart ?: current.backgroundAutostart,
    )

    companion object {
        const val FORMAT = "notesplus-settings"

        fun from(s: AppSettings) = SettingsBackup(
            theme = s.theme.name,
            accent = s.accent.paletteIndex?.toString() ?: "auto",
            language = s.languageOverride?.tag ?: "system",
            autosave = s.autosave,
            startWithWindows = s.startWithWindows,
            backgroundAutostart = s.backgroundAutostart,
        )
    }
}

class BackupException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { WRONG_TYPE, NEWER_VERSION, CORRUPT, INVALID, IO }
}

/** Reading and writing backup files. Reading validates everything before returning. */
object BackupFiles {
    const val NOTES_EXT = "nptxt"
    const val REMINDERS_EXT = "nprmd"
    const val SETTINGS_EXT = "npcfg"
    private const val MAX_BYTES = 200L * 1024 * 1024

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun writeNotes(path: Path, b: NotesBackup) = writeAtomic(path, json.encodeToString(NotesBackup.serializer(), b))
    fun writeReminders(path: Path, b: RemindersBackup) = writeAtomic(path, json.encodeToString(RemindersBackup.serializer(), b))
    fun writeSettings(path: Path, b: SettingsBackup) = writeAtomic(path, json.encodeToString(SettingsBackup.serializer(), b))

    fun readNotes(path: Path): NotesBackup {
        val b = decode(path, NotesBackup.FORMAT) { json.decodeFromString(NotesBackup.serializer(), it) }
        val folderIds = b.folders.map { it.id }
        check(folderIds.all { it.isNotBlank() } && folderIds.toSet().size == folderIds.size)
        check(b.folders.all { it.name.isNotBlank() })
        val noteIds = b.notes.map { it.id }
        check(noteIds.all { it.isNotBlank() } && noteIds.toSet().size == noteIds.size)
        check(b.notes.all { it.folderId == null || it.folderId in folderIds })
        return b
    }

    fun readReminders(path: Path): RemindersBackup {
        val b = decode(path, RemindersBackup.FORMAT) { json.decodeFromString(RemindersBackup.serializer(), it) }
        val ids = b.reminders.map { it.id }
        check(ids.all { it.isNotBlank() } && ids.toSet().size == ids.size)
        check(b.reminders.all { it.title.isNotBlank() && it.description.length <= REMINDER_DESCRIPTION_MAX })
        return b
    }

    fun readSettings(path: Path): SettingsBackup {
        val b = decode(path, SettingsBackup.FORMAT) { json.decodeFromString(SettingsBackup.serializer(), it) }
        check(b.theme == null || ThemeMode.entries.any { it.name == b.theme })
        check(b.accent == null || b.accent == "auto" || b.accent.toIntOrNull() in 0..15)
        check(b.language == null || b.language == "system" || Lang.fromTag(b.language) != null)
        return b
    }

    private fun check(ok: Boolean) {
        if (!ok) throw BackupException(BackupException.Reason.INVALID)
    }

    /** Checks the "format" and "schemaVersion" header before decoding the rest strictly. */
    private fun <T> decode(path: Path, format: String, decodeAll: (String) -> T): T {
        val text = try {
            if (Files.size(path) > MAX_BYTES) throw BackupException(BackupException.Reason.INVALID)
            Files.readString(path)
        } catch (e: IOException) {
            throw BackupException(BackupException.Reason.IO)
        }
        val header = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.CORRUPT)
        }
        val actualFormat = runCatching { header["format"]?.jsonPrimitive?.content }.getOrNull()
        if (actualFormat != format) throw BackupException(BackupException.Reason.WRONG_TYPE)
        val version = runCatching { header["schemaVersion"]?.jsonPrimitive?.int }.getOrNull()
            ?: throw BackupException(BackupException.Reason.CORRUPT)
        if (version > SCHEMA_VERSION) throw BackupException(BackupException.Reason.NEWER_VERSION)
        return try {
            decodeAll(text)
        } catch (e: SerializationException) {
            throw BackupException(BackupException.Reason.CORRUPT)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupException.Reason.CORRUPT)
        }
    }

    private fun writeAtomic(path: Path, text: String) {
        var tmp: Path? = null
        try {
            tmp = Files.createTempFile(path.toAbsolutePath().parent, ".notesplus-", ".tmp")
            Files.writeString(tmp, text)
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            tmp?.let { Files.deleteIfExists(it) }
            throw BackupException(BackupException.Reason.IO)
        }
    }
}
