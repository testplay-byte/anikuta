# ANI-KUTA — Backup & Restore Plan

> **Status:** DRAFT — awaiting user confirmation before implementation.
> **Branch:** `feature/backup-restore` (created from `main` @ `751df01`).
> **Author:** Main agent (analysis from Tasks 2-a/2-b/2-c/2-d, see `/home/z/my-project/worklog.md`).
> **Last updated:** 2026-07-18.
>
> This plan supersedes the stub backup code documented in
> `DOCS/CURRENT-STATE.md` and the (unrelated) `DOCS/PLAN/INJEKT-BACKUP-PLAN.md`
> (which is about DI migration, NOT data backup).

---

## 0. How to read this plan

| Section | What it tells you |
|---------|-------------------|
| §1 | Executive summary — the 30-second version |
| §2 | What exists today (verified) and what's broken |
| §3 | Goals & requirements (from your direction) |
| §4 | Proposed modular architecture |
| §5 | The AniList-ID linking strategy (4-tier) |
| §6 | How unlinked anime are handled (options + recommendation — **needs your decision**) |
| §7 | Restore summary / preview UX |
| §8 | Shared `PreferenceValue` type (fixes the settings-restore bug for both formats) |
| §9 | Aniyomi modern-schema adoption (proto field numbers) |
| §10 | Implementation phases (CI-verified between each) |
| §11 | File-by-file change list |
| §12 | Risks & mitigations |
| §13 | Open questions for you (please answer before I implement) |

---

## 1. Executive summary

ANI-KUTA already has a **partial** backup system (`app/.../backup/BackupManager.kt`, 565 lines), but it has serious gaps:

- **AniKuta-format backup CREATE** — works today. ✅
- **AniKuta-format RESTORE** — has **3 bugs**: all user preferences are silently dropped, category assignments are dropped, playback states are dropped. ⚠️
- **Aniyomi-format backup CREATE** — produces a file, but with **wrong protobuf field numbers** (legacy `3/4/103` instead of modern `500/501/502/503`), so real aniyomi sees an **empty anime library** when importing it. ❌
- **Aniyomi-format RESTORE** — a **pure stub** that counts entries but writes nothing. Plus the proto-number bug means it can't read modern aniyomi backups anyway. ❌
- **Restore preview/summary** — none. Restore happens blind; user only sees counts afterward (and those counts are wrong for aniyomi format). ❌
- **AniList-ID linking on aniyomi restore** — not implemented. ❌

This plan proposes a **modular rewrite** that:
1. Fixes all 3 anikuta-restore bugs.
2. Adopts aniyomi's **modern protobuf schema** verbatim, so backups are interoperable in both directions.
3. Implements **real aniyomi-format restore** with a 4-tier AniList-ID linking strategy (tracking → cache → fuzzy match → manual-queue).
4. Adds a **restore preview** so you see what's in a backup before committing.
5. Splits the 565-line `BackupManager` into focused, documented modules.
6. Handles manga data by **ignoring it** (anikuta is anime-only — no manga tables exist).

The work is split into **7 phases**, each pushed to the branch with a CI build between phases (per `WORKING-RULES.md §D-bis`).

---

## 2. Current state (verified — see worklog Tasks 2-c and 2-d for full detail)

### 2.1 What exists

| File | Lines | Status |
|------|------:|--------|
| `app/.../backup/BackupManager.kt` | 565 | Works for anikuta CREATE; 3 bugs in anikuta RESTORE; aniyomi CREATE wrong-format; aniyomi RESTORE stub |
| `app/.../backup/AnikutaBackup.kt` | 165 | Clean, versioned data model — **keep & extend** |
| `app/.../backup/AniyomiBackup.kt` | 112 | Wrong proto numbers + type mismatch — **rewrite** |
| `app/.../backup/BackupFormatDetector.kt` | 168 | Anikuta path correct; aniyomi path decodes into wrong schema |
| `app/.../ui/settings/BackupSettingsScreen.kt` | 215 | Functional but minimal — no preview, no options |
| `domain/.../backup/service/BackupPreferences.kt` | 18 | Dead code (verbatim aniyomi copy) |
| `domain/.../backup/service/PreferenceValues.kt` | 9 | Dead code; `FLAG_CHAPTERS` manga-leftover |

### 2.2 The 3 anikuta-restore bugs (confirmed by reading every line)

