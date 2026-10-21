package notesplus.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import notesplus.data.ImportResult
import notesplus.domain.Lang
import notesplus.io.ExternalFileException.Reason
import notesplus.io.BackupException.Reason as BackupReason

/**
 * All UI text, in English and Thai. Reading [current] from composition makes the UI
 * redraw when the language changes.
 */
object Strings {
    var current by mutableStateOf(Lang.EN)

    private fun t(en: String, th: String) = when (current) {
        Lang.EN -> en
        Lang.TH -> th
    }

    // Fixed in every language, as specified.
    const val APP_NAME = "Notes+"
    const val TRAY_TITLE = "Notes+ Tray"
    const val UNSAVED_PROMPT = "There are unsaved changes. Close?"
    const val NO = "No"
    const val DISCARD_EXIT = "GOODBYE UNSAVED DATA (Exit)"
    const val OKAY = "Okay"
    const val INSTANCE_CRASHED = "It seems an instance has crashed, maybe you should investigate it..."
    const val THEME_LIGHT = "ライト"
    const val THEME_DARK = "ダーク"
    const val THEME_SYSTEM = "システムデフォルト"

    val SEARCH get() = t("Search", "ค้นหา")
    val NEW_NOTE get() = t("New note", "โน้ตใหม่")
    val UNTITLED get() = t("Untitled", "ไม่มีชื่อ")
    val TITLE get() = t("Title", "ชื่อเรื่อง")
    val EDIT get() = t("Edit", "แก้ไข")
    val PREVIEW get() = t("Preview", "ตัวอย่าง")
    val SAVE get() = t("Save", "บันทึก")
    val SAVED get() = t("Saved", "บันทึกแล้ว")
    val UNSAVED get() = t("Unsaved changes", "ยังไม่ได้บันทึก")
    val AUTOSAVE_ON get() = t("Autosave: On", "บันทึกอัตโนมัติ: เปิด")
    val AUTOSAVE_OFF get() = t("Autosave: Off", "บันทึกอัตโนมัติ: ปิด")
    val PIN get() = t("Pin", "ปักหมุด")
    val UNPIN get() = t("Unpin", "เลิกปักหมุด")
    val MOVE_TO_TRASH get() = t("Move to Trash", "ย้ายไปถังขยะ")
    val NO_NOTE_SELECTED get() = t("Select a note or create a new one", "เลือกโน้ตหรือสร้างโน้ตใหม่")
    val NO_NOTES get() = t("No notes", "ไม่มีโน้ต")
    val SORT get() = t("Sort", "เรียง")
    val SORT_NEWEST get() = t("Newest", "ใหม่สุด")
    val SORT_OLDEST get() = t("Oldest", "เก่าสุด")
    val SORT_TITLE get() = t("A–Z", "ก–ฮ / A–Z")
    val SORT_SIZE get() = t("Size", "ขนาด")

    val NOTES get() = t("Notes", "โน้ต")
    val TRASH get() = t("Trash", "ถังขยะ")
    val TRASH_HINT get() = t(
        "Items are permanently deleted 30 days after they were moved to Trash.",
        "รายการจะถูกลบถาวรเมื่อครบ 30 วันหลังจากย้ายไปถังขยะ",
    )
    val TRASH_IS_EMPTY get() = t("Trash is empty", "ถังขยะว่างเปล่า")
    val EMPTY_TRASH get() = t("Empty Trash", "ล้างถังขยะ")
    val EMPTY_TRASH_PROMPT get() = t(
        "Permanently delete everything in Trash? This can't be undone.",
        "ลบทุกอย่างในถังขยะอย่างถาวรหรือไม่? การกระทำนี้ย้อนกลับไม่ได้",
    )
    val RESTORE get() = t("Restore", "กู้คืน")
    val DELETE_PERMANENTLY get() = t("Delete permanently", "ลบถาวร")
    val CANCEL get() = t("Cancel", "ยกเลิก")
    val OK get() = t("OK", "ตกลง")

