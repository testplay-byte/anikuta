package app.anikuta.backup

import app.anikuta.backup.format.anikuta.AnikutaCodec
import app.anikuta.backup.format.aniyomi.AniyomiCodec
import app.anikuta.core.util.system.logcat
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream

/**
 * Detects the format of a backup file: AniKuta (`.anikuta`) or Aniyomi (`.tachibk`).
 *
 * ## Phase 4 refactor
 * The AniKuta read/write logic lives in [AnikutaCodec].
 * The Aniyomi read/write logic lives in [AniyomiCodec] (with legacy-decode dispatch).
 * This class is now just a **format detector** — it identifies the format from
 * bytes but delegates actual read/write to the codecs.
 *
 * ## Auto-detection on restore
 *  - Check first 8 bytes for the AniKuta magic header `"ANIKUTA1"` → AniKuta format.
 *  - Otherwise try aniyomi (gzip magic `0x1f 0x8b`, then raw protobuf) → Aniyomi format.
 *  - If both fail → UNKNOWN.
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
            if (AniyomiCodec.isAniyomiFormat(bytes)) {
                Format.ANIYOMI
            } else {
                logcat(LogPriority.WARN) { "detect: not AniKuta, not Aniyomi" }
                Format.UNKNOWN
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

    // ---- Convenience read/write methods (delegate to codecs) ----
    // These are kept for backward compat with any code that called
    // BackupFormatDetector directly. New code should use the codecs directly.

    /** Delegate to [AnikutaCodec.write]. */
    fun writeAnikuta(backup: app.anikuta.backup.format.anikuta.AnikutaBackup, output: OutputStream) {
        AnikutaCodec.write(backup, output)
    }

    /** Delegate to [AnikutaCodec.read]. */
    fun readAnikuta(bytes: ByteArray): app.anikuta.backup.format.anikuta.AnikutaBackup? {
        return AnikutaCodec.read(bytes)
    }

    /** Delegate to [AnikutaCodec.read]. */
    fun readAnikuta(input: InputStream): app.anikuta.backup.format.anikuta.AnikutaBackup? {
        return try { AnikutaCodec.read(input.readBytes()) } catch (e: Exception) { null }
    }

    /** Delegate to [AniyomiCodec.write]. */
    fun writeAniyomi(backup: app.anikuta.backup.format.aniyomi.AniyomiBackup, output: OutputStream) {
        AniyomiCodec.write(backup, output)
    }

    /** Delegate to [AniyomiCodec.read]. */
    fun readAniyomi(bytes: ByteArray): app.anikuta.backup.format.aniyomi.AniyomiBackup? {
        return AniyomiCodec.read(bytes)
    }

    /** Delegate to [AniyomiCodec.read]. */
    fun readAniyomi(input: InputStream): app.anikuta.backup.format.aniyomi.AniyomiBackup? {
        return try { AniyomiCodec.read(input.readBytes()) } catch (e: Exception) { null }
    }
}
