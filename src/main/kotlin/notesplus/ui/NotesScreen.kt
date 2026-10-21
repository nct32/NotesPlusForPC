package notesplus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import notesplus.domain.Folder
import notesplus.domain.FolderFilter
import notesplus.domain.Note
import notesplus.domain.SortOrder

@Composable
fun NotesScreen(state: AppState) {
    Row(Modifier.fillMaxSize()) {
        NoteList(state, Modifier.width(300.dp).fillMaxHeight())
        VerticalDivider()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val editor = state.editor
            if (editor == null) {
                Text(Strings.NO_NOTE_SELECTED, Modifier.align(Alignment.Center))
            } else {
                // Keyed so switching Notes resets the tab and the autosave timer.
                key(editor.noteId) { Editor(state, editor) }
            }
        }
    }
}

@Composable
private fun NoteList(state: AppState, modifier: Modifier) {
    Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FolderBar(state)
        OutlinedTextField(
            value = state.query,
            onValueChange = state::updateQuery,
            label = { Text(Strings.SEARCH) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = state::newNote) { Text(Strings.NEW_NOTE) }
            Spacer(Modifier.weight(1f))
            SortMenu(state.sort, state::updateSort)
        }
        HorizontalDivider()
        if (state.list.isEmpty()) {
            Text(Strings.NO_NOTES, Modifier.padding(8.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.list, key = { it.id }) { note ->
                NoteRow(note, selected = note.id == state.editor?.noteId) { state.open(note.id) }
            }
        }
    }
}

/** Folder filter plus New / Rename / Delete folder. */
@Composable
private fun FolderBar(state: AppState) {
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var actionsOpen by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Folder?>(null) }
    var deleting by remember { mutableStateOf<Pair<Folder, Int>?>(null) }

    val filter = state.folderFilter
    val current = (filter as? FolderFilter.In)?.let { f -> state.folderList.find { it.id == f.folderId } }
    val label = when (filter) {
        FolderFilter.All -> Strings.ALL_NOTES
        FolderFilter.NoFolder -> Strings.NO_FOLDER
        is FolderFilter.In -> "📁 " + (current?.name ?: "")
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            TextButton(onClick = { menuOpen = true }) {
                Text("$label ▾", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(Strings.ALL_NOTES) }, onClick = {
                    menuOpen = false
                    state.updateFolderFilter(FolderFilter.All)
                })
                DropdownMenuItem(text = { Text(Strings.NO_FOLDER) }, onClick = {
                    menuOpen = false
                    state.updateFolderFilter(FolderFilter.NoFolder)
                })
                state.folderList.forEach { f ->
                    DropdownMenuItem(text = { Text("📁 " + f.name) }, onClick = {
                        menuOpen = false
                        state.updateFolderFilter(FolderFilter.In(f.id))
                    })
                }
            }
        }
        Box {
            TextButton(onClick = { actionsOpen = true }) { Text("⋯") }
            DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                DropdownMenuItem(text = { Text(Strings.NEW_FOLDER) }, onClick = {
                    actionsOpen = false
                    creating = true
                })
                if (current != null) {
                    DropdownMenuItem(text = { Text(Strings.RENAME_FOLDER) }, onClick = {
                        actionsOpen = false
                        renaming = current
                    })
                    DropdownMenuItem(text = { Text(Strings.DELETE_FOLDER) }, onClick = {
                        actionsOpen = false
                        scope.launch { deleting = current to state.folderNoteCount(current.id) }
                    })
                }
            }
        }
    }

    if (creating) {
        FolderNameDialog(Strings.NEW_FOLDER, "", onDismiss = { creating = false }) {
            creating = false
            state.createFolder(it)
        }
    }
    renaming?.let { f ->
        FolderNameDialog(Strings.RENAME_FOLDER, f.name, onDismiss = { renaming = null }) {
            renaming = null
            state.renameFolder(f.id, it)
        }
    }
    deleting?.let { (f, count) ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(Strings.DELETE_FOLDER) },
            text = { Text(Strings.deleteFolderPrompt(f.name, count)) },
            confirmButton = {
                Button(onClick = {
                    deleting = null
                    state.trashFolder(f.id)
                }) { Text(Strings.MOVE_TO_TRASH) }
            },
            dismissButton = { OutlinedButton(onClick = { deleting = null }) { Text(Strings.CANCEL) } },
        )
    }
}

