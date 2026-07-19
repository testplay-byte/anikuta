package app.anikuta.backup.format.aniyomi

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Legacy aniyomi backup format (old field numbers 3/4/103).
 *
 * Aniyomi's `BackupDetector.isLegacyBackup` returns `true` when:
 *  - `isLegacy` (field 500) is `true` (or absent → defaults to `true`), AND
 *  - `backupAnimeSources` (field 103) is non-empty.
 *
 * In that case, the backup uses the OLD field numbers:
 *  - `backupAnime` at field **3** (not 501)
 *  - `backupAnimeCategories` at field **4** (not 502)
 *  - `backupAnimeSources` at field **103** (not 503)
 *  - `backupAnimeExtensionRepo` at field **107** (not 505)
 *  - `backupCustomButton` at field **109** (not 506)
 *
 * Manga fields (1, 2, 101, 104, 105, 106) are the SAME in both legacy and modern.
 *
 * This class lets us DECODE legacy aniyomi backups. We then convert to the
 * modern [AniyomiBackup] via [toModern] for uniform processing in the importer.
 *
 * Reference: `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/Backup.kt`
 * → `LegacyBackup` class (read-only, aniyomi never writes this format anymore).
 */
@Serializable
data class LegacyAniyomiBackup(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<BackupCategory> = emptyList(), // manga
    @ProtoNumber(3) val backupAnime: List<BackupAnime> = emptyList(),
    @ProtoNumber(4) val backupAnimeCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(101) val backupSources: List<BackupSource> = emptyList(), // manga
    @ProtoNumber(103) val backupAnimeSources: List<BackupAnimeSource> = emptyList(),
    @ProtoNumber(104) val backupPreferences: List<app.anikuta.backup.model.BackupPreference> = emptyList(),
    @ProtoNumber(105) val backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(107) val backupAnimeExtensionRepo: List<BackupExtensionRepos> = emptyList(),
    @ProtoNumber(108) val backupMangaExtensionRepo: List<BackupExtensionRepos> = emptyList(),
    @ProtoNumber(109) val backupCustomButton: List<BackupCustomButtons> = emptyList(),
)

/**
 * A mini-schema for detecting whether a backup is legacy.
 *
 * Only reads 2 fields: `isLegacy` (500) and `backupAnimeSources` (103).
 * Matches aniyomi's `BackupDetector`.
 *
 * - If `isLegacy == true` AND `backupAnimeSources` is non-empty → legacy.
 * - Otherwise → modern (decode as [AniyomiBackup]).
 *
 * Reference: `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupDetector.kt`
 */
@Serializable
data class AniyomiBackupDetector(
    @ProtoNumber(103) val backupAnimeSources: List<DetectAnimeSource> = emptyList(),
    @ProtoNumber(500) val isLegacy: Boolean = true,
)

@Serializable
data class DetectAnimeSource(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val sourceId: Long = 0,
)

/**
 * Returns `true` if the given protobuf bytes represent a legacy aniyomi backup.
 *
 * Mirrors aniyomi's `BackupDetector.isLegacyBackup(bytes)`.
 */
fun isLegacyAniyomiBackup(bytes: ByteArray): Boolean {
    return try {
        val detector = kotlinx.serialization.protobuf.ProtoBuf
            .decodeFromByteArray(AniyomiBackupDetector.serializer(), bytes)
        detector.isLegacy && detector.backupAnimeSources.isNotEmpty()
    } catch (_: kotlinx.serialization.SerializationException) {
        false
    } catch (_: Exception) {
        false
    }
}

/**
 * Convert a legacy backup to the modern representation for uniform processing.
 *
 * All fields map 1:1 — only the proto field numbers differed.
 */
fun LegacyAniyomiBackup.toModern(): AniyomiBackup = AniyomiBackup(
    backupManga = backupManga,
    backupCategories = backupCategories,
    backupSources = backupSources,
    backupPreferences = backupPreferences,
    backupSourcePreferences = backupSourcePreferences,
    backupMangaExtensionRepo = backupMangaExtensionRepo,
    isLegacy = true, // preserve the flag for logging/debugging
    backupAnime = backupAnime,
    backupAnimeCategories = backupAnimeCategories,
    backupAnimeSources = backupAnimeSources,
    backupExtensions = emptyList(), // legacy field 106 not in our schema
    backupAnimeExtensionRepo = backupAnimeExtensionRepo,
    backupCustomButton = backupCustomButton,
)
