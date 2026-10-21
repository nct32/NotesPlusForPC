package notesplus

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import notesplus.data.BackupRepo
import notesplus.data.Db
import notesplus.data.FolderRepo
import notesplus.data.NoteRepo
import notesplus.data.ReminderRepo
import notesplus.data.SettingsRepo
import notesplus.data.TagRepo
import notesplus.data.TrashRepo
import notesplus.io.ExternalFiles
import notesplus.ipc.Launcher
import notesplus.ipc.TrayClient
import notesplus.ipc.TrayFiles
import notesplus.tray.TrayHost
import notesplus.ui.AppIcon
import notesplus.ui.AppState
import notesplus.ui.BackupDialogs
import notesplus.ui.BackupState
import notesplus.ui.FileDialogs
import notesplus.ui.FileScreen
import notesplus.ui.FileState
import notesplus.ui.NotesScreen
import notesplus.ui.NotesPlusTheme
import notesplus.ui.Page
import notesplus.ui.SettingsScreen
import notesplus.ui.SettingsState
import notesplus.ui.ReminderState
import notesplus.ui.RemindersScreen
import notesplus.ui.Strings
import notesplus.ui.TrashScreen
import notesplus.ui.TrashState
import notesplus.ui.UnsavedDialog
import notesplus.ui.io
import java.awt.FileDialog
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

private const val SYNC_POLL_MS = 1500L

/** Entry point: `--tray` starts the Tray Host, otherwise an Instance window. */
fun main(args: Array<String>) {
    if ("--tray" in args) TrayHost.run() else runInstance()
}

