package notesplus.domain

data class Note(
    val id: String,
    val title: String,
    val body: String,
    val folderId: String?,
    val pinned: Boolean,
    val createdAt: Long,
    val modifiedAt: Long,
    val deletedAt: Long? = null,
)

data class Folder(val id: String, val name: String, val deletedAt: Long? = null)

/** Pinned Notes always stay on top; this orders everything below them. */
enum class SortOrder { NEWEST, OLDEST, TITLE_AZ, SIZE }

/** Which Notes the note list shows. */
sealed interface FolderFilter {
    data object All : FolderFilter
    data object NoFolder : FolderFilter
    data class In(val folderId: String) : FolderFilter
}

data class TrashedFolder(val folder: Folder, val notes: List<Note>)

/** [notes] are Notes trashed on their own, outside any trashed Folder. */
data class TrashContents(val folders: List<TrashedFolder>, val notes: List<Note>)

const val TRASH_DAYS = 30
const val DAY_MS = 24L * 60 * 60 * 1000
