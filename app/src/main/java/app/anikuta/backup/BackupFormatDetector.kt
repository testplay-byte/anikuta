package app.anikuta.backup

import app.anikuta.backup.format.anikuta.AnikutaCodec
import app.anikuta.core.util.system.logcat
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Detects, serializes, and deserializes backup files in two formats:
 *
 * 1. **AniKuta format** (`.anikuta`): JSON with a magic header `"ANIKUTA1"`.
 *    Delegates to [AnikutaCodec] for read/write.
 *
 * 2. **Aniyomi format** (`.tachibk` / `.json.gz`): Protocol Buffers, gzipped.
 *    Uses [AniyomiBackup] (legacy schema — Phase 4 will modernize to 500-506).
 *
 * ## Auto-detection on restore
 *  - Read all bytes from the stream ONCE (ContentResolver streams don't support
 *    mark/reset, so we can't peek and rewind).
 *  - Check first 8 bytes for the AniKuta magic header → AniKuta format.
 *  - Otherwise try protobuf decode (gzip first, then raw) → Aniyomi format.
 *  - If both fail → UNKNOWN.
 *
 * ## Phase 2 refactor
 * The AniKuta read/write logic was extracted to [AnikutaCodec]. This class
 * still owns aniyomi-format read/write until Phase 4 introduces [AniyomiCodec].
 * The [detect] / [readAnikuta] / [readAniyomi] methods now delegate where
 * appropriate.
 */
object BackupFormatDetector {

    private const val TAG = "BackupFormat"

    enum class Format { ANIKUTA, ANIYOMI, UNKNOWN }

    /**
     * Detect the format of a backup file by reading all bytes ONCE.
     *
     * IMPORTANT: ContentResolver.openInputStream() returns streams that do NOT
     * support mark()/reset(). We must read all bytes in one pass, then work
     * with the byte array.
     */
    fun detect(bytes: ByteArray): Format {
        return try {
            if (bytes.size < 8) return Format.UNKNOWN

            // Check for AniKuta magic header (first 8 bytes)
            if (AnikutaCodec.isAnikutaFormat(bytes)) {
                return Format.ANIKUTA
            }

            // Not AniKuta — try Aniyomi (protobuf, possibly gzipped)
            try {
                val decompressed = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
                ProtoBuf.decodeFromByteArray(AniyomiBackup.serializer(), decompressed)
                Format.ANIYOMI
            } catch (e: Exception) {
                // Maybe not gzipped — try raw protobuf
                try {
                    ProtoBuf.decodeFromByteArray(AniyomiBackup.serializer(), bytes)
                    Format.ANIYOMI
                } catch (e2: Exception) {
                    logcat(LogPriority.WARN) {
                        "detect: not AniKuta, not Aniyomi (gzip: ${e.message}, raw: ${e2.message})"
                    }
                    Format.UNKNOWN
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "detect failed: ${e.message}" }
            Format.UNKNOWN
        }
    }

    /**
     * Convenience method: detect format from an InputStream.
     * Reads all bytes into memory, then calls detect(ByteArray).
     */
    fun detect(input: InputStream): Format {
        return try {
            detect(input.readBytes())
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "detect(InputStream) failed: ${e.message}" }
            Format.UNKNOWN
        }
    }

    // ---- AniKuta format (.anikuta) ----
    // Delegates to AnikutaCodec (Phase 2 refactor).

    /**
     * Serialize an AniKuta backup to an output stream (JSON with magic header).
     * Delegates to [AnikutaCodec.write].
     */
    fun writeAnikuta(backup: app.anikuta.backup.format.anikuta.AnikutaBackup, output: OutputStream) {
        AnikutaCodec.write(backup, output)
    }

    /**
     * Deserialize an AniKuta backup from a byte array.
     * Delegates to [AnikutaCodec.read].
     */
    fun readAnikuta(bytes: ByteArray): app.anikuta.backup.format.anikuta.AnikutaBackup? {
        return AnikutaCodec.read(bytes)
    }

    /**
     * Convenience method: deserialize from an InputStream.
     */
    fun readAnikuta(input: InputStream): app.anikuta.backup.format.anikuta.AnikutaBackup? {
        return try {
            AnikutaCodec.read(input.readBytes())
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "readAnikuta(InputStream) failed" }
            null
        }
    }

    // ---- Aniyomi format (.tachibk — protobuf+gzip) ----
    // Phase 4 will extract this into AniyomiCodec.

    /**
     * Serialize an aniyomi-compatible backup (protobuf + gzip).
     * Phase 4 will replace with [AniyomiCodec] using the modern 500-506 schema.
     */
    fun writeAniyomi(backup: AniyomiBackup, output: OutputStream) {
        val protoBytes = ProtoBuf.encodeToByteArray(AniyomiBackup.serializer(), backup)
        GZIPOutputStream(output).use { it.write(protoBytes) }
    }

    /**
     * Deserialize an aniyomi-format backup from a byte array.
     * Tries gzip first, then raw protobuf.
     * Phase 4 will replace with [AniyomiCodec] using the modern 500-506 schema.
     */
    fun readAniyomi(bytes: ByteArray): AniyomiBackup? {
        return try {
            try {
                val decompressed = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
                ProtoBuf.decodeFromByteArray(AniyomiBackup.serializer(), decompressed)
            } catch (e: Exception) {
                ProtoBuf.decodeFromByteArray(AniyomiBackup.serializer(), bytes)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "readAniyomi failed" }
            null
        }
    }

    /**
     * Convenience method: deserialize from an InputStream.
     */
    fun readAniyomi(input: InputStream): AniyomiBackup? {
        return try {
            readAniyomi(input.readBytes())
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "readAniyomi(InputStream) failed" }
            null
        }
    }
}
