package notesplus.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import notesplus.data.FolderRepo
import notesplus.data.NoteRepo
import notesplus.data.SettingsRepo
import notesplus.data.TagRepo
import notesplus.domain.Folder
import notesplus.domain.FolderFilter
import notesplus.domain.Note
import notesplus.domain.SortOrder

class EditorState(note: Note, tags: List<String>) {
    val noteId = note.id
    var title by mutableStateOf(note.title)
    var body by mutableStateOf(note.body)
    var savedTitle by mutableStateOf(note.title)
    var savedBody by mutableStateOf(note.body)
    var pinned by mutableStateOf(note.pinned)
    var folderId by mutableStateOf(note.folderId)
    var tags by mutableStateOf(tags)
    val dirty get() = title != savedTitle || body != savedBody
}

enum class Page { NOTES, REMINDERS, TRASH, FILE, SETTINGS }

/** State of one Instance's Notes page. */
class AppState(
    private val notes: NoteRepo,
    private val folders: FolderRepo,
    private val tags: TagRepo,
    private val settings: SettingsRepo,
    private val scope: CoroutineScope,
) {
    var page by mutableStateOf(Page.NOTES)
        private set
    var query by mutableStateOf("")
        private set
    var sort by mutableStateOf(SortOrder.NEWEST)
        private set
    var folderFilter by mutableStateOf<FolderFilter>(FolderFilter.All)
        private set
    var list by mutableStateOf(emptyList<Note>())
        private set
    var folderList by mutableStateOf(emptyList<Folder>())
        private set
    var editor by mutableStateOf<EditorState?>(null)
        private set

    /** The saved Autosave setting. */
    private var autosaveSetting by mutableStateOf(true)
    /** Editor menu-bar toggle; applies to this Instance only and resets on restart. */
    private var autosaveOverride by mutableStateOf<Boolean?>(null)
    val autosaveOn get() = autosaveOverride ?: autosaveSetting

    /** Set when the unsaved-changes dialog should show; runs if the user discards. */
    var pendingDiscard by mutableStateOf<(() -> Unit)?>(null)
        private set

    fun load() = scope.launch {
        autosaveSetting = io { settings.autosave }
        sort = io { settings.sortOrder }
        refresh()
    }

    private suspend fun refresh() {
        val q = query
        val s = sort
        val f = folderFilter
        folderList = io { folders.list() }
        // A filter on a Folder that no longer exists falls back to all Notes.
        if (f is FolderFilter.In && folderList.none { it.id == f.folderId }) folderFilter = FolderFilter.All
        list = io { notes.list(q, s, folderFilter) }
    }

    /**
     * Another Instance changed the database: refresh the lists and the open Note.
     * Unsaved edits here are kept (last save wins, ADR 0002).
     */
    suspend fun syncFromDb() {
        refresh()
        val e = editor ?: return
        val note = io { notes.get(e.noteId) }
        if (e.dirty || editor !== e) return // Keep unsaved edits; or the user switched Notes meanwhile.
        // Deleted, trashed, or inside a Folder that was trashed (no longer among the live Folders).
        if (note == null || note.deletedAt != null || (note.folderId != null && folderList.none { it.id == note.folderId })) {
            editor = null
            return
        }
        if (note.title != e.savedTitle || note.body != e.savedBody) {
            e.title = note.title
            e.body = note.body
            e.savedTitle = note.title
            e.savedBody = note.body
        }
        e.pinned = note.pinned
        e.folderId = note.folderId
        e.tags = io { tags.tagsFor(e.noteId) }
    }

    fun goTo(target: Page) = whenSafe {
        // Leaving Notes closes the Note editor (already saved or discarded above).
        if (target != Page.NOTES) editor = null
        page = target
        scope.launch { refresh() }
    }

    fun updateQuery(q: String) {
        query = q
        scope.launch { refresh() }
    }

    fun updateSort(s: SortOrder) {
        sort = s
        scope.launch {
            io { settings.sortOrder = s }
            refresh()
        }
    }

    fun updateFolderFilter(f: FolderFilter) {
        folderFilter = f
        scope.launch { refresh() }
    }

    /** The saved Autosave setting changed (here or in another Instance). */
    fun applyAutosaveSetting(on: Boolean) {
        autosaveSetting = on
    }

    fun toggleAutosave() {
        autosaveOverride = !autosaveOn
    }

    fun saveNow(): Job? {
        val e = editor ?: return null
        if (!e.dirty) return null
        val title = e.title
        val body = e.body
        return scope.launch {
            io { notes.save(e.noteId, title, body) }
            e.savedTitle = title
            e.savedBody = body
            refresh()
        }
    }

    /**
     * Runs [action] once the current Note is safe to leave: saved if Autosave is on,
     * otherwise only after the user confirms discarding.
     */
    fun whenSafe(action: () -> Unit) {
        val e = editor
        when {
            e == null || !e.dirty -> action()
            autosaveOn -> scope.launch {
                saveNow()?.join()
                action()
            }
            else -> pendingDiscard = action
        }
    }

    /** Shows the unsaved-changes dialog for something other than the Note editor (an External File). */
    fun askDiscard(action: () -> Unit) {
        pendingDiscard = action
    }

    fun confirmDiscard() {
        val action = pendingDiscard ?: return
        pendingDiscard = null
        action()
    }

    fun cancelDiscard() {
        pendingDiscard = null
    }

    fun open(id: String) = whenSafe {
        scope.launch {
            val note = io { notes.get(id) } ?: return@launch
            editor = EditorState(note, io { tags.tagsFor(id) })
        }
    }

    fun newNote() = whenSafe {
        scope.launch {
            val inFolder = (folderFilter as? FolderFilter.In)?.folderId
            val note = io { notes.create(inFolder) }
            editor = EditorState(note, emptyList())
            refresh()
        }
    }

    /** "Save as Note" from an External File: a new Note with no Folder, opened in the editor. */
    fun createNoteFrom(title: String, body: String) = whenSafe {
        scope.launch {
            val note = io {
                val n = notes.create()
                notes.save(n.id, title, body)
                notes.get(n.id) ?: n
            }
            editor = EditorState(note, emptyList())
            page = Page.NOTES
            refresh()
        }
    }

    fun togglePin() {
        val e = editor ?: return
        val pinned = !e.pinned
        e.pinned = pinned
        scope.launch {
            io { notes.setPinned(e.noteId, pinned) }
            refresh()
        }
    }

    fun moveToFolder(folderId: String?) {
        val e = editor ?: return
        e.folderId = folderId
        scope.launch {
            io { notes.setFolder(e.noteId, folderId) }
            refresh()
        }
    }

    fun updateTags(names: List<String>) {
        val e = editor ?: return
        scope.launch {
            io { tags.setTags(e.noteId, names) }
            e.tags = io { tags.tagsFor(e.noteId) }
            refresh()
        }
    }

    fun trashCurrent() {
        val e = editor ?: return
        editor = null
        scope.launch {
            io { notes.trash(e.noteId) }
            refresh()
        }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        scope.launch {
            val folder = io { folders.create(name) }
            folderFilter = FolderFilter.In(folder.id)
            refresh()
        }
    }

    fun renameFolder(id: String, name: String) {
        if (name.isBlank()) return
        scope.launch {
            io { folders.rename(id, name) }
            refresh()
        }
    }

    suspend fun folderNoteCount(id: String): Int = io { folders.noteCount(id) }

    /** Sends a Folder and its Notes to Trash, closing the editor if its Note was inside. */
    fun trashFolder(id: String) = whenSafe {
        if (editor?.folderId == id) editor = null
        scope.launch {
            io { folders.trash(id) }
            refresh()
        }
    }
}