| Bug | What happens | Impact |
|-----|--------------|--------|
| **#1 — Settings not restored** | `collectAnikutaBackup()` gathers ALL SharedPreferences via `prefs.getAll()` into `backup.settings: Map<String,String>`, but `restoreAnikutaBackup()` never iterates it. | Every preference lost on restore: notification modes, quiet hours, download quality/server/audio priority, player prefs, library display prefs, subtitle style, etc. |
| **#2 — Category assignments not restored** | `categoryStore.restoreAssignments(...)` exists but is never called. | Categories exist after restore but are all empty (anime not assigned to them). |
| **#3 — Playback states not restored** | `backup.playbackStates` collected but never written back. | Resume-after-restore memory lost — user must re-resolve video source on every resume. |

Plus 3 minor bugs: sub/dub `lastUpdated` overwritten, extension-link key `split(":")` breaks on URLs with `:`, cover `medium` URL lost in round-trip.

### 2.3 The aniyomi-format problem (confirmed by cross-referencing aniyomi's real `REFERENCE/` source)

Aniyomi's **modern** `Backup` schema puts anime at proto field **501** (with `isLegacy=false` at field 500). Anikuta's current `AniyomiBackup.kt` puts anime at field **3** (legacy position) and never writes `isLegacy`. Aniyomi's detector reads `isLegacy` as `true` (the default) → decodes as legacy → looks for anime at field 3 → but also requires `backupAnimeSources` at field 103 to be non-empty → anikuta writes it empty → detector returns false → **aniyomi decodes as modern, ignores field 3, sees an empty anime library**.

Additionally, aniyomi's `BackupPreference.value` is a **sealed `PreferenceValue`** (Int/Long/Float/String/Boolean/StringSet), but anikuta's is a bare `String` — wire-format incompatible.

**Net result**: anikuta's aniyomi-format backups are unreadable by aniyomi, and anikuta can't read real aniyomi backups. Both directions are broken.

### 2.4 Where the real user data lives (critical for backup design)

Anikuta is **AniList-first**. The SQLDelight tables (`animes`, `episodes`, `animehistory`, etc.) exist in the schema but are **scaffold** — wired in DI but consumed by nothing at runtime (verified by grep). All real user data lives in **SharedPreferences-backed stores**:

| Store | Pref key | What it holds |
|-------|----------|---------------|
| `LibraryStore` | `pref_library_saved_anime` | Saved AniList anime (JSON map, keyed by anilistId) |
| `WatchProgressStore` | `pref_watch_progress_map` | Resume positions (keyed `"$anilistId:$episodeUrl"`) |
| `PlaybackStateStore` | `pref_playback_state_map` | Last video URL + server + tracks (keyed `"$anilistId:$episodeUrl"`) |
| `CategoryStore` | `pref_library_categories` + `pref_library_category_assignments` | User categories + anime→category |
| `SubDubStore` | `pref_sub_dub_cache` | Sub/dub availability per anime |
| `ExtensionLinkStore` | `pref_extension_anilist_links` | `"$sourceId:$animeUrl"` → anilistId cache |
| `ReleaseTrackingStore` | `pref_release_tracking_map` | Tracked-anime map (24 fields per anime — notification settings, learned release-time offsets, source mapping) |
| `DownloadStore` | `active_anime_downloads` | In-flight download queue (completed downloads auto-removed after 20s; truth is on filesystem) |
| `ext_match_<anilistId>` / `ext_sanime_url_<anilistId>` | (individual prefs) | DetailViewModel's source cache |

The download **video files** live on the SAF filesystem (`<base>/downloads/<source>/<anime>/<episode>/video.mkv`), NOT in SharedPreferences. The backup will be **metadata-only** (aniyomi-style) — it records *which* episodes were downloaded, not the video bytes. On restore, the user can re-download.

---

## 3. Goals & requirements (from your direction)

1. **Backup in both formats** — anikuta (`.anikuta`, JSON) AND aniyomi (`.tachibk`/`.json.gz`, gzip+protobuf).
2. **Restore in both formats** — auto-detect format on restore.
3. **Ignore manga data** — anikuta is anime-only. Aniyomi backups may contain manga; we skip it.
4. **Properly handle anime content** — back it up, restore it, and **link AniList IDs** so AniList-based features (metadata, tracking, notifications) work after restore.
5. **Handle unlinked anime** — those that can't be auto-linked to AniList are "handled as needed" (see §6 — **needs your decision**).
6. **Show a restore summary** — user sees what's in the backup (counts, date, missing sources, unlinked anime) before committing.
7. **Modular architecture** — small, focused, documented files; one responsibility each.
8. **Properly documented** — this plan + inline code comments + updated `TECHNICAL-OVERVIEW.md` after implementation.

