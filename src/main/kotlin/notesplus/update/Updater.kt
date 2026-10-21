package notesplus.update

/**
 * Auto-update entry point. Intentionally does nothing for now; the real updater will be
 * added later behind this call (the Tray Host calls it once at startup).
 */
object Updater {
    fun check() = Unit
}
