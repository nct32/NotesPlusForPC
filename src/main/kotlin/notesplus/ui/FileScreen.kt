package notesplus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FileScreen(files: FileState, onSaveAsNote: (title: String, body: String) -> Unit, onClose: () -> Unit) {
    val file = files.file ?: return
    var askingTitle by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { files.save() }, enabled = files.dirty && !file.readOnly) { Text(Strings.SAVE_TO_FILE) }
            OutlinedButton(onClick = { askingTitle = true }) { Text(Strings.SAVE_AS_NOTE) }
            OutlinedButton(onClick = onClose) { Text(Strings.CLOSE_FILE) }
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    file.readOnly -> Strings.READ_ONLY
                    files.dirty -> Strings.UNSAVED
                    else -> Strings.SAVED
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Column {
            Text("📄 " + file.path.fileName, style = MaterialTheme.typography.titleMedium)
            Text(
                file.path.toAbsolutePath().toString() + "  ·  " + file.format.label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextField(
            value = files.text,
            onValueChange = { files.text = it },
            readOnly = file.readOnly,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (askingTitle) {
        var title by remember { mutableStateOf(file.path.fileName.toString().substringBeforeLast('.')) }
        AlertDialog(
            onDismissRequest = { askingTitle = false },
            title = { Text(Strings.SAVE_AS_NOTE) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.replace("\n", "") },
                    label = { Text(Strings.TITLE) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    askingTitle = false
                    onSaveAsNote(title.trim(), files.text)
                }, enabled = title.isNotBlank()) { Text(Strings.SAVE) }
            },
            dismissButton = { OutlinedButton(onClick = { askingTitle = false }) { Text(Strings.CANCEL) } },
        )
    }
}

/** Error/notice, large-file and changed-on-disk dialogs; shown on any page. */
@Composable
fun FileDialogs(files: FileState) {
    files.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { files.message = null },
            text = { Text(msg) },
            confirmButton = { Button(onClick = { files.message = null }) { Text(Strings.OK) } },
        )
    }
    files.pendingLarge?.let { (_, bytes) ->
        AlertDialog(
            onDismissRequest = { files.pendingLarge = null },
            text = { Text(Strings.largeFilePrompt(bytes)) },
            confirmButton = { Button(onClick = files::confirmLarge) { Text(Strings.OPEN_ANYWAY) } },
            dismissButton = { OutlinedButton(onClick = { files.pendingLarge = null }) { Text(Strings.CANCEL) } },
        )
    }
    if (files.changedOnDisk) {
        AlertDialog(
            onDismissRequest = { files.changedOnDisk = false },
            text = { Text(Strings.CHANGED_ON_DISK) },
            confirmButton = {
                Button(onClick = {
                    files.changedOnDisk = false
                    files.save(force = true)
                }) { Text(Strings.OVERWRITE) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        files.changedOnDisk = false
                        files.reload()
                    }) { Text(Strings.RELOAD) }
                    OutlinedButton(onClick = { files.changedOnDisk = false }) { Text(Strings.CANCEL) }
                }
            },
        )
    }
}
