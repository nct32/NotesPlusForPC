package notesplus.data

import notesplus.domain.REMINDER_DESCRIPTION_MAX
import notesplus.domain.Reminder
import java.sql.ResultSet
import java.util.UUID

class ReminderRepo(private val db: Db, private val clock: () -> Long = System::currentTimeMillis) {

    /** All Reminders matching [query] (title or description), soonest first. */
    fun list(query: String = ""): List<Reminder> {
        val q = query.trim()
        val pattern = "%" + q.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return db.read { c ->
            c.prepareStatement(
                """
                SELECT * FROM reminder
                WHERE ? = '' OR np_lower(title) LIKE ? ESCAPE '\' OR np_lower(description) LIKE ? ESCAPE '\'
                ORDER BY due_at ASC, id
                """.trimIndent()
            ).use { st ->
                st.setString(1, q)
                st.setString(2, pattern)
                st.setString(3, pattern)
                st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toReminder()) } }
            }
        }
    }

    fun get(id: String): Reminder? = db.read { c ->
        c.prepareStatement("SELECT * FROM reminder WHERE id = ?").use {
            it.setString(1, id)
            it.executeQuery().use { rs -> if (rs.next()) rs.toReminder() else null }
        }
    }

    /** A Reminder created for a time already past never fires, so it starts out notified. */
    fun create(title: String, description: String, dueAt: Long): Reminder {
        val r = Reminder(UUID.randomUUID().toString(), title.trim(), description, dueAt, notified = dueAt <= clock())
        validate(r)
        db.tx { c ->
            c.prepareStatement("INSERT INTO reminder (id, title, description, due_at, notified) VALUES (?, ?, ?, ?, ?)").use {
                it.setString(1, r.id)
                it.setString(2, r.title)
                it.setString(3, r.description)
                it.setLong(4, r.dueAt)
                it.setInt(5, if (r.notified) 1 else 0)
                it.executeUpdate()
            }
        }
        return r
    }

    /** Updates a Reminder. A new future time makes it fire again; a new past time never fires. */
    fun update(id: String, title: String, description: String, dueAt: Long) {
        validate(Reminder(id, title.trim(), description, dueAt))
        db.tx { c ->
            c.prepareStatement(
                """
                UPDATE reminder SET title = ?, description = ?,
                    notified = CASE WHEN due_at <> ? THEN ? ELSE notified END,
                    due_at = ?
                WHERE id = ?
                """.trimIndent()
            ).use {
                it.setString(1, title.trim())
                it.setString(2, description)
                it.setLong(3, dueAt)
                it.setInt(4, if (dueAt <= clock()) 1 else 0)
                it.setLong(5, dueAt)
                it.setString(6, id)
                it.executeUpdate()
            }
        }
    }

    fun delete(id: String) = db.tx { c ->
        c.prepareStatement("DELETE FROM reminder WHERE id = ?").use {
            it.setString(1, id)
            it.executeUpdate()
        }
    }

    /** Marks Reminders due at or before [cutoff] as handled without firing them. */
    fun markHandledUpTo(cutoff: Long) = db.tx { c ->
        c.prepareStatement("UPDATE reminder SET notified = 1 WHERE notified = 0 AND due_at <= ?").use {
            it.setLong(1, cutoff)
            it.executeUpdate()
        }
    }

    /** Returns the Reminders that are due now and not yet fired, marking them fired. */
    fun takeDue(now: Long = clock()): List<Reminder> = db.tx { c ->
        val due = c.prepareStatement("SELECT * FROM reminder WHERE notified = 0 AND due_at <= ? ORDER BY due_at").use {
            it.setLong(1, now)
            it.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toReminder()) } }
        }
        c.prepareStatement("UPDATE reminder SET notified = 1 WHERE id = ?").use { st ->
            due.forEach {
                st.setString(1, it.id)
                st.executeUpdate()
            }
        }
        due
    }

    private fun validate(r: Reminder) {
        require(r.title.isNotEmpty()) { "Reminder title is empty" }
        require(r.description.length <= REMINDER_DESCRIPTION_MAX) { "Reminder description is over $REMINDER_DESCRIPTION_MAX characters" }
    }

    private fun ResultSet.toReminder() = Reminder(
        id = getString("id"),
        title = getString("title"),
        description = getString("description"),
        dueAt = getLong("due_at"),
        notified = getInt("notified") != 0,
    )
}
