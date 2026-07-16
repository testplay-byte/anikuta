package app.anikuta.backup

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Detects, serializes, and deserializes backup files in two formats:
 *
 * 1. **AniKuta format** (.anikuta): JSON with a magic header "ANIKUTA1".
 *    Human-readable, contains all our data (library, history, searches, settings, etc.)
 *
 * 2. **Aniyomi format** (.json.gz or .protobuf.gz): Protocol Buffers, gzipped.
 *    Compatible with aniyomi's backup format — can be imported/exported by aniyomi.
 *
 * Auto-detection on restore:
 *  - Read the first 8 bytes. If they match "ANIKUTA1" → our format.
 *  - Otherwise → try protobuf decode (aniyomi format).
 *  - If both fail → error.
 */
object BackupFormatDetector {

    private const val TAG = "BackupFormat"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    enum class Format { ANIKUTA, ANIYOMI, UNKNOWN }

    /**
     * Detect the format of a backup file by reading its first bytes.
     */
    fun detect(input: InputStream): Format {
        return try {
            input.mark(8)
            val header = ByteArray(8)
            val read = input.read(header)
            input.reset()

            if (read >= 8 && String(header, 0, 8) == AnikutaBackup.MAGIC) {
                Format.ANIKUTA
            } else {
                // Try protobuf (aniyomi format) — if it decodes, it's aniyomi
                try {
                    // Read the full stream, try protobuf decode
                    input.reset()
                    val bytes = input.readBytes()
                    // Try decoding as aniyomi protobuf
                    val gzipped = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
                    ProtoBuf.decodeFromByteArray(AniyomiBackup.serializer(), gzipped)
                    Format.ANIYOMI
                } catch (e: Exception) {
                    // Maybe not gzipped — try raw protobuf
                    try {
                        ProtoBuf.decodeFromByteArray(AniyomiBackup.serializer(), bytes)
                        Format.ANIYOMI
                    } catch (e2: Exception) {
                        Format.UNKNOWN
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "detect failed: ${e.message}")
            Format.UNKNOWN
        }
    }

    // ---- AniKuta format (.anikuta) ----

    /**
     * Serialize an AniKuta backup to an output stream (JSON with magic header).
     */
    fun writeAnikuta(backup: AnikutaBackup, output: OutputStream) {
        val jsonStr = json.encodeToString(AnikutaBackup.serializer(), backup)
        val data = (AnikutaBackup.MAGIC + jsonStr).toByteArray(Charsets.UTF_8)
        output.write(data)
        output.flush()
    }

    /**
     * Deserialize an AniKuta backup from an input stream.
     */
    fun readAnikuta(input: InputStream): AnikutaBackup? {
        return try {
            val bytes = input.readBytes()
            val header = String(bytes, 0, 8, Charsets.UTF_8)
            if (header != AnikutaBackup.MAGIC) return null
            val jsonStr = String(bytes, 8, bytes.size - 8, Charsets.UTF_8)
            json.decodeFromString(AnikutaBackup.serializer(), jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "readAnikuta failed", e)
            null
        }
    }

    // ---- Aniyomi format (.json.gz — actually protobuf+gzip) ----

    /**
     * Serialize an aniyomi-compatible backup (protobuf + gzip).
     */
    fun writeAniyomi(backup: AniyomiBackup, output: OutputStream) {
        val protoBytes = ProtoBuf.encodeToByteArray(AniyomiBackup.serializer(), backup)
        GZIPOutputStream(output).use { it.write(protoBytes) }
    }

    /**
     * Deserialize an aniyomi-format backup (try gzip first, then raw protobuf).
     */
    fun readAniyomi(input: InputStream): AniyomiBackup? {
        return try {
            val bytes = input.readBytes()
            // Try gzip first
            try {
                val decompressed = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
                ProtoBuf.decodeFromByteArray(AniyomiBackup.serializer(), decompressed)
            } catch (e: Exception) {
                // Maybe not gzipped — try raw protobuf
                ProtoBuf.decodeFromByteArray(AniyomiBackup.serializer(), bytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "readAniyomi failed", e)
            null
        }
    }
}