    val ALL_NOTES get() = t("All notes", "โน้ตทั้งหมด")
    val NO_FOLDER get() = t("No folder", "ไม่มีโฟลเดอร์")
    val FOLDER get() = t("Folder", "โฟลเดอร์")
    val NEW_FOLDER get() = t("New folder", "โฟลเดอร์ใหม่")
    val RENAME_FOLDER get() = t("Rename folder", "เปลี่ยนชื่อโฟลเดอร์")
    val DELETE_FOLDER get() = t("Delete folder", "ลบโฟลเดอร์")
    val FOLDER_NAME get() = t("Folder name", "ชื่อโฟลเดอร์")
    val TAGS get() = t("Tags", "แท็ก")
    val ADD_TAG get() = t("Add tag (Enter)", "เพิ่มแท็ก (Enter)")

    val REMINDERS get() = t("Reminders", "การเตือน")
    val NEW_REMINDER get() = t("New reminder", "การเตือนใหม่")
    val EDIT_REMINDER get() = t("Edit reminder", "แก้ไขการเตือน")
    val NO_REMINDERS get() = t("No reminders", "ไม่มีการเตือน")
    val DESCRIPTION get() = t("Description", "รายละเอียด")
    val OVERDUE get() = t("Overdue", "เลยกำหนด")
    val DELETE get() = t("Delete", "ลบ")
    val TIME_IN_PAST get() = t(
        "This time is in the past. The reminder will be created overdue and won't send a notification.",
        "เวลานี้ผ่านไปแล้ว การเตือนจะถูกสร้างในสถานะเลยกำหนดและจะไม่ส่งการแจ้งเตือน",
    )

    val TRAY_OPEN get() = t("Open", "เปิด")
    val TRAY_EXIT get() = t("Exit", "ออก")

    val OPEN_FILE get() = t("Open file", "เปิดไฟล์")
    val FILE get() = t("File", "ไฟล์")
    val SAVE_TO_FILE get() = t("Save to file", "บันทึกลงไฟล์")
    val SAVE_AS_NOTE get() = t("Save as Note", "บันทึกเป็นโน้ต")
    val CLOSE_FILE get() = t("Close file", "ปิดไฟล์")
    val READ_ONLY get() = t("Read-only", "อ่านอย่างเดียว")
    val OPEN_ANYWAY get() = t("Open anyway", "เปิดต่อ")
    val OVERWRITE get() = t("Overwrite", "เขียนทับ")
    val RELOAD get() = t("Reload", "โหลดใหม่")
    val CHANGED_ON_DISK get() = t(
        "This file was changed by another program since you opened it. Overwrite it with your version, or reload the file from disk (losing your edits)?",
        "ไฟล์นี้ถูกโปรแกรมอื่นแก้ไขหลังจากที่คุณเปิด ต้องการเขียนทับด้วยฉบับของคุณ หรือโหลดไฟล์ใหม่จากดิสก์ (การแก้ไขของคุณจะหายไป)?",
    )

    val SETTINGS get() = t("Settings", "การตั้งค่า")
    val APPEARANCE get() = t("Appearance", "รูปลักษณ์")
    val THEME get() = t("Theme", "ธีม")
    val ACCENT_COLOR get() = t("Accent color", "สีเน้น")
    val ACCENT_AUTO get() = t("Auto (Windows)", "อัตโนมัติ (Windows)")
    val LANGUAGE get() = t("Language", "ภาษา")
    val LANGUAGE_SYSTEM get() = t("System Default", "ค่าเริ่มต้นของระบบ")
    val LANGUAGE_HINT get() = t(
        "Only languages installed in Windows are listed.",
        "แสดงเฉพาะภาษาที่ติดตั้งใน Windows",
    )
    val EDITOR get() = t("Editor", "ตัวแก้ไข")
    val AUTOSAVE get() = t("Autosave notes", "บันทึกโน้ตอัตโนมัติ")
    val AUTOSAVE_HINT get() = t(
        "The Autosave button in the editor overrides this for one window until it closes.",
        "ปุ่มบันทึกอัตโนมัติในตัวแก้ไขจะใช้แทนค่านี้เฉพาะหน้าต่างนั้นจนกว่าจะปิด",
    )
    val STARTUP get() = t("Startup", "การเริ่มต้น")
    val START_WITH_WINDOWS get() = t("Start with Windows", "เริ่มพร้อม Windows")
    val BACKGROUND_AUTOSTART get() = t(
        "Background Autostart (start in the system tray only, without a window)",
        "เริ่มในเบื้องหลัง (เริ่มเฉพาะในถาดระบบ ไม่เปิดหน้าต่าง)",
    )
    val AUTOSTART_UNAVAILABLE get() = t(
        "Available in the installed app only.",
        "ใช้ได้เฉพาะในแอปที่ติดตั้งแล้วเท่านั้น",
    )