Plus the standing project rules (`WORKING-RULES.md`): understand before acting, minimize changes, CI-verified builds, never merge without your confirmation.

---

## 4. Proposed modular architecture

The current `BackupManager.kt` (565 lines) does everything. It will be split into focused modules. **Package layout:**

```
app/src/main/java/app/anikuta/backup/
├── BackupManager.kt                  # Thin facade — delegates to the modules below.
│                                     # Public API: createBackup, restoreBackup, peekBackup.
│
├── model/
│   ├── PreferenceValue.kt            # Shared sealed type (Int/Long/Float/String/Bool/StringSet).
│   │                                 # Used by BOTH anikuta and aniyomi formats. Mirrors aniyomi's
│   │                                 # exact declaration order for wire compat.
│   ├── BackupSummary.kt              # Pre-restore preview data (format, version, date, counts,
│   │                                 # missing sources, unlinked anime, warnings).
│   └── RestoreResult.kt              # Post-restore result (per-section counts + error list).
│
├── prefs/
│   ├── PreferenceCollector.kt        # SharedPreferences → Map<String, PreferenceValue>
│   │                                 # (excludes __APP_STATE_*, __PRIVATE_* unless opted in,
│   │                                 #  and excludes the dedicated getObject keys to avoid
│   │                                 #  double-restore).
│   └── PreferenceRestorer.kt         # Map<String, PreferenceValue> → SharedPreferences, with
│                                     # type-guard (only restore keys that exist on this device
│                                     # with matching type — aniyomi's pattern).
│
├── format/
│   ├── anikuta/
│   │   ├── AnikutaBackup.kt          # Data model (exists — extend with typed PreferenceValue).
│   │   ├── AnikutaCodec.kt           # Magic header + JSON read/write (extracted from BackupFormatDetector).
│   │   ├── AnikutaCollector.kt       # AppState → AnikutaBackup (all 9 sections).
│   │   └── AnikutaRestorer.kt        # AnikutaBackup → AppState (fixes bugs #1/#2/#3).
│   │
│   ├── aniyomi/
│   │   ├── AniyomiBackupModels.kt    # REWRITE: modern proto schema (fields 500-506).
│   │   │                             # Verbatim copy of aniyomi's @ProtoNumber annotations.
│   │   ├── AniyomiCodec.kt           # gzip+protobuf read/write + legacy-decode dispatch
│   │   │                             # (BackupDetector.isLegacyBackup pattern).
│   │   ├── AniyomiExporter.kt        # AnikutaBackup → AniyomiBackup (with AniList tracking,
│   │   │                             # source/url from ExtensionLinkStore where known).
│   │   └── AniyomiImporter.kt        # AniyomiBackup → AnikutaBackup (with 4-tier AniList
│   │                                 # linking — see §5).
│   │
│   └── BackupFormatDetector.kt       # Existing — refactored to delegate to the codecs.
│
├── link/
│   └── AniListLinker.kt              # The 4-tier linking strategy (see §5).
│                                     # Given a BackupAnime (source, url, title), returns
│                                     # Linked(anilistId) | Unlinked(reason).
│
├── validator/
│   └── BackupValidator.kt            # peekBackup(uri): BackupSummary — decode without restoring,
│                                     # detect missing sources, count anime/history/categories,
│                                     # identify unlinked anime (for aniyomi format).
│
└── (UI stays in ui/settings/)
    # BackupSettingsScreen.kt — extended with preview dialog + restore-options.
    # New: RestorePreviewDialog.kt — shows BackupSummary, lets user confirm/cancel.
    # New: RestoreOptionsSheet.kt — checkboxes (include categories, history, settings, etc.)
```

**Why this split:**
- Each file has **one responsibility** (Core Rule 2 — modular complexity).
- Format-specific code is isolated — adding a third format later is trivial.
- The `link/` package isolates the AniList-linking logic (the hardest part) so it's testable and replaceable.
- The `validator/` package enables the preview UX without coupling to restore.
- The facade `BackupManager` stays thin — easy to reason about the public API.

---

