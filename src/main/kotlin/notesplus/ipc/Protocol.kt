package notesplus.ipc

/**
 * Line-based protocol over a loopback socket between Instances and the Tray Host.
 *
 * Instance → Tray Host: `HELLO <token> <pid>` first, `UNSAVED 0|1` as a reply, `BYE` before a normal exit.
 * Tray Host → Instance: `QUERY_UNSAVED`, `EXIT`.
 * A connection that closes without `BYE` means the Instance crashed.
 */
internal object Protocol {
    const val HELLO = "HELLO"
    const val BYE = "BYE"
    const val UNSAVED = "UNSAVED"
    const val QUERY_UNSAVED = "QUERY_UNSAVED"
    const val EXIT = "EXIT"
}
