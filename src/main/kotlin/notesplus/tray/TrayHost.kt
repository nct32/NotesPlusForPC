package notesplus.tray

import notesplus.data.Db
import notesplus.data.ReminderRepo
import notesplus.data.SettingsRepo
import notesplus.data.TrashRepo
import notesplus.domain.Languages
import notesplus.platform.Windows
import notesplus.ipc.Launcher
import notesplus.ipc.TrayFiles
import notesplus.ipc.TrayLog
import notesplus.ipc.TrayServer
import notesplus.ui.Strings
import notesplus.ui.formatDue
import notesplus.ui.AppIcon
import notesplus.update.Updater
import java.awt.Frame
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.concurrent.thread

/**
 * The Tray Host: one background process that owns the tray icon, fires Reminders,
 * purges Trash, and closes every Instance on Exit (ADR 0002).
 */
object TrayHost {
    private const val TICK_MS = 1000L
    private const val ALIVE_EVERY_MS = 5000L
    private const val LANGUAGE_EVERY_MS = 2000L
    private const val PURGE_EVERY_MS = 60L * 60 * 1000
    /** A Tray Host down for less than this (crash + relaunch) still fires what came due meanwhile. */
    private const val BRIEF_OUTAGE_MS = 30_000L

    private val exiting = AtomicBoolean(false)

    fun run() {
        val dir = TrayFiles.defaultDir
        TrayLog.file = dir.resolve("tray.log")
        val lock = TrayFiles.tryLock(dir)
        if (lock == null) {
            TrayLog.log("Another Tray Host is already running; exiting")
            return
        }
        if (!SystemTray.isSupported()) {
            TrayLog.log("System tray not supported; exiting")
            return
        }
        TrayLog.log("Tray Host started")
        Thread.setDefaultUncaughtExceptionHandler { t, e -> TrayLog.log("Uncaught in ${t.name}", e) }
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }

        val db = Db.open()
        val reminders = ReminderRepo(db)
        val trash = TrashRepo(db)
        val settings = SettingsRepo(db)
        runCatching {
            Strings.current = Languages.resolve(settings.load().languageOverride, Windows.displayLanguage(), Windows.installedLanguages())
        }

        // Reminders that passed while Notes+ was closed: nothing happens.
        val now = System.currentTimeMillis()
        val lastAlive = TrayFiles.readAlive(dir)
        val cutoff = if (lastAlive != null && now - lastAlive in 0..BRIEF_OUTAGE_MS) lastAlive else now
        reminders.markHandledUpTo(cutoff)
        TrayFiles.writeAlive(dir, now)

        val server = TrayServer(dir, onInstanceCrashed = ::showCrashDialog)
        server.start()
        Updater.check()

        lateinit var icon: TrayIcon
        val onOpen = { runCatching { Launcher.spawn() }; Unit }
        val onExit = {
            if (!exiting.get()) {
                thread(name = "tray-exit") {
                    if (exiting.get()) return@thread
                    if (server.anyUnsaved() && !confirmDiscard()) return@thread
                    if (!exiting.compareAndSet(false, true)) return@thread
                    TrayLog.log("Exit: closing ${server.instanceCount} instance(s)")
                    server.exitAll()
                    SwingUtilities.invokeAndWait { SystemTray.getSystemTray().remove(icon) }
                    server.close()
                    db.close()
                    lock.release()
                    System.exit(0)
                }
            }
        }

        SwingUtilities.invokeAndWait {
            icon = TrayIcon(AppIcon.image, Strings.APP_NAME, buildMenu(onOpen, onExit)).apply {
                isImageAutoSize = true
                // Left click opens the same menu as right click.
                addMouseListener(object : MouseAdapter() {
                    override fun mouseReleased(e: MouseEvent) {
                        if (e.button == MouseEvent.BUTTON1) showMenuAt(e.xOnScreen, e.yOnScreen, onOpen, onExit)
                    }
                })
            }
            SystemTray.getSystemTray().add(icon)
        }