## 5. The AniList-ID linking strategy (4-tier)

This is the core challenge for aniyomi-format restore. Aniyomi anime are keyed by `(source, url)` — aniyomi-internal. Anikuta anime are keyed by `anilistId`. We need to bridge them.

`AniListLinker.link(backupAnime) → LinkResult` tries these in order:

### Tier 1 — Tracker data (highest confidence)
Aniyomi backups include `BackupAnimeTracking` per anime. If a tracking entry has `syncId == 2` (AniList), its `mediaId` **IS** the AniList ID. Use directly. This is the cleanest path — aniyomi users who track on AniList will link 100% here.

### Tier 2 — ExtensionLinkStore cache
If no AniList tracking, check anikuta's `ExtensionLinkStore` (`pref_extension_anilist_links`, keyed `"$sourceId:$animeUrl"`). If the source+url was previously linked on this device, reuse the cached anilistId. Fast, offline.

### Tier 3 — Fuzzy title match
If no cache, run a fuzzy title search via `AniyomiSourceBridge` / `AniListRepository.searchAnime(title)`. Match by title similarity (+ year if available). This is slow (network) and may need user confirmation for ambiguous matches — so it runs **after** Tiers 1-2, and ambiguous results fall through to Tier 4.

### Tier 4 — Manual queue (unlinked)
If all tiers fail, the anime is **Unlinked**. It is still imported (see §6) but flagged for manual linking. The user can link it later from the detail page (via the existing `SourceLinkingScreen` flow).

```
LinkResult = sealed class {
    data class Linked(val anilistId: Int, val tier: LinkTier) : LinkResult()
    data class Unlinked(val reason: UnlinkReason, val backupAnime: BackupAnime) : LinkResult()
}
UnlinkReason = NO_TRACKER | NO_CACHE_MATCH | FUZZY_NO_MATCH | FUZZY_AMBIGUOUS
```

**For anikuta-format restore**: no linking needed — `BackupLibraryAnime.id` IS the AniList ID already.

**For aniyomi-format CREATE (export)**: we always emit a `BackupAnimeTracking(syncId=2, mediaId=<anilistId>, title=...)` for every anime, so aniyomi (or a future anikuta re-import) can use Tier 1.

---

## 6. How unlinked anime are handled — **needs your decision**

When an aniyomi backup contains anime that can't be auto-linked to AniList (all 4 tiers fail), we have three options. I recommend **Option B**, but this is your call.

### Option A — Skip with report (simplest)
Unlinked anime are **not imported**. The restore summary shows "N anime skipped — couldn't link to AniList. Install matching extensions or link manually, then restore again."
- **Pro:** No new architecture. Anikuta stays purely AniList-first.
- **Con:** User loses those anime from the backup. Must re-restore after fixing extensions.

### Option B — Import as source-based "pending link" entries (recommended)
Unlinked anime are imported into a **new lightweight store** (`PendingLinkStore`) keyed by `(sourceId, animeUrl)`, holding title + thumbnail + source name. They appear in the library with a "🔗 Link to AniList" badge. Tapping the badge opens `SourceLinkingScreen` (existing flow). History/watch-progress is preserved keyed by `(sourceId, episodeUrl)` until linked, then migrated to the anilistId key.
- **Pro:** No data loss. User can link at their own pace. Mirrors aniyomi's stub-source philosophy, adapted to anikuta's AniList-first model.
- **Con:** Adds a new store + a new library section + migration logic when a link is confirmed. More code.

### Option C — Import as AniList-stub with synthetic ID
Unlinked anime get a synthetic negative anilistId (e.g. `-sourceId * 1000000 - hash(url)`), imported into `LibraryStore` as a minimal AniListAnime (title + thumbnail only, no real AniList metadata). They show in the library but metadata/notifications/tracking don't work until manually re-linked (which replaces the synthetic ID with the real one).
- **Pro:** No new store — reuses LibraryStore. UI unchanged.
- **Con:** Synthetic IDs are hacky. AniList-dependent code (notifications, tracking) must handle "fake" IDs. Risk of ID collisions. Ugly.

**My recommendation: Option B.** It's the most correct architecturally and gives the best UX, even though it's more code. But if you want to ship faster, Option A is a reasonable v1 (we can always add Option B later).

➡️ **Please tell me A, B, or C before I start Phase 4 (aniyomi restore).**

---

## 7. Restore summary / preview UX

