# app/anikuta/backup/ — Backup & Restore System

> Modular backup/restore for ANI-KUTA. Supports two formats:
> - **AniKuta format** (`.anikuta`) — JSON with magic header, our own complete format.
> - **Aniyomi format** (`.tachibk`) — gzip+protobuf, wire-compatible with aniyomi.
>
> See `DOCS/PLAN/BACKUP-RESTORE-PLAN.md` for the full design.

## Package structure

```
backup/
├── BackupManager.kt          # Public facade — createBackup / restoreBackup / peekBackup.
│                             # Delegates to the modules below.
│
├── model/                    # Pure data models (no logic, no Android deps except PreferenceStore)
│   ├── PreferenceValue.kt    # Sealed type: Int/Long/Float/String/Boolean/StringSet.
│   │                         # Wire-compatible with aniyomi's PreferenceValue (declaration order!).
│   ├── BackupPreference.kt   # (in PreferenceValue.kt) key + typed value.
│   ├── BackupSummary.kt      # Pre-restore preview data (Step 2 of restore flow).
│   └── RestoreResult.kt      # Post-restore result (Step 4) + UnlinkedAnime model.
│
├── prefs/                    # Preference collect/restore (shared by both formats)
│   ├── PreferenceCollector.kt  # PreferenceStore → List<BackupPreference> (with exclusions)
│   └── PreferenceRestorer.kt  # List<BackupPreference> → PreferenceStore (type-guarded)
│
├── format/
│   ├── anikuta/              # AniKuta format (.anikuta — JSON + magic header)
│   │   ├── AnikutaBackup.kt    # (moved from backup/) data model, versioned
│   │   ├── AnikutaCodec.kt     # read/write: magic header + JSON
│   │   ├── AnikutaCollector.kt # AppState → AnikutaBackup (all 9 sections)
│   │   └── AnikutaRestorer.kt  # AnikutaBackup → AppState (fixes bugs #1/#2/#3)
│   │
│   ├── aniyomi/              # Aniyomi format (.tachibk — gzip+protobuf, modern schema 500-506)
│   │   ├── AniyomiBackupModels.kt  # REWRITE: modern proto schema (verbatim from aniyomi)
│   │   ├── AniyomiCodec.kt         # gzip+protobuf read/write + legacy-decode dispatch
│   │   ├── AniyomiExporter.kt      # AnikutaBackup → AniyomiBackup (emit tracking, sources)
│   │   └── AniyomiImporter.kt      # AniyomiBackup → AnikutaBackup (with AniList linking)
│   │
│   └── BackupFormatDetector.kt  # Auto-detect format from bytes (refactored to delegate to codecs)
│
├── link/                     # AniList-ID linking (for aniyomi-format restore)
│   └── AniListLinker.kt      # 4-tier: tracking → cache → fuzzy → manual-queue
│
└── validator/
    └── BackupValidator.kt    # peekBackup(uri): BackupSummary — decode without restoring
```

## The restore flow (4 steps)

```
Step 1: DECODE      → BackupValidator.peekBackup(uri) → BackupSummary
Step 2: SUMMARY     → RestorePreviewDialog (user reviews + confirms + picks options)
Step 3: RESTORE     → BackupManager.restoreBackup(uri, options) → live progress
Step 4: REVIEW      → RestoreResult + UnlinkedAnime list → user resolves each
```

## Key design principles

1. **One responsibility per file** — each file does one thing (Core Rule 2).
2. **Format isolation** — anikuta and aniyomi code are in separate packages; adding
   a third format later is trivial.
3. **Shared `PreferenceValue`** — the sealed type fixes the settings-restore bug
   (Bug #1) AND enables aniyomi pref interop with one design.
4. **Type-guard on restore** — prefs are only restored if the key exists on this
   device with matching type (aniyomi's pattern; prevents cross-version crashes).
5. **No data loss** — unlinked anime are never skipped; they're queued for the
   post-restore review (Step 4).
6. **Logging everywhere** — every collect/restore/link operation logs to logcat
   (tag prefix `Backup*`) for debugging.

## Data sources (where user data actually lives)

Anikuta is AniList-first. The SQLDelight tables are scaffold (wired in DI but
unused at runtime). All real user data is in SharedPreferences-backed stores:

| Store | Pref key | Backed up by |
|-------|----------|-------------|
| LibraryStore | `pref_library_saved_anime` | AnikutaCollector (dedicated) |
| WatchProgressStore | `pref_watch_progress_map` | AnikutaCollector (dedicated) |
| PlaybackStateStore | `pref_playback_state_map` | AnikutaCollector (dedicated) |
| CategoryStore | `pref_library_categories` + `..._assignments` | AnikutaCollector (dedicated) |
| SubDubStore | `pref_sub_dub_cache` | AnikutaCollector (dedicated) |
| ExtensionLinkStore | `pref_extension_anilist_links` | AnikutaCollector (dedicated) |
| ReleaseTrackingStore | `pref_release_tracking_map` | AnikutaCollector (dedicated) |
| DownloadStore | `active_anime_downloads` | NOT backed up (in-flight queue; truth is on filesystem) |
| All other prefs | (individual keys) | PreferenceCollector (generic, type-safe) |

These dedicated-store keys are EXCLUDED from the generic PreferenceCollector
(see `PreferenceCollector.DEDICATED_STORE_KEYS`) to avoid double-restore.