@Composable
private fun FolderNameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(Strings.FOLDER_NAME) },
                singleLine = true,
            )
        },
        confirmButton = { Button(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(Strings.OK) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}

@Composable
private fun NoteRow(note: Note, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Text(
                (if (note.pinned) "📌 " else "") + note.title.ifBlank { Strings.UNTITLED },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.body.isNotBlank()) {
                Text(
                    note.body.lineSequence().first { it.isNotBlank() },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SortMenu(current: SortOrder, onSelect: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("${Strings.SORT}: ${current.label()}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortOrder.entries.forEach { s ->
                DropdownMenuItem(text = { Text(s.label()) }, onClick = {
                    open = false
                    onSelect(s)
                })
            }
        }
    }
}

private fun SortOrder.label() = when (this) {
    SortOrder.NEWEST -> Strings.SORT_NEWEST
    SortOrder.OLDEST -> Strings.SORT_OLDEST
    SortOrder.TITLE_AZ -> Strings.SORT_TITLE
    SortOrder.SIZE -> Strings.SORT_SIZE
}

@Composable
private fun Editor(state: AppState, editor: EditorState) {
    var tab by remember { mutableStateOf(0) }

    // Autosave: save one second after the last change.
    LaunchedEffect(editor.title, editor.body, state.autosaveOn) {
        if (state.autosaveOn && editor.dirty) {
            delay(1000)
            state.saveNow()
        }
    }

    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Editor menu bar
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { state.saveNow() }, enabled = editor.dirty) { Text(Strings.SAVE) }
            OutlinedButton(onClick = state::toggleAutosave) {
                Text(if (state.autosaveOn) Strings.AUTOSAVE_ON else Strings.AUTOSAVE_OFF)
            }
            OutlinedButton(onClick = state::togglePin) { Text(if (editor.pinned) Strings.UNPIN else Strings.PIN) }
            FolderPicker(state.folderList, editor.folderId, state::moveToFolder)
            OutlinedButton(onClick = state::trashCurrent) { Text(Strings.MOVE_TO_TRASH) }
            Spacer(Modifier.weight(1f))
            Text(
                if (editor.dirty) Strings.UNSAVED else Strings.SAVED,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        TextField(
            value = editor.title,
            onValueChange = { editor.title = it },
            placeholder = { Text(Strings.TITLE) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        TagEditor(editor.tags, state::updateTags)
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(Strings.EDIT) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(Strings.PREVIEW) })
        }
        if (tab == 0) {
            TextField(
                value = editor.body,
                onValueChange = { editor.body = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
                Markdown(editor.body)
            }
        }
    }
}

@Composable
private fun FolderPicker(folders: List<Folder>, selectedId: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = folders.find { it.id == selectedId }?.name ?: Strings.NO_FOLDER
    Box {
        OutlinedButton(onClick = { open = true }) { Text("📁 $name ▾", maxLines = 1) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(Strings.NO_FOLDER) }, onClick = {
                open = false
                onSelect(null)
            })
            folders.forEach { f ->
                DropdownMenuItem(text = { Text(f.name) }, onClick = {
                    open = false
                    onSelect(f.id)
                })
            }
        }
    }
}

/** Tags as chips (click to remove) plus a field that adds a Tag on Enter. */
@Composable
private fun TagEditor(tags: List<String>, onChange: (List<String>) -> Unit) {
    var input by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(Strings.TAGS + ":", style = MaterialTheme.typography.labelLarge)
        tags.forEach { tag ->
            AssistChip(onClick = { onChange(tags - tag) }, label = { Text("#$tag  ×") })
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.replace("\n", "") },
            placeholder = { Text(Strings.ADD_TAG) },
            singleLine = true,
            modifier = Modifier.width(200.dp).onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Enter) {
                    if (input.isNotBlank()) onChange(tags + input.trim())
                    input = ""
                    true
                } else {
                    false
                }
            },
        )
    }
}