Today, restore is blind — you pick a file, it restores, you see counts after. You want a summary before committing. Here's the flow:

### 7.1 The preview flow
```
User taps "Restore backup"
  → SAF file picker (OpenDocument, */*)
  → user picks a file
  → BackupValidator.peekBackup(uri) runs (fast — decode only, no writes)
  → RestorePreviewDialog shows:
      ┌─────────────────────────────────────────────┐
      │ Backup preview                              │
      │                                             │
      │ Format: AniKuta (.anikuta)                  │
      │ Created: 2026-07-15 14:32                   │
      │ App version: 0.1.0                          │
      │                                             │
      │ Contains:                                   │
      │   • 47 anime in library                     │
      │   • 312 history entries                     │
      │   • 5 categories                            │
      │   • 40 tracked anime (notifications)        │
      │   • 28 sub/dub cache entries                │
      │   • 12 extension links                      │
      │   • 89 playback states                      │
      │   • 142 preferences                         │
      │                                             │
      │ ⚠ Warnings:                                 │
      │   • 3 anime could not be auto-linked to     │
      │     AniList (will be imported as pending)   │
      │   • Source "Gogoanime" not installed        │
      │                                             │
      │ Restore options:                            │
      │   [✓] Library        [✓] History            │
      │   [✓] Categories     [✓] Notifications      │
      │   [✓] Preferences    [✓] Download queue     │
      │                                             │
      │            [Cancel]  [Restore]              │
      └─────────────────────────────────────────────┘
  → user toggles options, taps Restore
  → restore runs (with progress bar)
  → post-restore result dialog (per-section counts + any errors)
```

### 7.2 What the validator checks
- **Format detection** — anikuta vs aniyomi vs unknown (error).
- **Decodability** — can we parse it? (catch corrupt files early).
- **Content counts** — per-section counts (anime, history, categories, etc.).
- **Missing sources** (aniyomi format) — which extension sources in the backup aren't installed on this device. (Mirrors aniyomi's `BackupFileValidator`.)
- **Unlinked anime** (aniyomi format) — how many couldn't auto-link to AniList (runs Tiers 1-2 only in preview; Tier 3 fuzzy match is too slow for preview, runs during actual restore).
- **Schema version** — warn if newer than supported.