    const val SKIP_LEAVE = "Skip (leave them alone)"

    val BACKUP get() = t("Backup", "สำรองข้อมูล")
    val BACKUP_HINT get() = t(
        "Export to a file, or import from one. Notes (.nptxt) include their folders and tags; Trash isn't exported. Importing settings (.npcfg) applies them immediately.",
        "ส่งออกเป็นไฟล์ หรือนำเข้าจากไฟล์ โน้ต (.nptxt) รวมโฟลเดอร์และแท็ก ไม่รวมถังขยะ การนำเข้าการตั้งค่า (.npcfg) จะมีผลทันที",
    )
    val EXPORT get() = t("Export…", "ส่งออก…")
    val IMPORT get() = t("Import…", "นำเข้า…")
    val CONFLICT_TITLE get() = t("Already exists", "มีอยู่แล้ว")
    val MERGE get() = t("Merge", "รวม")
    val CANCEL_IMPORT get() = t("Cancel import", "ยกเลิกการนำเข้า")
    val IMPORTED_SUFFIX get() = t(" (imported)", " (นำเข้า)")
    val SETTINGS_EXPORTED get() = t("Settings exported.", "ส่งออกการตั้งค่าแล้ว")
    val SETTINGS_IMPORTED get() = t("Settings imported and applied.", "นำเข้าและใช้การตั้งค่าแล้ว")

    fun conflictPrompt(label: String) = t(
        "\"$label\" already exists here with different content. Merge keeps both (the imported copy is marked \"(imported)\"), Skip keeps yours, Overwrite replaces yours with the imported one.",
        "\"$label\" มีอยู่แล้วแต่เนื้อหาต่างกัน รวม = เก็บทั้งสองฉบับ (ฉบับที่นำเข้าจะมีคำว่า \"(นำเข้า)\") ข้าม = เก็บฉบับของคุณ เขียนทับ = แทนที่ด้วยฉบับที่นำเข้า",
    )

    fun applyToAll(n: Int) = t("Apply to all $n remaining conflicts", "ใช้กับรายการที่ซ้ำทั้งหมดที่เหลือ $n รายการ")

    fun exportedNotes(n: Int) = t("Exported ${notesCount(n)}.", "ส่งออก ${notesCount(n)} แล้ว")
    fun exportedReminders(n: Int) = t(
        if (n == 1) "Exported 1 reminder." else "Exported $n reminders.",
        "ส่งออกการเตือน $n รายการแล้ว",
    )

    fun importResult(r: ImportResult) = t(
        "Import finished: ${r.added} added, ${r.merged} merged, ${r.overwritten} overwritten, ${r.skipped} skipped.",
        "นำเข้าเสร็จสิ้น: เพิ่ม ${r.added} รวม ${r.merged} เขียนทับ ${r.overwritten} ข้าม ${r.skipped}",
    )

