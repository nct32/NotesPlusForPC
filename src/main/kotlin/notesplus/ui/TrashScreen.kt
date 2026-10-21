package notesplus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import notesplus.domain.DAY_MS
import notesplus.domain.Note
import notesplus.domain.TRASH_DAYS

@Composable
fun TrashScreen(state: TrashState) {
    LaunchedEffect(Unit) { state.refresh() }
    var confirmEmpty by remember { mutableStateOf(false) }
    val contents = state.contents
    val isEmpty = contents.folders.isEmpty() && contents.notes.isEmpty()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Strings.TRASH, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            Button(onClick = { confirmEmpty = true }, enabled = !isEmpty) { Text(Strings.EMPTY_TRASH) }
        }
        Text(Strings.TRASH_HINT, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        if (isEmpty) Text(Strings.TRASH_IS_EMPTY, Modifier.padding(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(contents.folders, key = { "f" + it.folder.id }) { tf ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("📁 " + tf.folder.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${Strings.notesCount(tf.notes.size)} · ${Strings.daysLeft(daysLeft(tf.folder.deletedAt))}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(onClick = { state.restoreFolder(tf.folder.id) }) { Text(Strings.RESTORE) }
                            TextButton(onClick = { state.deleteFolder(tf.folder.id) }) { Text(Strings.DELETE_PERMANENTLY) }
                        }
                        tf.notes.forEach { note ->
                            TrashNoteRow(note, Modifier.padding(start = 24.dp), state)
                        }
                    }
                }
            }
            items(contents.notes, key = { "n" + it.id }) { note ->
                TrashNoteRow(note, Modifier, state)
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(Strings.EMPTY_TRASH) },
            text = { Text(Strings.EMPTY_TRASH_PROMPT) },
            confirmButton = {
                Button(onClick = {
                    confirmEmpty = false
                    state.empty()
                }) { Text(Strings.EMPTY_TRASH) }
            },
            dismissButton = { OutlinedButton(onClick = { confirmEmpty = false }) { Text(Strings.CANCEL) } },
        )
    }
}

@Composable
private fun TrashNoteRow(note: Note, modifier: Modifier, state: TrashState) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(note.title.ifBlank { Strings.UNTITLED }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            note.deletedAt?.let {
                Text(Strings.daysLeft(daysLeft(it)), style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(onClick = { state.restoreNote(note.id) }) { Text(Strings.RESTORE) }
        TextButton(onClick = { state.deleteNote(note.id) }) { Text(Strings.DELETE_PERMANENTLY) }
    }
}

private fun daysLeft(deletedAt: Long?): Int {
    if (deletedAt == null) return TRASH_DAYS
    val elapsed = (System.currentTimeMillis() - deletedAt) / DAY_MS
    return (TRASH_DAYS - elapsed).toInt().coerceAtLeast(0)
}
