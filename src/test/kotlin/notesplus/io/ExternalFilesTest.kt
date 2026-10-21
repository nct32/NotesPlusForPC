package notesplus.io

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ExternalFilesTest {
    private val dir = Files.createTempDirectory("notesplus-files")

    @AfterTest
    fun cleanup() {
        dir.toFile().setWritable(true)
        dir.toFile().listFiles()?.forEach { it.setWritable(true) }
        dir.toFile().deleteRecursively()
    }

    private fun file(name: String, bytes: ByteArray): Path = dir.resolve(name).also { Files.write(it, bytes) }

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    /** Opens, edits nothing, saves, and checks the file is byte-for-byte unchanged. */
    private fun assertRoundTrip(path: Path) {
        val before = Files.readAllBytes(path)
        val opened = ExternalFiles.read(path)
        ExternalFiles.write(path, opened.text, opened.format)
        assertContentEquals(before, Files.readAllBytes(path))
    }

    @Test
    fun utf8WithoutBomAndCrlfRoundTripsExactly() {
        val p = file("a.txt", "สวัสดี\r\nline 2\r\n".toByteArray(Charsets.UTF_8))
        val opened = ExternalFiles.read(p)
        assertEquals("สวัสดี\nline 2\n", opened.text)
        assertEquals("UTF-8 · CRLF", opened.format.label)
        assertRoundTrip(p)
    }

    @Test
    fun utf8BomAndLfRoundTripExactly() {
        val p = file("a.md", bytes(0xEF, 0xBB, 0xBF) + "# hi\nこんにちは\n".toByteArray())
        val opened = ExternalFiles.read(p)
        assertEquals("# hi\nこんにちは\n", opened.text)
        assertTrue(opened.format.bom)
        assertEquals("\n", opened.format.lineSeparator)
        assertRoundTrip(p)
    }

    @Test
    fun utf16LittleAndBigEndianWithBomRoundTrip() {
        val le = file("le.txt", bytes(0xFF, 0xFE) + "a\r\nb".toByteArray(Charsets.UTF_16LE))
        val be = file("be.txt", bytes(0xFE, 0xFF) + "a\nb".toByteArray(Charsets.UTF_16BE))
        assertEquals("a\nb", ExternalFiles.read(le).text)
        assertEquals("a\nb", ExternalFiles.read(be).text)
        assertRoundTrip(le)
        assertRoundTrip(be)
    }

    @Test
    fun editsAreWrittenWithTheOriginalLineEndings() {
        val p = file("a.log", "one\r\ntwo".toByteArray())
        val opened = ExternalFiles.read(p)
        ExternalFiles.write(p, opened.text + "\nthree", opened.format)
        assertEquals("one\r\ntwo\r\nthree", Files.readString(p))
    }

    private val thai874 = java.nio.charset.Charset.forName("x-windows-874")
    private val western1252 = java.nio.charset.Charset.forName("windows-1252")

    @Test
    fun legacyThaiFileOpensAndRoundTrips() {
        // "สวัสดี" in TIS-620 / Windows-874.
        val p = file("thai.txt", bytes(0xCA, 0xC7, 0xD1, 0xCA, 0xB4, 0xD5, 0x0D, 0x0A))
        val opened = ExternalFiles.read(p, listOf(thai874, western1252))
        assertEquals("สวัสดี\n", opened.text)
        assertEquals("windows-874 · CRLF", opened.format.label)
        val before = Files.readAllBytes(p)
        ExternalFiles.write(p, opened.text, opened.format)
        assertContentEquals(before, Files.readAllBytes(p))
    }

    @Test
    fun legacyWesternFileOpensWhenThaiCannotDecodeIt() {
        // 0x8E (Ž) is undefined in Windows-874, so only Windows-1252 decodes this.
        val p = file("w.txt", "Žcafé".toByteArray(western1252))
        val opened = ExternalFiles.read(p, listOf(thai874, western1252))
        assertEquals("Žcafé", opened.text)
        assertEquals(western1252, opened.format.charset)
    }

    @Test
    fun charactersTheLegacyEncodingCannotHoldAreRefusedOnSave() {
        val p = file("thai.txt", bytes(0xCA, 0xC7, 0xD1, 0xCA, 0xB4, 0xD5))
        val opened = ExternalFiles.read(p, listOf(thai874))
        val e = assertFailsWith<ExternalFileException> { ExternalFiles.write(p, opened.text + " 😀", opened.format) }
        assertEquals(ExternalFileException.Reason.UNENCODABLE, e.reason)
        assertContentEquals(bytes(0xCA, 0xC7, 0xD1, 0xCA, 0xB4, 0xD5), Files.readAllBytes(p), "file untouched")
    }

    @Test
    fun undecodableFileIsRefusedInsteadOfGuessed() {
        // 0x81 is undefined in Windows-1252 and invalid as UTF-8.
        val p = file("x.txt", bytes(0x41, 0x81, 0x42))
        val e = assertFailsWith<ExternalFileException> { ExternalFiles.read(p, listOf(western1252)) }
        assertEquals(ExternalFileException.Reason.ENCODING, e.reason)
    }

    @Test
    fun nulBytesWithoutBomAreRefusedAsBinary() {
        val p = file("x.json", bytes(0x7B, 0x00, 0x7D))
        assertEquals(ExternalFileException.Reason.BINARY_CONTENT, assertFailsWith<ExternalFileException> { ExternalFiles.read(p) }.reason)
    }

    @Test
    fun extensionRules() {
        for (ext in listOf("doc", "DOCX", "odt", "pages", "pdf", "wpd")) {
            assertEquals(
                ExternalFiles.Check.Refused(ExternalFileException.Reason.BINARY_FORMAT),
                ExternalFiles.check(file("f.$ext", bytes(1))),
                ext,
            )
        }
        assertEquals(ExternalFiles.Check.Refused(ExternalFileException.Reason.UNKNOWN_TYPE), ExternalFiles.check(file("f.exe", bytes(1))))
        assertEquals(ExternalFiles.Check.Refused(ExternalFileException.Reason.UNKNOWN_TYPE), ExternalFiles.check(file("noext", bytes(1))))
        assertEquals(ExternalFiles.Check.Ok, ExternalFiles.check(file("main.RS", bytes(1))))
        assertEquals(ExternalFiles.Check.Refused(ExternalFileException.Reason.NOT_FOUND), ExternalFiles.check(dir.resolve("missing.txt")))
    }

    @Test
    fun sizeLimits() {
        val p = file("big.txt", ByteArray(100) { 'a'.code.toByte() })
        assertEquals(ExternalFiles.Check.Ok, ExternalFiles.check(p, warnBytes = 100, maxBytes = 200))
        assertEquals(ExternalFiles.Check.Large(100), ExternalFiles.check(p, warnBytes = 99, maxBytes = 200))
        assertEquals(ExternalFiles.Check.Refused(ExternalFileException.Reason.TOO_LARGE), ExternalFiles.check(p, warnBytes = 10, maxBytes = 99))
    }

    @Test
    fun saveLeavesNoTempFilesAndUpdatesTheStamp() {
        val p = file("a.txt", "x".toByteArray())
        val opened = ExternalFiles.read(p)
        val stamp = ExternalFiles.write(p, "longer text", opened.format)
        assertNotEquals(opened.stamp, stamp)
        assertEquals(stamp, ExternalFiles.stamp(p))
        assertEquals(listOf("a.txt"), Files.list(dir).use { s -> s.map { it.fileName.toString() }.toList() })
    }

    @Test
    fun readOnlyFilesAreMarkedAndCannotBeSaved() {
        val p = file("ro.txt", "x".toByteArray())
        p.toFile().setWritable(false)
        val opened = ExternalFiles.read(p)
        assertTrue(opened.readOnly)
        assertEquals(
            ExternalFileException.Reason.READ_ONLY,
            assertFailsWith<ExternalFileException> { ExternalFiles.write(p, "y", opened.format) }.reason,
        )
        assertEquals("x", Files.readString(p))
    }

    @Test
    fun aFileWithoutLineBreaksGetsWindowsLineEndings() {
        val opened = ExternalFiles.read(file("one.txt", "single".toByteArray()))
        assertEquals("\r\n", opened.format.lineSeparator)
        assertFalse(opened.format.bom)
    }
}
