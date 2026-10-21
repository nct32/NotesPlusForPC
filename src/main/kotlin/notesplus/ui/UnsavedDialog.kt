package notesplus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState

@Composable
fun UnsavedDialog(onNo: () -> Unit, onDiscard: () -> Unit) {
    DialogWindow(
        onCloseRequest = onNo,
        title = Strings.TRAY_TITLE,
        state = rememberDialogState(width = 420.dp, height = 170.dp),
        resizable = false,
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(Strings.UNSAVED_PROMPT)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                    OutlinedButton(onClick = onDiscard) { Text(Strings.DISCARD_EXIT) }
                    Button(onClick = onNo) { Text(Strings.NO) }
                }
            }
        }
    }
}