        thread(isDaemon = true, name = "tray-scheduler") {
            var lastAliveWrite = 0L
            var lastLanguageCheck = 0L
            var lastPurge = 0L
            while (!exiting.get()) {
                val t = System.currentTimeMillis()
                try {
                    reminders.takeDue(t).forEach { r ->
                        TrayLog.log("Firing reminder ${r.id}")
                        icon.displayMessage(r.title, r.description.ifBlank { formatDue(r.dueAt) }, TrayIcon.MessageType.NONE)
                    }
                } catch (e: Exception) {
                    TrayLog.log("Reminder check failed", e)
                }
                if (t - lastLanguageCheck >= LANGUAGE_EVERY_MS) {
                    runCatching { updateLanguage(settings, icon.popupMenu) }
                    lastLanguageCheck = t
                }
                if (t - lastAliveWrite >= ALIVE_EVERY_MS) {
                    runCatching { TrayFiles.writeAlive(dir, t) }
                    lastAliveWrite = t
                }
                if (t - lastPurge >= PURGE_EVERY_MS) {
                    runCatching { trash.purge() }
                    lastPurge = t
                }
                Thread.sleep(TICK_MS)
            }
        }
        // The AWT event thread keeps this process alive until Exit.
    }

    private fun buildMenu(onOpen: () -> Unit, onExit: () -> Unit) = PopupMenu().apply {
        add(MenuItem(Strings.TRAY_OPEN).apply { addActionListener { onOpen() } })
        addSeparator()
        add(MenuItem(Strings.TRAY_EXIT).apply { addActionListener { onExit() } })
    }

    /** Follows the UI language setting (and the Windows display language) for the tray menu. */
    private fun updateLanguage(settings: SettingsRepo, menu: PopupMenu) {
        val lang = Languages.resolve(settings.load().languageOverride, Windows.displayLanguage(), Windows.installedLanguages())
        if (lang == Strings.current) return
        Strings.current = lang
        SwingUtilities.invokeLater {
            menu.getItem(0).label = Strings.TRAY_OPEN
            menu.getItem(2).label = Strings.TRAY_EXIT
        }
    }

    /** AWT only shows a tray icon's menu on right click, so left click shows a copy from a hidden window. */
    private fun showMenuAt(x: Int, y: Int, onOpen: () -> Unit, onExit: () -> Unit) {
        val frame = Frame().apply {
            isUndecorated = true
            type = Window.Type.UTILITY
            setBounds(x, y, 1, 1)
            isVisible = true
        }
        val menu = buildMenu({ frame.dispose(); onOpen() }, { frame.dispose(); onExit() })
        frame.add(menu)
        menu.show(frame, 0, 0)
        // On Windows show() returns once the menu closes.
        SwingUtilities.invokeLater { frame.dispose() }
    }

    private fun showCrashDialog() = SwingUtilities.invokeLater {
        TrayLog.log("Showing crash dialog")
        try {
            val ok = Strings.OKAY
            JOptionPane.showOptionDialog(
                null, Strings.INSTANCE_CRASHED, Strings.TRAY_TITLE,
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, arrayOf(ok), ok,
            )
            TrayLog.log("Crash dialog dismissed")
        } catch (e: Throwable) {
            TrayLog.log("Crash dialog failed", e)
        }
    }

    /** "There are unsaved changes. Close?" — true when the user picks the discard button. */
    private fun confirmDiscard(): Boolean {
        var discard = false
        SwingUtilities.invokeAndWait {
            val options = arrayOf(Strings.NO, Strings.DISCARD_EXIT)
            val choice = JOptionPane.showOptionDialog(
                null, Strings.UNSAVED_PROMPT, Strings.TRAY_TITLE,
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0],
            )
            discard = choice == 1
        }
        return discard
    }

}
