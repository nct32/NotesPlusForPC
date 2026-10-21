package notesplus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import notesplus.domain.REMINDER_DESCRIPTION_MAX
import notesplus.domain.Reminder
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dueFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

fun formatDue(dueAt: Long): String =
    Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault()).format(dueFormat)

/** Edit target: null = closed, [NEW] = creating, otherwise the Reminder being edited. */
private val NEW = Reminder("", "", "", 0)

@Composable
fun RemindersScreen(state: ReminderState) {
    LaunchedEffect(Unit) { state.refresh() }
    // Re-evaluate the red "overdue" look every 30 seconds.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    var editing by remember { mutableStateOf<Reminder?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Strings.REMINDERS, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            OutlinedTextField(
                value = state.query,
                onValueChange = state::updateQuery,
                label = { Text(Strings.SEARCH) },
                singleLine = true,
                modifier = Modifier.width(280.dp),
            )
            Button(onClick = { editing = NEW }) { Text(Strings.NEW_REMINDER) }
        }
        HorizontalDivider()
        if (state.list.isEmpty()) Text(Strings.NO_REMINDERS, Modifier.padding(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.list, key = { it.id }) { r ->
                ReminderCard(r, overdue = r.isOverdue(now), onClick = { editing = r }, onDelete = { state.delete(r.id) })
            }
        }
    }

    editing?.let { target ->
        ReminderDialog(
            initial = target.takeIf { it !== NEW },
            onDismiss = { editing = null },
            onSave = { title, description, dueAt ->
                editing = null
                state.save(target.id.takeIf { target !== NEW }, title, description, dueAt)
            },
        )
    }
}

@Composable
private fun ReminderCard(r: Reminder, overdue: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (overdue) colors.errorContainer else colors.surfaceVariant,
        contentColor = if (overdue) colors.onErrorContainer else colors.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(r.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r.description.isNotBlank()) {
                    Text(r.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "⏰ " + formatDue(r.dueAt) + if (overdue) "  ·  ${Strings.OVERDUE}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (overdue) colors.error else colors.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDelete) { Text(Strings.DELETE) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDialog(
    initial: Reminder?,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, dueAt: Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    // New Reminders default to the next full hour.
    val start = initial?.let { Instant.ofEpochMilli(it.dueAt).atZone(zone).toLocalDateTime() }
        ?: LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).plusHours(1)

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var time by remember { mutableStateOf(start.toLocalTime()) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    val dueAt = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
    val inPast = dueAt <= System.currentTimeMillis()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) Strings.NEW_REMINDER else Strings.EDIT_REMINDER) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.replace("\n", "") },
                    label = { Text(Strings.TITLE) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(REMINDER_DESCRIPTION_MAX) },
                    label = { Text(Strings.DESCRIPTION) },
                    supportingText = { Text("${description.length} / $REMINDER_DESCRIPTION_MAX") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickingDate = true }) {
                        Text("📅 " + date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
                    }
                    OutlinedButton(onClick = { pickingTime = true }) {
                        Text("🕒 " + time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
                    }
                }
                if (inPast) {
                    Text(Strings.TIME_IN_PAST, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title.trim(), description, dueAt) }, enabled = title.isNotBlank()) { Text(Strings.SAVE) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )

    if (pickingDate) {
        // The Material date picker works in UTC midnights.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    pickingDate = false
                }) { Text(Strings.OK) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(Strings.CANCEL) } },
        ) {
            DatePicker(pickerState)
        }
    }

    if (pickingTime) {
        val pickerState = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            text = { TimePicker(pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(pickerState.hour, pickerState.minute)
                    pickingTime = false
                }) { Text(Strings.OK) }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(Strings.CANCEL) } },
        )
    }
}
