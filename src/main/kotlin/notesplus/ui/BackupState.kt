package notesplus.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notesplus.data.BackupRepo
import notesplus.data.Conflict
import notesplus.data.Resolution
import notesplus.io.BackupException
import notesplus.io.BackupFiles
import notesplus.io.SettingsBackup
import java.nio.file.Path

/**
 * Export/import of .nptxt, .nprmd and .npcfg files. Each import is read and checked in full,
 * conflicts are resolved one by one, and only then is anything written.
 */
class BackupState(
    private val repo: BackupRepo,
    private val settings: SettingsState,
    private val scope: CoroutineScope,
    private val pickOpen: (extension: String) -> Path?,
    private val pickSave: (fileName: String) -> Path?,
    private val onDataChanged: suspend () -> Unit,
) {
    var message by mutableStateOf<String?>(null)

    /** The conflict being asked about, if any. */
    var conflict by mutableStateOf<Conflict?>(null)
        private set
    var conflictsLeft by mutableStateOf(0)
        private set
    private var answer: CompletableDeferred<Pair<Resolution, Boolean>?>? = null

    fun answerConflict(resolution: Resolution, applyToAll: Boolean) {
        answer?.complete(resolution to applyToAll)
    }

    /** Closing the conflict dialog cancels the whole import; nothing is written. */
    fun cancelImport() {
        answer?.complete(null)
    }

    private suspend fun resolveAll(conflicts: List<Conflict>): Map<String, Resolution>? {
        val result = mutableMapOf<String, Resolution>()
        var all: Resolution? = null
        for ((i, c) in conflicts.withIndex()) {
            if (all != null) {
                result[c.id] = all
                continue
            }
            val d = CompletableDeferred<Pair<Resolution, Boolean>?>()
            answer = d
            conflictsLeft = conflicts.size - i
            conflict = c
            val picked = d.await()
            conflict = null
            val (resolution, applyToAll) = picked ?: return null
            result[c.id] = resolution
            if (applyToAll) all = resolution
        }
        return result
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: BackupException) {
            message = Strings.backupError(e.reason)
        }
    }

    fun exportNotes() = scope.launch {
        val path = pickSave("notes.${BackupFiles.NOTES_EXT}")?.withExtension(BackupFiles.NOTES_EXT) ?: return@launch
        guarded {
            val b = io { repo.exportNotes() }
            withContext(Dispatchers.IO) { BackupFiles.writeNotes(path, b) }
            message = Strings.exportedNotes(b.notes.size)
        }
    }

    fun importNotes() = scope.launch {
        val path = pickOpen(BackupFiles.NOTES_EXT) ?: return@launch
        guarded {
            val b = withContext(Dispatchers.IO) { BackupFiles.readNotes(path) }
            val resolutions = resolveAll(io { repo.noteConflicts(b) }) ?: return@guarded
            val r = io { repo.importNotes(b, { resolutions[it] ?: Resolution.SKIP }, Strings.IMPORTED_SUFFIX) }
            onDataChanged()
            message = Strings.importResult(r)
        }
    }

    fun exportReminders() = scope.launch {
        val path = pickSave("reminders.${BackupFiles.REMINDERS_EXT}")?.withExtension(BackupFiles.REMINDERS_EXT) ?: return@launch
        guarded {
            val b = io { repo.exportReminders() }
            withContext(Dispatchers.IO) { BackupFiles.writeReminders(path, b) }
            message = Strings.exportedReminders(b.reminders.size)
        }
    }

    fun importReminders() = scope.launch {
        val path = pickOpen(BackupFiles.REMINDERS_EXT) ?: return@launch
        guarded {
            val b = withContext(Dispatchers.IO) { BackupFiles.readReminders(path) }
            val resolutions = resolveAll(io { repo.reminderConflicts(b) }) ?: return@guarded
            val r = io { repo.importReminders(b, { resolutions[it] ?: Resolution.SKIP }, Strings.IMPORTED_SUFFIX) }
            onDataChanged()
            message = Strings.importResult(r)
        }
    }

    fun exportSettings() = scope.launch {
        val path = pickSave("settings.${BackupFiles.SETTINGS_EXT}")?.withExtension(BackupFiles.SETTINGS_EXT) ?: return@launch
        guarded {
            withContext(Dispatchers.IO) { BackupFiles.writeSettings(path, SettingsBackup.from(settings.settings)) }
            message = Strings.SETTINGS_EXPORTED
        }
    }

    /** Applies immediately; settings missing from the file keep their current values. */
    fun importSettings() = scope.launch {
        val path = pickOpen(BackupFiles.SETTINGS_EXT) ?: return@launch
        guarded {
            val b = withContext(Dispatchers.IO) { BackupFiles.readSettings(path) }
            settings.update { b.applyTo(it) }
            message = Strings.SETTINGS_IMPORTED
        }
    }

    private fun Path.withExtension(ext: String): Path =
        if (fileName.toString().endsWith(".$ext", ignoreCase = true)) this else resolveSibling("$fileName.$ext")
}
