package app.anikuta.backup.format.anikuta

import app.anikuta.core.util.system.logcat
import kotlinx.serialization.json.Json
import logcat.LogPriority
import java.io.OutputStream

/**
 * Codec for the AniKuta backup format (`.anikuta`).
 *
 * ## File format
 * ```
 * [8 bytes: magic "ANIKUTA1"] [UTF-8 JSON of AnikutaBackup]
 * ```
 * Not gzipped, not encrypted. Human-readable (after stripping the 8-byte magic).
 *
 * ## Why a magic header?
 * For reliable format auto-detection on restore. The first 8 bytes are checked
 * against [AnikutaBackup.MAGIC] before attempting JSON decode. This cleanly
 * distinguishes anikuta-format files from aniyomi-format files (which start
 * with gzip magic `0x1f 0x8b`) and from arbitrary files.
 *
 * ## Versioning
 * The [AnikutaBackup.version] field (currently 2) is embedded in the JSON.
 * `ignoreUnknownKeys = true` in the [json] config means older apps can decode
 * backups from newer apps (new fields are ignored). Adding fields with defaults
 * is backward + forward compatible.
 *
 * ## Thread safety
 * All methods are stateless and safe to call from any thread.
 */
object AnikutaCodec {

    /** Shared JSON instance — `ignoreUnknownKeys` for forward/backward compat. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false // compact for smaller files
    }

    /**
     * Serialize an [AnikutaBackup] to an output stream.
     * Format: `[MAGIC][JSON]`.
     */
    fun write(backup: AnikutaBackup, output: OutputStream) {
        val jsonStr = json.encodeToString(AnikutaBackup.serializer(), backup)
        val data = (AnikutaBackup.MAGIC + jsonStr).toByteArray(Charsets.UTF_8)
        output.write(data)
        output.flush()
        logcat(LogPriority.DEBUG) {
            "AniKuta backup written: ${backup.library.size} anime, ${backup.history.size} history, " +
                "${backup.settings.size} prefs, v${backup.version}, ${data.size} bytes"
        }
    }

    /**
     * Deserialize an [AnikutaBackup] from a byte array.
     *
     * @return the parsed backup, or `null` if the magic header doesn't match
     *   or JSON decoding fails.
     */
    fun read(bytes: ByteArray): AnikutaBackup? {
        if (bytes.size < AnikutaBackup.MAGIC.length) {
            logcat(LogPriority.WARN) { "AniKuta read: file too small (${bytes.size} bytes)" }
            return null
        }
        val header = String(bytes, 0, AnikutaBackup.MAGIC.length, Charsets.UTF_8)
        if (header != AnikutaBackup.MAGIC) {
            logcat(LogPriority.WARN) { "AniKuta read: magic mismatch (got '$header')" }
            return null
        }
        return try {
            val jsonStr = String(
                bytes,
                AnikutaBackup.MAGIC.length,
                bytes.size - AnikutaBackup.MAGIC.length,
                Charsets.UTF_8,
            )
            json.decodeFromString(AnikutaBackup.serializer(), jsonStr)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "AniKuta read: JSON decode failed" }
            null
        }
    }

    /**
     * Check whether the given bytes start with the AniKuta magic header.
     * Cheap — only reads the first 8 bytes. Used by [app.anikuta.backup.BackupFormatDetector].
     */
    fun isAnikutaFormat(bytes: ByteArray): Boolean {
        if (bytes.size < AnikutaBackup.MAGIC.length) return false
        val header = String(bytes, 0, AnikutaBackup.MAGIC.length, Charsets.UTF_8)
        return header == AnikutaBackup.MAGIC
    }
}
