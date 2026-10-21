package notesplus.domain

const val REMINDER_DESCRIPTION_MAX = 299

/** [notified] is true once the Tray Host has handled the Reminder (fired, or found it already past). */
data class Reminder(
    val id: String,
    val title: String,
    val description: String,
    val dueAt: Long,
    val notified: Boolean = false,
) {
    fun isOverdue(now: Long) = dueAt <= now
}
