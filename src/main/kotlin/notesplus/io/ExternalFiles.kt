package notesplus.io

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** How an External File is stored on disk, so saving writes it back the same way. */
data class TextFormat(val charset: Charset, val bom: Boolean, val lineSeparator: String) {
    val label: String
        get() {
            val enc = charset.name().removePrefix("x-") + if (bom) " BOM" else ""
            val eol = when (lineSeparator) {
                "\r\n" -> "CRLF"
                "\n" -> "LF"
                else -> "CR"
            }
            return "$enc · $eol"
        }
}

/** Last-modified time and size, to notice when another program changed the file. */
data class DiskStamp(val modified: Long, val size: Long)

/** [text] uses "\n" line breaks; [format] restores the original ones on save. */
data class OpenedFile(val path: Path, val text: String, val format: TextFormat, val stamp: DiskStamp, val readOnly: Boolean)

class ExternalFileException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { BINARY_FORMAT, UNKNOWN_TYPE, TOO_LARGE, NOT_FOUND, BINARY_CONTENT, ENCODING, UNENCODABLE, READ_ONLY, IO }
}

object ExternalFiles {
    /** Plain-text formats, edited like Notepad++. */
    val SUPPORTED = setOf(
        "txt", "rtf", "acs", "c", "cpp", "h", "md", "markdown", "log", "tsv", "eml",
        "yaml", "yml", "json", "xml", "toml", "html", "htm", "css",
        "py", "js", "ts", "java", "php", "rb", "go", "rs",
    )

    /** Binary/container formats that a text editor would corrupt. */
    val UNSUPPORTED = setOf("doc", "docx", "odt", "pages", "pdf", "wpd")

    const val WARN_BYTES = 5L * 1024 * 1024
    const val MAX_BYTES = 50L * 1024 * 1024

    sealed interface Check {
        data object Ok : Check
        data class Large(val bytes: Long) : Check
        data class Refused(val reason: ExternalFileException.Reason) : Check
    }

    fun extension(path: Path) = path.fileName.toString().substringAfterLast('.', "").lowercase()

    /** Decides before reading whether a file may be opened. */
    fun check(path: Path, warnBytes: Long = WARN_BYTES, maxBytes: Long = MAX_BYTES): Check {
        val ext = extension(path)
        if (ext in UNSUPPORTED) return Check.Refused(ExternalFileException.Reason.BINARY_FORMAT)
        if (ext !in SUPPORTED) return Check.Refused(ExternalFileException.Reason.UNKNOWN_TYPE)
        if (!Files.isRegularFile(path)) return Check.Refused(ExternalFileException.Reason.NOT_FOUND)
        val size = Files.size(path)
        return when {
            size > maxBytes -> Check.Refused(ExternalFileException.Reason.TOO_LARGE)
            size > warnBytes -> Check.Large(size)
            else -> Check.Ok
        }
    }

    private val WINDOWS_874: Charset = Charset.forName("x-windows-874")
    private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

    /**
     * Legacy encodings tried (strictly) when a file isn't valid UTF-8: Thai and Western,
     * with Windows' own "ANSI" code page first, like Notepad++.
     */
    fun defaultFallbacks(): List<Charset> {
        val ansi = runCatching { Charset.forName(System.getProperty("native.encoding")) }.getOrNull()
        val legacy = listOf(WINDOWS_874, WINDOWS_1252)
        return if (ansi in legacy) listOf(ansi!!) + (legacy - ansi) else legacy
    }

    fun read(path: Path, fallbacks: List<Charset> = defaultFallbacks()): OpenedFile {
        val bytes = try {
            Files.readAllBytes(path)
        } catch (e: NoSuchFileException) {
            throw ExternalFileException(ExternalFileException.Reason.NOT_FOUND, e)
        } catch (e: IOException) {
            throw ExternalFileException(ExternalFileException.Reason.IO, e)
        }
        val (bomCharset, bomLength) = when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8 to 3
            bytes.startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE to 2
            bytes.startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE to 2
            else -> null to 0
        }
        // Without a BOM, NUL bytes mean this is not a text file.
        if (bomCharset == null && bytes.any { it == 0.toByte() }) {
            throw ExternalFileException(ExternalFileException.Reason.BINARY_CONTENT)
        }
        val candidates = if (bomCharset != null) listOf(bomCharset) else listOf(Charsets.UTF_8) + fallbacks
        val (charset, raw) = candidates.firstNotNullOfOrNull { cs ->
            decodeStrict(cs, bytes, bomLength)?.let { cs to it }
        } ?: throw ExternalFileException(ExternalFileException.Reason.ENCODING) // Refuse rather than corrupt it on save.
        val format = TextFormat(charset, bomLength > 0, detectLineSeparator(raw))
        return OpenedFile(path, normalize(raw), format, stamp(path), readOnly = !Files.isWritable(path))
    }

    /** Writes [text] back in [format], atomically: a crash never leaves a half-written file. */
    fun write(path: Path, text: String, format: TextFormat): DiskStamp {
        if (Files.exists(path) && !Files.isWritable(path)) throw ExternalFileException(ExternalFileException.Reason.READ_ONLY)
        // e.g. an emoji typed into a Windows-874 file: refuse instead of writing "?".
        if (!format.charset.newEncoder().canEncode(text)) throw ExternalFileException(ExternalFileException.Reason.UNENCODABLE)
        val body = if (format.lineSeparator == "\n") text else text.replace("\n", format.lineSeparator)
        val bom = if (!format.bom) ByteArray(0) else when (format.charset) {
            Charsets.UTF_8 -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            Charsets.UTF_16LE -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
            else -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        }
        val dir = path.toAbsolutePath().parent
        val tmp = Files.createTempFile(dir, ".notesplus-", ".tmp")
        try {
            Files.write(tmp, bom + body.toByteArray(format.charset))
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            Files.deleteIfExists(tmp)
            throw ExternalFileException(ExternalFileException.Reason.IO, e)
        }
        return stamp(path)
    }

    fun stamp(path: Path): DiskStamp = try {
        DiskStamp(Files.getLastModifiedTime(path).toMillis(), Files.size(path))
    } catch (e: IOException) {
        DiskStamp(-1, -1)
    }

    private fun decodeStrict(charset: Charset, bytes: ByteArray, offset: Int): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
            .toString()
    } catch (e: CharacterCodingException) {
        null
    }

    private fun detectLineSeparator(text: String): String {
        val i = text.indexOfFirst { it == '\r' || it == '\n' }
        return when {
            i < 0 -> "\r\n" // No line breaks yet: use the Windows default.
            text[i] == '\n' -> "\n"
            i + 1 < text.length && text[i + 1] == '\n' -> "\r\n"
            else -> "\r"
        }
    }

    private fun normalize(text: String) = text.replace("\r\n", "\n").replace('\r', '\n')

    private fun ByteArray.startsWith(vararg prefix: Int) =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }
}