private fun runInstance() {
    val db = Db.open()
    try {
        application {
            val scope = rememberCoroutineScope()
            val state = remember { AppState(NoteRepo(db), FolderRepo(db), TagRepo(db), SettingsRepo(db), scope) }
            val trashState = remember { TrashState(TrashRepo(db), scope) }
            val reminderState = remember { ReminderState(ReminderRepo(db), scope) }
            val fileState = remember { FileState(scope, onOpened = { state.goTo(Page.FILE) }) }
            val settingsState = remember { SettingsState(SettingsRepo(db), scope) }
            LaunchedEffect(Unit) { state.load() }
            LaunchedEffect(Unit) { settingsState.reload() }
            LaunchedEffect(Unit) { settingsState.followWindows() }
            LaunchedEffect(settingsState.language) { Strings.current = settingsState.language }
            LaunchedEffect(settingsState.settings.autosave) { state.applyAutosaveSetting(settingsState.settings.autosave) }
            // Pick up changes saved by other Instances and the Tray Host (ADR 0002).
            LaunchedEffect(Unit) {
                var last = io { db.dataVersion() }
                while (true) {
                    delay(SYNC_POLL_MS)
                    try {
                        val v = io { db.dataVersion() }
                        if (v != last) {
                            last = v
                            settingsState.reload()
                            state.syncFromDb()
                            when (state.page) {
                                Page.REMINDERS -> reminderState.refresh()
                                Page.TRASH -> trashState.refresh()
                                Page.NOTES, Page.FILE, Page.SETTINGS -> Unit
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A busy database or similar: try again on the next tick.
                    }
                }
            }

            /** Runs [action] once neither the Note editor nor the External File has unsaved edits. */
            fun whenAllSafe(action: () -> Unit) = state.whenSafe {
                if (fileState.dirty) state.askDiscard(action) else action()
            }

            fun openFile(path: Path) = whenAllSafe {
                fileState.close()
                fileState.requestOpen(path)
            }

            val tray = remember {
                lateinit var client: TrayClient
                client = TrayClient(
                    TrayFiles.defaultDir,
                    object : TrayClient.Handler {
                        // Pending autosaves are flushed on Exit, so only manual-save edits count.
                        override fun hasUnsaved() =
                            (state.editor?.dirty == true && !state.autosaveOn) || fileState.dirty

                        override fun onExitRequested() {
                            scope.launch {
                                if (state.autosaveOn) state.saveNow()?.join()
                                client.bye()
                                exitApplication()
                            }
                        }
                    },
                    spawnTray = { Launcher.spawn("--tray") },
                )
                client
            }
            DisposableEffect(Unit) {
                tray.start()
                onDispose { }
            }

            // The window, for parenting native file dialogs; set once the window exists.
            val windowRef = remember { AtomicReference<ComposeWindow>() }
            val backupState = remember {
                BackupState(
                    BackupRepo(db), settingsState, scope,
                    pickOpen = { ext -> chooseFile(windowRef.get(), listOf(ext)) },
                    pickSave = { name -> chooseSaveFile(windowRef.get(), name) },
                    onDataChanged = {
                        state.syncFromDb()
                        reminderState.refresh()
                    },
                )
            }

            Window(
                onCloseRequest = {
                    whenAllSafe {
                        tray.bye()
                        exitApplication()
                    }
                },
                title = Strings.APP_NAME,
                icon = AppIcon.painter,
                state = rememberWindowState(width = 1200.dp, height = 760.dp),
                onPreviewKeyEvent = { e ->
                    if (e.type == KeyEventType.KeyDown && e.isCtrlPressed && e.key == Key.S) {
                        if (state.page == Page.FILE) fileState.save() else state.saveNow()
                        true
                    } else {
                        false
                    }
                },
            ) {
                windowRef.set(window)
                // Drop a file onto the window to open it.
                LaunchedEffect(Unit) { installFileDrop(window) { openFile(it) } }

                NotesPlusTheme(settingsState.accentColor, settingsState.dark) {
                    Surface(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxSize()) {
                            NavigationRail(Modifier.fillMaxHeight()) {
                                NavigationRailItem(
                                    selected = state.page == Page.NOTES,
                                    onClick = { state.goTo(Page.NOTES) },
                                    icon = { Text("📝") },
                                    label = { Text(Strings.NOTES) },
                                )
                                NavigationRailItem(
                                    selected = state.page == Page.REMINDERS,
                                    onClick = { state.goTo(Page.REMINDERS) },
                                    icon = { Text("⏰") },
                                    label = { Text(Strings.REMINDERS) },
                                )
                                NavigationRailItem(
                                    selected = state.page == Page.TRASH,
                                    onClick = { state.goTo(Page.TRASH) },
                                    icon = { Text("🗑") },
                                    label = { Text(Strings.TRASH) },
                                )
                                NavigationRailItem(
                                    selected = false,
                                    onClick = { chooseFile(window, ExternalFiles.SUPPORTED)?.let { openFile(it) } },
                                    icon = { Text("📂") },
                                    label = { Text(Strings.OPEN_FILE) },
                                )
                                fileState.file?.let { f ->
                                    NavigationRailItem(
                                        selected = state.page == Page.FILE,
                                        onClick = { state.goTo(Page.FILE) },
                                        icon = { Text("📄") },
                                        label = {
                                            Text(
                                                (if (fileState.dirty) "• " else "") + f.path.fileName,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                NavigationRailItem(
                                    selected = state.page == Page.SETTINGS,
                                    onClick = { state.goTo(Page.SETTINGS) },
                                    icon = { Text("⚙") },
                                    label = { Text(Strings.SETTINGS) },
                                )
                            }
                            VerticalDivider()
                            when (state.page) {
                                Page.NOTES -> NotesScreen(state)
                                Page.REMINDERS -> RemindersScreen(reminderState)
                                Page.TRASH -> TrashScreen(trashState)
                                Page.SETTINGS -> SettingsScreen(settingsState, backupState)
                                Page.FILE -> FileScreen(
                                    fileState,
                                    onSaveAsNote = state::createNoteFrom,
                                    onClose = {
                                        val close = {
                                            fileState.close()
                                            state.goTo(Page.NOTES)
                                        }
                                        if (fileState.dirty) state.askDiscard(close) else close()
                                    },
                                )
                            }
                        }
                    }
                }
                NotesPlusTheme(settingsState.accentColor, settingsState.dark) {
                    FileDialogs(fileState)
                    BackupDialogs(backupState)
                    if (state.pendingDiscard != null) {
                        UnsavedDialog(onNo = state::cancelDiscard, onDiscard = state::confirmDiscard)
                    }
                }
            }
        }
    } finally {
        db.close()
    }
}

/** The native Windows open dialog, filtered to [extensions]. */
private fun chooseFile(window: ComposeWindow, extensions: Collection<String>): Path? {
    val dialog = FileDialog(window, Strings.OPEN_FILE, FileDialog.LOAD)
    // Windows' native dialog ignores FilenameFilter but accepts a ";"-separated pattern.
    dialog.file = extensions.joinToString(";") { "*.$it" }
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return Path.of(dialog.directory, name)
}

/** The native Windows save dialog, starting with [fileName]. */
private fun chooseSaveFile(window: ComposeWindow, fileName: String): Path? {
    val dialog = FileDialog(window, Strings.EXPORT, FileDialog.SAVE)
    dialog.file = fileName
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return Path.of(dialog.directory, name)
}

private fun installFileDrop(window: ComposeWindow, onDrop: (Path) -> Unit) {
    window.contentPane.dropTarget = DropTarget(window.contentPane, object : DropTargetAdapter() {
        override fun drop(e: DropTargetDropEvent) {
            e.acceptDrop(DnDConstants.ACTION_COPY)
            val files = runCatching {
                @Suppress("UNCHECKED_CAST")
                e.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
            }.getOrNull()
            e.dropComplete(files != null)
            files?.firstOrNull()?.let { onDrop(it.toPath()) }
        }
    })
}
