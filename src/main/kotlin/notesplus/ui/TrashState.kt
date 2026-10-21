package notesplus.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import notesplus.data.TrashRepo
import notesplus.domain.TrashContents

class TrashState(private val trash: TrashRepo, private val scope: CoroutineScope) {
    var contents by mutableStateOf(TrashContents(emptyList(), emptyList()))
        private set

    fun refresh() = scope.launch { contents = io { trash.contents() } }

    private fun change(block: TrashRepo.() -> Unit) = scope.launch {
        io { trash.block() }
        contents = io { trash.contents() }
    }

    fun restoreNote(id: String) = change { restoreNote(id) }
    fun restoreFolder(id: String) = change { restoreFolder(id) }
    fun deleteNote(id: String) = change { deleteNote(id) }
    fun deleteFolder(id: String) = change { deleteFolder(id) }
    fun empty() = change { empty() }
}
