package app.anikuta.backup.format.aniyomi

import app.anikuta.backup.model.BackupPreference
import app.anikuta.core.util.system.logcat
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Codec for the Aniyomi backup format (`.tachibk`).
 *
 * ## File format
 * ```
 * gzip(protobuf(AniyomiBackup))
 * ```
 * (Optionally raw protobuf without gzip — we handle both on read, always
 * gzip on write.)
 *
 * ## Detection: gzip magic bytes
 * The first 2 bytes of a gzipped file are `0x1f 0x8b`. We check these to
 * decide whether to gunzip before protobuf-decoding. (No app-level magic
 * header — aniyomi doesn't use one.)
 *
 * ## Legacy vs modern dispatch
 * After decompressing, we check [isLegacyAniyomiBackup] to decide whether to
 * decode as [LegacyAniyomiBackup] (old field numbers 3/4/103) or [AniyomiBackup]
 * (modern 500-506). Legacy backups are converted to modern via [LegacyAniyomiBackup.toModern].
 *
 * ## `encodeDefaults` note
 * The [protoBuf] instance uses `encodeDefaults = false` (the library default).
 * This means the [AniyomiBackup.isLegacy] field (default `true`) is NOT written
 * to the wire unless we explicitly set it to `false`. The [AniyomiBackup.modern]
 * factory does this — always use it for export.
 *
 * ## Thread safety
 * All methods are stateless and safe to call from any thread.
 *
 * Reference: `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupDecoder.kt`
 */
object AniyomiCodec {

    /** Shared protobuf instance. `encodeDefaults = false` matches aniyomi. */
    val protoBuf: ProtoBuf = ProtoBuf

    /**
     * Serialize an [AniyomiBackup] to an output stream (gzip + protobuf).
     *
     * The backup should be created via [AniyomiBackup.modern] to ensure
     * `isLegacy = false` is written.
     */
    fun write(backup: AniyomiBackup, output: OutputStream) {
        val protoBytes = protoBuf.encodeToByteArray(AniyomiBackup.serializer(), backup)
        GZIPOutputStream(output).use { it.write(protoBytes) }
        logcat(LogPriority.DEBUG) {
            "Aniyomi backup written: ${backup.backupAnime.size} anime, " +
                "${backup.backupAnimeSources.size} sources, isLegacy=${backup.isLegacy}, " +
                "${protoBytes.size} proto bytes (gzipped)"
        }
    }

    /**
     * Deserialize an aniyomi-format backup from a byte array.
     *
     * Handles:
     *  - gzip-compressed protobuf (normal case — first 2 bytes `0x1f 0x8b`).
     *  - raw protobuf (uncompressed — rare, but aniyomi's decoder tries it).
     *  - legacy field numbers (3/4/103) → converted to modern via [LegacyAniyomiBackup.toModern].
     *
     * @return the parsed backup (always [AniyomiBackup], possibly converted from
     *   legacy), or `null` if decoding fails.
     */
    fun read(bytes: ByteArray): AniyomiBackup? {
        // Step 1: decompress (if gzipped)
        val protoBytes = try {
            if (bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0x1f && (bytes[1].toInt() and 0xFF) == 0x8b) {
                GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
            } else {
                bytes // raw protobuf
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Aniyomi read: decompress failed" }
            return null
        }

        // Step 2: check legacy vs modern
        val isLegacy = isLegacyAniyomiBackup(protoBytes)
        logcat(LogPriority.DEBUG) { "Aniyomi read: isLegacy=$isLegacy, ${protoBytes.size} proto bytes" }

        return try {
            if (isLegacy) {
                val legacy = protoBuf.decodeFromByteArray(LegacyAniyomiBackup.serializer(), protoBytes)
                logcat(LogPriority.DEBUG) {
                    "Aniyomi read (legacy): ${legacy.backupAnime.size} anime, ${legacy.backupAnimeSources.size} sources"
                }
                legacy.toModern()
            } else {
                val modern = protoBuf.decodeFromByteArray(AniyomiBackup.serializer(), protoBytes)
                logcat(LogPriority.DEBUG) {
                    "Aniyomi read (modern): ${modern.backupAnime.size} anime, ${modern.backupAnimeSources.size} sources"
                }
                modern
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Aniyomi read: protobuf decode failed" }
            null
        }
    }

    /**
     * Check whether the given bytes are a gzipped aniyomi backup.
     * Cheap — only checks the first 2 bytes for gzip magic.
     */
    fun isAniyomiFormat(bytes: ByteArray): Boolean {
        if (bytes.size < 2) return false
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        // gzip magic 0x1f 0x8b, OR raw protobuf (heuristic: try decode)
        if (b0 == 0x1f && b1 == 0x8b) return true
        // Try raw protobuf decode as a last resort
        return try {
            protoBuf.decodeFromByteArray(AniyomiBackup.serializer(), bytes)
            true
        } catch (_: Exception) {
            false
        }
    }
}
