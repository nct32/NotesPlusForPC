package notesplus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import notesplus.data.Resolution

/** The Backup section of the Settings page. */
@Composable
fun BackupSection(backup: BackupState) {
    Text(Strings.BACKUP_HINT, style = MaterialTheme.typography.bodySmall)
    BackupRow(Strings.NOTES, backup::exportNotes, backup::importNotes)
    BackupRow(Strings.REMINDERS, backup::exportReminders, backup::importReminders)
    BackupRow(Strings.SETTINGS, backup::exportSettings, backup::importSettings)
}

@Composable
private fun BackupRow(label: String, onExport: () -> Unit, onImport: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(120.dp))
        OutlinedButton(onClick = onExport) { Text(Strings.EXPORT) }
        OutlinedButton(onClick = onImport) { Text(Strings.IMPORT) }
    }
}

/** Result messages and the Merge / Skip / Overwrite conflict dialog; shown on any page. */
@Composable
fun BackupDialogs(backup: BackupState) {
    backup.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { backup.message = null },
            text = { Text(msg) },
            confirmButton = { Button(onClick = { backup.message = null }) { Text(Strings.OK) } },
        )
    }
    backup.conflict?.let { c ->
        // Keyed so "Apply to all" resets for each import but stays while stepping through one.
        key(c.id) {
            var applyToAll by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = backup::cancelImport,
                title = { Text(Strings.CONFLICT_TITLE) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Strings.conflictPrompt(c.label.ifBlank { Strings.UNTITLED }))
                        if (backup.conflictsLeft > 1) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = applyToAll, onCheckedChange = { applyToAll = it })
                                Text(Strings.applyToAll(backup.conflictsLeft))
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { backup.answerConflict(Resolution.MERGE, applyToAll) }) { Text(Strings.MERGE) }
                        OutlinedButton(onClick = { backup.answerConflict(Resolution.SKIP, applyToAll) }) { Text(Strings.SKIP_LEAVE) }
                        OutlinedButton(onClick = { backup.answerConflict(Resolution.OVERWRITE, applyToAll) }) { Text(Strings.OVERWRITE) }
                    }
                },
                dismissButton = { TextButton(onClick = backup::cancelImport) { Text(Strings.CANCEL_IMPORT) } },
            )
        }
    }
}
