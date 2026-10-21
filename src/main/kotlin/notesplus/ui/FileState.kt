package notesplus.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notesplus.io.ExternalFileException
import notesplus.io.ExternalFiles
import notesplus.io.OpenedFile
import java.nio.file.Path

/** The External File open in this Instance (one at a time). Never autosaved. */
class FileState(private val scope: CoroutineScope, private val onOpened: () -> Unit) {
    var file by mutableStateOf<OpenedFile?>(null)
        private set
    var text by mutableStateOf("")
    private var savedText by mutableStateOf("")
    val dirty get() = file != null && text != savedText

    /** An error or notice to show in a dialog. */
    var message by mutableStateOf<String?>(null)
    /** A large file waiting for "open anyway?". */
    var pendingLarge by mutableStateOf<Pair<Path, Long>?>(null)
    /** Set when saving found the file changed by another program. */
    var changedOnDisk by mutableStateOf(false)

    fun requestOpen(path: Path) = scope.launch {
        when (val check = withContext(Dispatchers.IO) { ExternalFiles.check(path) }) {
            ExternalFiles.Check.Ok -> load(path)
            is ExternalFiles.Check.Large -> pendingLarge = path to check.bytes
            is ExternalFiles.Check.Refused -> message = Strings.fileError(check.reason)
        }
    }

    fun confirmLarge() {
        val (path, _) = pendingLarge ?: return
        pendingLarge = null
        scope.launch { load(path) }
    }

    private suspend fun load(path: Path) {
        try {
            val opened = withContext(Dispatchers.IO) { ExternalFiles.read(path) }
            file = opened
            text = opened.text
            savedText = opened.text
            onOpened()
        } catch (e: ExternalFileException) {
            message = Strings.fileError(e.reason)
        }
    }

    /** Saves back to disk; unless [force], first checks that no other program changed it. */
    fun save(force: Boolean = false) {
        val f = file ?: return
        if (f.readOnly) {
            message = Strings.fileError(ExternalFileException.Reason.READ_ONLY)
            return
        }
        val toSave = text
        scope.launch {
            try {
                val current = withContext(Dispatchers.IO) { ExternalFiles.stamp(f.path) }
                if (!force && current != f.stamp) {
                    changedOnDisk = true
                    return@launch
                }
                val stamp = withContext(Dispatchers.IO) { ExternalFiles.write(f.path, toSave, f.format) }
                file = f.copy(stamp = stamp)
                savedText = toSave
            } catch (e: ExternalFileException) {
                message = Strings.fileError(e.reason)
            }
        }
    }

    fun reload() {
        val f = file ?: return
        scope.launch { load(f.path) }
    }

    fun close() {
        file = null
        text = ""
        savedText = ""
    }
}