### 7.3 Restore options
User can selectively enable/disable sections before restoring (like aniyomi's `RestoreOptions`). Default: all enabled. This lets a user restore just preferences, or just library, etc.

---

## 8. Shared `PreferenceValue` type (fixes Bug #1 for both formats)

This is the key insight that fixes the biggest bug AND enables aniyomi interop with one design.

### 8.1 The problem
Anikuta's current `AnikutaBackup.settings: Map<String, String>` loses type info — `getAll()` returns `Map<String, *>` (Boolean/String/Int/Long/Float/Set<String>), but everything is `.toString()`-ed. On restore, there's no way to know if `"true"` was a Boolean or a String. And `Set<String>` becomes `"[a, b, c]"` (Kotlin Set.toString) — not round-trippable. This is why settings are currently dropped.

### 8.2 The solution
Adopt aniyomi's `PreferenceValue` sealed class **verbatim** (same declaration order for wire compat):

```kotlin
@Serializable
sealed class PreferenceValue
@Serializable data class IntPreferenceValue(val value: Int) : PreferenceValue()
@Serializable data class LongPreferenceValue(val value: Long) : PreferenceValue()
@Serializable data class FloatPreferenceValue(val value: Float) : PreferenceValue()
@Serializable data class StringPreferenceValue(val value: String) : PreferenceValue()
@Serializable data class BooleanPreferenceValue(val value: Boolean) : PreferenceValue()
@Serializable data class StringSetPreferenceValue(val value: Set<String>) : PreferenceValue()
```

- **Anikuta format**: change `settings: Map<String, String>` → `settings: List<BackupPreference>` where `BackupPreference(key: String, value: PreferenceValue)`. (Bump `AnikutaBackup.version` to 2. The codec uses `ignoreUnknownKeys = true`, so v1 files still decode — the old `settings` field is ignored, preferences are lost on v1→v2 restore, which is acceptable since v1 never worked anyway.)
- **Aniyomi format**: use the same `PreferenceValue` — it's wire-compatible with aniyomi's by construction.

### 8.3 Exclusion rules (which prefs are NOT backed up)
- `__APP_STATE_*` keys — always excluded (device-specific internal state, e.g. `storage_dir` which is a SAF URI).
- `__PRIVATE_*` keys — excluded unless user opts in (tokens, credentials). Default: excluded.
- Dedicated `getObject` keys (already restored by dedicated paths): `pref_release_tracking_map`, `pref_sub_dub_cache`, `pref_library_saved_anime`, `pref_extension_anilist_links`, `pref_playback_state_map`, `pref_watch_progress_map`, `pref_library_categories`, `pref_library_category_assignments`, `active_anime_downloads`, `search_recent_terms`. Excluded from settings-restore to avoid double-restore.

### 8.4 Type-guard on restore (aniyomi's pattern)
On restore, a preference is only written if the key **already exists** on this device with a matching type. This prevents a backup from a newer app version from writing unknown keys that crash an older version. Cross-device restore of prefs is best-effort by design. (Same as aniyomi.)

---

## 9. Aniyomi modern-schema adoption

`AniyomiBackupModels.kt` will be rewritten to mirror aniyomi's modern `Backup` schema **verbatim**. Key points (full field list in worklog Task 2-d §12):

### 9.1 Root `Backup`
- `@ProtoNumber(500) isLegacy: Boolean = true` — **must explicitly write `false`** in the exporter.
- `@ProtoNumber(501) backupAnime: List<BackupAnime>`
- `@ProtoNumber(502) backupAnimeCategories: List<BackupCategory>`
- `@ProtoNumber(503) backupAnimeSources: List<BackupAnimeSource>` — **must be populated** (detector requires non-empty for legacy path; modern path uses it for source-name mapping).
- `@ProtoNumber(104) backupPreferences: List<BackupPreference>`
- Manga fields (1, 2, 101, 106) — always `emptyList()` (anikuta is anime-only).
- Fields 504/505/506 (extensions/APKs, ext-repos, custom-buttons) — optional; skip for v1.

### 9.2 Legacy decode support
Add a `LegacyBackup` class (legacy field numbers 3/4/103) + `BackupDetector.isLegacyBackup(bytes)` check. If a real aniyomi backup is legacy format, decode via `LegacyBackup` then convert to modern `Backup`. This lets anikuta read both old and new aniyomi backups.

### 9.3 `BackupAnime` — all 24 fields
Full proto numbers in worklog Task 2-d §1.3. Season fields (502-507) included for schema fidelity even though anikuta doesn't use seasons — they'll just be defaults.

### 9.4 `BackupAnimeTracking` — the AniList link
`@ProtoNumber(1) syncId: Int` (2 = AniList), `@ProtoNumber(100) mediaId: Long` (the AniList ID). This is what Tier 1 linking reads. On export, anikuta emits this for every anime.

### 9.5 Codec
`gzip(protobuf(Backup))`, extension `.tachibk` (aniyomi convention) or `.json.gz` (keep existing UI label). Detect by first 2 bytes: `0x1f 0x8b` = gzip → aniyomi; `"ANIKUTA1"` = anikuta.

---

## 10. Implementation phases (CI-verified between each)

Per `WORKING-RULES.md §D-bis`: create PR immediately after Phase 1, push each phase, **wait for CI green** before next phase. No merging without your confirmation.

| Phase | What | Files | CI? |
|-------|------|-------|-----|
| **0** | Plan doc (this file) + create branch + open draft PR | `DOCS/PLAN/BACKUP-RESTORE-PLAN.md` | No (doc only) |
| **1** | Shared `PreferenceValue` + `PreferenceCollector` + `PreferenceRestorer` + modular package skeleton (empty facades that delegate to existing `BackupManager`) | `backup/model/`, `backup/prefs/` | **Yes — must compile** |
| **2** | Fix anikuta-restore bugs #1/#2/#3 + minor bugs. Migrate `AnikutaBackup.settings` to typed `List<BackupPreference>` (v2). Refactor `BackupManager` to delegate to `AnikutaCollector`/`AnikutaRestorer` | `backup/format/anikuta/` | **Yes** |
| **3** | Add `BackupValidator` + `BackupSummary` + `RestorePreviewDialog` + `RestoreOptionsSheet`. Wire into `BackupSettingsScreen` | `backup/validator/`, `ui/settings/` | **Yes** |
| **4** | Rewrite `AniyomiBackupModels.kt` (modern proto schema) + `AniyomiCodec.kt` (gzip+protobuf + legacy decode). Rewrite `AniyomiExporter.kt` (emit tracking, populate sources) | `backup/format/aniyomi/` | **Yes** |
| **5** | Implement `AniListLinker.kt` (4-tier) + `AniyomiImporter.kt` (real restore with linking + unlinked handling per your §6 decision) | `backup/link/`, `backup/format/aniyomi/AniyomiImporter.kt` | **Yes** |
| **6** | Unlinked-anime handling per your decision (Option A = nothing extra; B = `PendingLinkStore` + library badge; C = synthetic IDs) | varies | **Yes** |
| **7** | Polish: error log (`anikuta_restore_error.txt`), progress notification, docs update (`TECHNICAL-OVERVIEW.md`, `CURRENT-STATE.md`), dead-code cleanup (`domain/backup/service/`) | various | **Yes** |

**Each phase = one commit (or a few) pushed to `feature/backup-restore`, PR stays open, CI runs on each push.** I report back after each phase with CI status. You test the APK on-device before I merge anything.

---

## 11. File-by-file change list

### New files
| Path | Phase | Purpose |
|------|-------|---------|
| `DOCS/PLAN/BACKUP-RESTORE-PLAN.md` | 0 | This plan |
| `app/.../backup/model/PreferenceValue.kt` | 1 | Shared sealed type |
| `app/.../backup/model/BackupSummary.kt` | 3 | Preview data |
| `app/.../backup/model/RestoreResult.kt` | 2 | Extended result |
| `app/.../backup/prefs/PreferenceCollector.kt` | 1 | prefs → PreferenceValue map |
| `app/.../backup/prefs/PreferenceRestorer.kt` | 1 | PreferenceValue map → prefs |
| `app/.../backup/format/anikuta/AnikutaCodec.kt` | 2 | magic+JSON read/write |
| `app/.../backup/format/anikuta/AnikutaCollector.kt` | 2 | state → AnikutaBackup |
| `app/.../backup/format/anikuta/AnikutaRestorer.kt` | 2 | AnikutaBackup → state |
| `app/.../backup/format/aniyomi/AniyomiBackupModels.kt` | 4 | modern proto schema |
| `app/.../backup/format/aniyomi/AniyomiCodec.kt` | 4 | gzip+protobuf + legacy |
| `app/.../backup/format/aniyomi/AniyomiExporter.kt` | 4 | anikuta → aniyomi |
| `app/.../backup/format/aniyomi/AniyomiImporter.kt` | 5 | aniyomi → anikuta (with linking) |
| `app/.../backup/link/AniListLinker.kt` | 5 | 4-tier linking |
| `app/.../backup/validator/BackupValidator.kt` | 3 | peek → BackupSummary |
| `app/.../ui/settings/RestorePreviewDialog.kt` | 3 | preview UI |
| `app/.../ui/settings/RestoreOptionsSheet.kt` | 3 | restore-options checkboxes |
| (Option B only) `app/.../data/cache/PendingLinkStore.kt` | 6 | unlinked-anime store |

### Modified files
| Path | Phase | Change |
|------|-------|--------|
| `app/.../backup/BackupManager.kt` | 2 | Thin facade — delegate to modules |
| `app/.../backup/AnikutaBackup.kt` | 2 | `settings: Map<String,String>` → `List<BackupPreference>`; bump version to 2; add `downloadedEpisodes` field |
| `app/.../backup/AniyomiBackup.kt` | 4 | **Delete** (replaced by `AniyomiBackupModels.kt`) |
| `app/.../backup/BackupFormatDetector.kt` | 2/4 | Refactor to delegate to codecs |
| `app/.../ui/settings/BackupSettingsScreen.kt` | 3 | Add preview dialog + options sheet |
| `app/.../di/AppModule.kt` | 1+ | Register new modules |
| `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md` | 7 | Update §backup |
| `DOCS/CURRENT-STATE.md` | 7 | Update backup status |

### Deleted files (dead code)
| Path | Phase | Why |
|------|-------|-----|
| `domain/.../backup/service/BackupPreferences.kt` | 7 | Dead code (verbatim aniyomi copy, never referenced) |
| `domain/.../backup/service/PreferenceValues.kt` | 7 | Dead code; `FLAG_CHAPTERS` manga-leftover |

---

## 12. Risks & mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Aniyomi proto schema copied wrong → wire-incompatible | Medium | High (aniyomi can't read our backups) | Copy verbatim from `REFERENCE/app/.../backup/models/`; verify by round-tripping a real aniyomi backup in Phase 4 CI |
| `PreferenceValue` sealed-class discriminator order wrong | Medium | High (prefs won't decode) | Copy exact declaration order from aniyomi; add a unit test that encodes+decodes each type |
| AniList fuzzy match is slow / rate-limited | High | Medium (restore hangs) | Run Tier 3 in a background coroutine with a per-anime timeout; fall through to Tier 4 on timeout |
| Breaking existing anikuta-format backups (v1) | Low | Low (v1 never worked — settings were dropped anyway) | `ignoreUnknownKeys=true` means v1 files still decode; just no prefs restored from v1. Document this. |
| SQLDelight schema drift | None | — | We don't touch SQLDelight — all data is in SharedPreferences (verified). |
| MPV/extension contract break | None | — | Backup doesn't touch `:source-api` or player. |
| CI fails on a phase | Medium | Delays | Fix before next phase per `WORKING-RULES §D-bis`. No batching. |
| `encodeDefaults=false` bites us (aniyomi default `isLegacy=true`) | High if missed | High (aniyomi sees empty library) | **Explicitly set `isLegacy=false`** in exporter — documented in §9.1. This is the #1 gotcha. |

---

## 13. Open questions for you (please answer before I implement)

These are the decisions I need from you. I'll wait for your answers before starting Phase 1.

1. **Unlinked-anime handling (§6):** Option A (skip), B (pending-link store — recommended), or C (synthetic IDs)?

2. **Aniyomi-format restore scope:** Do you want full aniyomi-format restore (Phases 4-6, ~3-4 days), or is aniyomi-format CREATE-only (export for aniyomi users) + anikuta-format restore enough for v1? (You said "restore in both formats" — so I'm assuming full — but confirming.)

3. **Fuzzy title match during restore (Tier 3):** Should it run automatically (best-effort, may link wrong), or prompt the user for each ambiguous match? Auto is faster but can mis-link; prompt is safer but tedious for large backups. (My recommendation: auto-link with confidence threshold ≥ 0.9, else queue for manual.)

4. **Auto-backup:** The existing `BackupPreferences.backupInterval()` (dead code) suggests aniyomi-style auto-backup was planned. Do you want me to wire it up in this feature (Phase 7+), or defer to a separate task?

5. **Backup file extension for aniyomi format:** Keep `.json.gz` (current UI label) or switch to `.tachibk` (aniyomi convention, better for file-association)? (My recommendation: `.tachibk` for aniyomi format, `.anikuta` for anikuta format — clear distinction.)

6. **Should the backup include the list of "downloaded episodes" (metadata, not files)?** Currently there's no persistent index of completed downloads (they're auto-removed from the queue after 20s; truth is on filesystem). To back up "which episodes were downloaded", I'd either (a) scan the filesystem at backup time, or (b) add a new persistent index. Do you want this in v1, or defer? (My recommendation: defer to a follow-up — get the core backup/restore solid first.)

7. **Anything else?** Are there specific behaviors or UI details you want that I haven't covered?

---

## Appendix A — AniList ID mapping summary

| Direction | Source of AniList ID |
|-----------|---------------------|
| Anikuta backup → Anikuta restore | `BackupLibraryAnime.id` (direct) |
| Anikuta backup → Aniyomi export | `BackupLibraryAnime.id` → `BackupAnimeTracking(syncId=2, mediaId=id)` |
| Aniyomi backup → Anikuta restore | Tier 1: tracking `mediaId`; Tier 2: ExtensionLinkStore; Tier 3: fuzzy; Tier 4: manual |
| Aniyomi backup → Aniyomi export (passthrough) | tracking preserved as-is |

## Appendix B — Reference docs read during planning
- `/home/z/my-project/worklog.md` — Tasks 1, 2-a, 2-b, 2-c, 2-d (full research reports)
- `DOCS/REFERENCE-DOCS/SUBSYSTEMS/BACKUP-RESTORE.md` — aniyomi backup subsystem doc
- `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/data/backup/` — aniyomi source (read-only)
- `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/Backup.kt` — authoritative proto schema
- All existing anikuta backup files (`app/.../backup/*.kt`)

---

_This plan is a living document. I'll update it as phases complete and decisions are made. Once you confirm the open questions in §13, I'll begin Phase 1._