    fun backupError(reason: BackupReason) = when (reason) {
        BackupReason.WRONG_TYPE -> t("This isn't the right kind of Notes+ file.", "ไฟล์นี้ไม่ใช่ไฟล์ Notes+ ประเภทที่ถูกต้อง")
        BackupReason.NEWER_VERSION -> t(
            "This file was made by a newer version of Notes+. Please update Notes+ first.",
            "ไฟล์นี้สร้างจาก Notes+ รุ่นใหม่กว่า กรุณาอัปเดต Notes+ ก่อน",
        )
        BackupReason.CORRUPT -> t("This file is damaged or not valid, so nothing was imported.", "ไฟล์นี้เสียหายหรือไม่ถูกต้อง จึงไม่ได้นำเข้าอะไรเลย")
        BackupReason.INVALID -> t("This file contains invalid data, so nothing was imported.", "ไฟล์นี้มีข้อมูลไม่ถูกต้อง จึงไม่ได้นำเข้าอะไรเลย")
        BackupReason.IO -> t("The file couldn't be read or written.", "อ่านหรือเขียนไฟล์ไม่ได้")
    }

    fun languageName(lang: Lang) = when (lang) {
        Lang.EN -> "English"
        Lang.TH -> "ไทย (Thai)"
    }

    fun largeFilePrompt(bytes: Long): String {
        val mb = "%.1f".format(bytes / 1024.0 / 1024.0)
        return t(
            "This file is $mb MB. Large files can make the editor slow. Open anyway?",
            "ไฟล์นี้มีขนาด $mb MB ไฟล์ขนาดใหญ่อาจทำให้ตัวแก้ไขช้า ต้องการเปิดต่อหรือไม่?",
        )
    }

    fun fileError(reason: Reason) = when (reason) {
        Reason.BINARY_FORMAT -> t(
            "This file format isn't supported. Notes+ only edits plain-text files (.doc, .docx, .odt, .pages, .pdf and .wpd can't be opened).",
            "ไม่รองรับรูปแบบไฟล์นี้ Notes+ แก้ไขได้เฉพาะไฟล์ข้อความธรรมดา (ไม่สามารถเปิด .doc, .docx, .odt, .pages, .pdf และ .wpd)",
        )
        Reason.UNKNOWN_TYPE -> t("This file type isn't supported.", "ไม่รองรับประเภทไฟล์นี้")
        Reason.TOO_LARGE -> t("This file is larger than 50 MB and can't be opened.", "ไฟล์นี้ใหญ่กว่า 50 MB จึงเปิดไม่ได้")
        Reason.NOT_FOUND -> t("The file couldn't be found.", "ไม่พบไฟล์")
        Reason.BINARY_CONTENT -> t(
            "This file doesn't contain plain text and can't be opened.",
            "ไฟล์นี้ไม่ใช่ข้อความธรรมดา จึงเปิดไม่ได้",
        )
        Reason.ENCODING -> t(
            "This file's text encoding isn't supported, so it can't be opened safely.",
            "ไม่รองรับการเข้ารหัสข้อความของไฟล์นี้ จึงเปิดอย่างปลอดภัยไม่ได้",
        )
        Reason.UNENCODABLE -> t(
            "The text contains characters that this file's encoding can't store, so it wasn't saved.",
            "ข้อความมีอักขระที่การเข้ารหัสของไฟล์นี้เก็บไม่ได้ จึงไม่ได้บันทึก",
        )
        Reason.READ_ONLY -> t("This file is read-only and can't be saved.", "ไฟล์นี้เป็นแบบอ่านอย่างเดียว จึงบันทึกไม่ได้")
        Reason.IO -> t("The file couldn't be read or written.", "อ่านหรือเขียนไฟล์ไม่ได้")
    }

    fun notesCount(n: Int) = t(if (n == 1) "1 note" else "$n notes", "$n โน้ต")
    fun daysLeft(n: Int) = t(if (n == 1) "1 day left" else "$n days left", "เหลืออีก $n วัน")
    fun deleteFolderPrompt(name: String, n: Int) = t(
        "Move the folder \"$name\" and its ${notesCount(n)} to Trash?",
        "ย้ายโฟลเดอร์ \"$name\" และ ${notesCount(n)} ในโฟลเดอร์ไปถังขยะหรือไม่?",
    )
}
