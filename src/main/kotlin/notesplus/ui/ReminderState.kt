package notesplus.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import notesplus.data.ReminderRepo
import notesplus.domain.Reminder

class ReminderState(private val reminders: ReminderRepo, private val scope: CoroutineScope) {
    var query by mutableStateOf("")
        private set
    var list by mutableStateOf(emptyList<Reminder>())
        private set

    fun refresh() = scope.launch {
        val q = query
        list = io { reminders.list(q) }
    }

    fun updateQuery(q: String) {
        query = q
        refresh()
    }

    /** Creates when [id] is null, otherwise updates. */
    fun save(id: String?, title: String, description: String, dueAt: Long) = scope.launch {
        io {
            if (id == null) reminders.create(title, description, dueAt)
            else reminders.update(id, title, description, dueAt)
        }
        list = io { reminders.list(query) }
    }

    fun delete(id: String) = scope.launch {
        io { reminders.delete(id) }
        list = io { reminders.list(query) }
    }
}
