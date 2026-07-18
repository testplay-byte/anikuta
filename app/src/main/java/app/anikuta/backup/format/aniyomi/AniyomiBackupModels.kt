package app.anikuta.backup.format.aniyomi

import app.anikuta.backup.model.BackupPreference
import app.anikuta.backup.model.PreferenceValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Aniyomi-compatible backup models — **modern** protobuf schema.
 *
 * This file mirrors aniyomi's real backup schema verbatim (from
 * `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/`), so
 * backups produced by ANI-KUTA can be imported by aniyomi and vice-versa.
 *
 * ## The 500-offset block (critical)
 * Aniyomi's modern `Backup` puts anime-side fields at proto numbers 500-506
 * (not the legacy 3/4/103). The detection flag is `isLegacy` at field 500:
 *  - Modern backups write `isLegacy = false` → aniyomi decodes as modern.
 *  - Legacy backups omit field 500 → `isLegacy` defaults to `true` → legacy decode.
 *
 * **We MUST explicitly set `isLegacy = false`** when exporting, because the
 * data-class default is `true` and kotlinx-serialization-protobuf skips
 * default values when `encodeDefaults = false` (the library default). If we
 * forget, aniyomi's detector reads `isLegacy` as `true` → legacy decode →
 * looks for anime at field 3 → sees an empty library.
 *
 * ## PreferenceValue wire compat
 * The [PreferenceValue] sealed class (in `backup/model/`) uses the EXACT same
 * declaration order as aniyomi's, so kotlinx-serialization-protobuf assigns
 * the same wire discriminators. This makes [BackupPreference] wire-compatible
 * in both directions.
 *
 * ## Manga
 * Anikuta is anime-only. [Backup.backupManga] is always `emptyList()`. When
 * reading an aniyomi backup that contains manga, we simply ignore the manga
 * list (the [AniyomiImporter] skips it).
 *
 * Reference: `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/Backup.kt`
 */

/**
 * Root backup container — aniyomi modern format.
 *
 * Wire format: `gzip(protobuf(Backup))`.
 */
@Serializable
data class AniyomiBackup(
    // ---- Manga block (inherited from Tachiyomi, always empty for anikuta) ----
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<BackupCategory> = emptyList(), // manga categories
    @ProtoNumber(101) val backupSources: List<BackupSource> = emptyList(), // manga sources
    @ProtoNumber(104) val backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) val backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) val backupMangaExtensionRepo: List<BackupExtensionRepos> = emptyList(),

    // ---- Anime block (500-offset — the modern fields) ----
    /** CRITICAL: must be `false` for modern decode. See file KDoc. */
    @ProtoNumber(500) val isLegacy: Boolean = true,
    @ProtoNumber(501) val backupAnime: List<BackupAnime> = emptyList(),
    @ProtoNumber(502) val backupAnimeCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(503) val backupAnimeSources: List<BackupAnimeSource> = emptyList(),
    @ProtoNumber(504) val backupExtensions: List<BackupExtension> = emptyList(),
    @ProtoNumber(505) val backupAnimeExtensionRepo: List<BackupExtensionRepos> = emptyList(),
    @ProtoNumber(506) val backupCustomButton: List<BackupCustomButtons> = emptyList(),
) {
    companion object {
        /**
         * Create a modern backup (for export). Sets `isLegacy = false`.
         *
         * Usage: `AniyomiBackup.modern(backupAnime = ..., ...)`
         */
        fun modern(
            backupAnime: List<BackupAnime> = emptyList(),
            backupAnimeCategories: List<BackupCategory> = emptyList(),
            backupAnimeSources: List<BackupAnimeSource> = emptyList(),
            backupPreferences: List<BackupPreference> = emptyList(),
        ) = AniyomiBackup(
            isLegacy = false,
            backupAnime = backupAnime,
            backupAnimeCategories = backupAnimeCategories,
            backupAnimeSources = backupAnimeSources,
            backupPreferences = backupPreferences,
        )
    }
}

// ---- Anime ----

@Serializable
data class BackupAnime(
    @ProtoNumber(1) val source: Long = 0,
    @ProtoNumber(2) val url: String = "",
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val artist: String? = null,
    @ProtoNumber(5) val author: String? = null,
    @ProtoNumber(6) val description: String? = null,
    @ProtoNumber(7) val genre: List<String> = emptyList(),
    @ProtoNumber(8) val status: Int = 0,
    @ProtoNumber(9) val thumbnailUrl: String? = null,
    @ProtoNumber(13) val dateAdded: Long = 0,
    @ProtoNumber(16) val episodes: List<BackupEpisode> = emptyList(),
    /** Category ORDERS (not IDs) — matches [BackupCategory.order]. */
    @ProtoNumber(17) val categories: List<Long> = emptyList(),
    @ProtoNumber(18) val tracking: List<BackupAnimeTracking> = emptyList(),
    @ProtoNumber(100) val favorite: Boolean = true,
    @ProtoNumber(101) val episodeFlags: Int = 0,
    @ProtoNumber(103) val viewer_flags: Int = 0,
    @ProtoNumber(104) val history: List<BackupAnimeHistory> = emptyList(),
    @ProtoNumber(105) val updateStrategy: Int = 0, // 0=ALWAYS_UPDATE, 1=ONLY_FETCH_ONCE
    @ProtoNumber(106) val lastModifiedAt: Long = 0,
    @ProtoNumber(107) val favoriteModifiedAt: Long? = null,
    @ProtoNumber(109) val version: Long = 0,
    // 500-block season fields — included for schema fidelity; anikuta doesn't use seasons
    @ProtoNumber(500) val backgroundUrl: String? = null,
    // 501 is "broken, do not use" per aniyomi source
    @ProtoNumber(502) val parentId: Long? = null,
    @ProtoNumber(503) val id: Long? = null,
    @ProtoNumber(504) val seasonFlags: Long = 0,
    @ProtoNumber(505) val seasonNumber: Double = -1.0,
    @ProtoNumber(506) val seasonSourceOrder: Long = 0,
    @ProtoNumber(507) val fetchType: Int = 1, // 0=Seasons, 1=Episodes (default Episodes)
)

@Serializable
data class BackupEpisode(
    @ProtoNumber(1) val url: String = "",
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val scanlator: String? = null,
    @ProtoNumber(4) val seen: Boolean = false,
    @ProtoNumber(5) val bookmark: Boolean = false,
    @ProtoNumber(6) val lastSecondSeen: Long = 0,
    @ProtoNumber(7) val dateFetch: Long = 0,
    @ProtoNumber(8) val dateUpload: Long = 0,
    @ProtoNumber(9) val episodeNumber: Float = 0f,
    @ProtoNumber(10) val sourceOrder: Long = 0,
    @ProtoNumber(11) val lastModifiedAt: Long = 0,
    @ProtoNumber(12) val version: Long = 0,
    @ProtoNumber(16) val totalSeconds: Long = 0, // out-of-order proto number (matches aniyomi)
    @ProtoNumber(501) val fillermark: Boolean = false,
    @ProtoNumber(502) val summary: String? = null,
    @ProtoNumber(503) val previewUrl: String? = null,
)

@Serializable
data class BackupAnimeHistory(
    @ProtoNumber(1) val url: String = "",
    @ProtoNumber(2) val lastRead: Long = 0,
    @ProtoNumber(3) val readDuration: Long = 0, // dead for anime (aniyomi doesn't populate it)
)

@Serializable
data class BackupAnimeTracking(
    @ProtoNumber(1) val syncId: Int = 0, // AniList=2, MAL=1, Shikimori=3, ...
    @ProtoNumber(2) val libraryId: Long = 0,
    @ProtoNumber(3) val mediaIdInt: Int = 0, // @Deprecated legacy Int media ID
    @ProtoNumber(4) val trackingUrl: String = "",
    @ProtoNumber(5) val title: String = "",
    @ProtoNumber(6) val lastEpisodeSeen: Float = 0f,
    @ProtoNumber(7) val totalEpisodes: Int = 0,
    @ProtoNumber(8) val score: Float = 0f,
    @ProtoNumber(9) val status: Int = 0,
    @ProtoNumber(10) val startedWatchingDate: Long = 0,
    @ProtoNumber(11) val finishedWatchingDate: Long = 0,
    @ProtoNumber(12) val private: Boolean = false,
    @ProtoNumber(100) val mediaId: Long = 0, // modern 64-bit media ID
) {
    /** The effective tracker media ID (modern Long, falling back to legacy Int). */
    val effectiveMediaId: Long get() = if (mediaId != 0L) mediaId else mediaIdInt.toLong()
}

// ---- Categories ----

@Serializable
data class BackupCategory(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val order: Long = 0,
    @ProtoNumber(3) val id: Long = 0, // used by PreferenceRestorer for category-id remapping
    @ProtoNumber(100) val flags: Long = 0,
)

// ---- Sources ----

@Serializable
data class BackupSource(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val sourceId: Long = 0,
)

@Serializable
data class BackupAnimeSource(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val sourceId: Long = 0,
)

// ---- Preferences (uses the shared PreferenceValue sealed type) ----

/**
 * Per-source preferences (keyed `"source_<id>"`).
 * Maps to aniyomi's `BackupSourcePreferences`.
 */
@Serializable
data class BackupSourcePreferences(
    @ProtoNumber(1) val sourceKey: String = "",
    @ProtoNumber(2) val prefs: List<BackupPreference> = emptyList(),
)

// ---- Extensions ----

/** Bundled APK (off by default — anikuta doesn't redistribute APKs). */
@Serializable
data class BackupExtension(
    @ProtoNumber(1) val pkgName: String = "",
    @ProtoNumber(2) val apk: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupExtension) return false
        return pkgName == other.pkgName && apk.contentEquals(other.apk)
    }
    override fun hashCode(): Int = 31 * pkgName.hashCode() + apk.contentHashCode()
}

@Serializable
data class BackupExtensionRepos(
    @ProtoNumber(1) val baseUrl: String = "",
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val shortName: String? = null,
    @ProtoNumber(4) val website: String = "",
    @ProtoNumber(5) val signingKeyFingerprint: String = "",
)

@Serializable
data class BackupCustomButtons(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val isFavorite: Boolean = false,
    @ProtoNumber(3) val sortIndex: Long = 0,
    @ProtoNumber(4) val content: String = "",
    @ProtoNumber(5) val longPressContent: String = "",
    @ProtoNumber(6) val onStartup: String = "",
)

// ---- Manga (for reading aniyomi backups — anikuta ignores manga on restore) ----

/**
 * Manga model — included only so we can DECODE aniyomi backups that contain
 * manga. Anikuta is anime-only, so the [AniyomiImporter] skips manga entries.
 *
 * This is a minimal representation; not all aniyomi manga fields are here
 * (we don't need them since we ignore manga).
 */
@Serializable
data class BackupManga(
    @ProtoNumber(1) val source: Long = 0,
    @ProtoNumber(2) val url: String = "",
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(7) val genre: List<String> = emptyList(),
    @ProtoNumber(9) val thumbnailUrl: String? = null,
    @ProtoNumber(100) val favorite: Boolean = false,
)
