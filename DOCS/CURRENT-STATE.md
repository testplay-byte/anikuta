# ANI-KUTA — Current State

> **The single source of truth for the project's current status.**
> Read this FIRST. If anything here conflicts with another doc, **this file wins**.
> Update this file after every merge to `main` (or any significant status change).
>
> Last updated: 2026-07-16.

---

## Snapshot

| Field | Value |
|-------|-------|
| Active branch | `main` (HEAD `ca644ad`, 2026-07-16) |
| Other branches | `live-preview-dashboard` (temporary, not for feature work) |
| App version | `0.1.0` (versionCode 1) |
| Application ID | `app.anikuta` |
| Latest verified build | `27053e1` (Phase 7 floating bottom nav, all features working) |
| Build status | ✅ Green on GitHub Actions (`assembleDebug`, arm64-v8a) |
| Total commits on `main` | 504 |
| Player status | ✅ User-verified on-device (play, seek, resume — Session 20) |
| Downloads | ✅ Full modular download system merged to `main` (3 engines: SinglePass/HLS/Segment + foreground service + manifest-based resume + downloads page) |
| Subtitles | ✅ Working (root cause was a fake HTML `subfont.ttf`; fixed with real DejaVu Sans TTF) |

---

## What's done (Phases 0–7, all on `main`)

| Phase | Status | Summary |
|-------|--------|---------|
| 0 — Foundation | ✅ Done | Planning, decisions D1–D4, aniyomi copied to `REFERENCE/` |
| 1 — Skeleton + onboarding | ✅ Done | 5-module Gradle project, GitHub Actions, Injekt DI, M3 theme, 7-step onboarding, bottom nav |
| 2 — AniList + home | ✅ Done | AniList GraphQL client, 3-step cache (Local→Supabase→AniList), home page |
| 3 — Detail + episodes | ✅ Done | Detail page (hero, description, episodes), AniyomiSourceBridge fuzzy matching |
| 4 — Player (MPV) | ✅ Done + user-verified | MPV streaming, controls, progress save/resume |
| 5 — Library/History/Search | ✅ Done | Core navigation pages, source wiring, watch progress |
| 6 — Functionality + polish | ✅ Done | Settings reorg, player UX, extension mgmt, AniList tracking, downloads, error handling |
| 7 — Backend improvements | ✅ Done | Extension trust + repos, episode caching, 3-stage pull-to-refresh, video picker, downloads drag-and-drop, floating nav, onboarding permissions |
| 7.5 — Episode list enhancements | ✅ Done | Thumbnails, titles, summaries, auto-fetch from AniList + Jikan/MAL (`EpisodeMetadataFetcher`), toggles in settings (`MetadataSettingsScreen`, `PlayerEpisodeDisplayScreen`, `DisplaySettingsScreen`, `LayoutSettingsScreen`, `DetailsSettingsScreen`), live preview (`EpisodeRowPreview`) |
| 8 — Statistics & watch tracking | Planned | See `DOCS/PLAN/STATISTICS-PLAN.md` |
| 9 — 4 designs + theming | Not started | Material3 / Dark Neon / Neobrutalism / Coffee Notebook. Current theme is a single hardcoded Material 3 Expressive scheme (Light/Dark only; no AMOLED, no accent presets, no design picker). |
| 10 — Final polish | Not started | Backup/restore (currently stub only — see below), auto-download (stub only), episode notifications (not started), performance pass, bug bash |

---

## Known issues (open)

31 triaged issues in `KNOWN-ISSUES.md`: **3 CRITICAL, 4 HIGH, 8 MEDIUM, 16 LOW**.
Most are open/deferred. The 3 CRITICAL are player-related (race conditions found in the Session 44 code review — not yet fixed; the player works but is fragile).

---

## Key risk areas (handle with extra caution)

1. **`PlayerActivity.kt` (2,430 LoC god object)** — all player business logic in one Activity; `PlayerViewModel` is just a state bag. Refactor deferred (awaiting user decision). See `DOCS/ENGINEERING/MODULARIZATION-ASSESSMENT.md` P0.4.
2. **MPV native lifecycle** — `MPVLib.initialize()` runs once per process; re-init crashes the app. Has broken and been re-fixed multiple times.
3. **Zero SQLDelight migrations** — the first schema change after launch will wipe user data unless `.sqm` migrations are added. See `DOCS/ENGINEERING/MODULARIZATION-ASSESSMENT.md` P0.5.
4. **Subtitle rendering** — has broken silently multiple times (fake font, sid getter, arg order). Each fix was deep and non-obvious.
5. **Extension binary contract** — the `eu.kanade.tachiyomi.animesource.*` package names must never be renamed (breaks extension classloading — learned in Phase 5).
6. **`:app` is a 147-file monolith** — no feature-module split yet. Natural future candidates: `:feature:player`, `:feature:library`, etc.

Full risk list: `DOCS/ENGINEERING/MODULARIZATION-ASSESSMENT.md`.

---

## Repository layout (quick map)

```
anikuta/
├── app/ core/ data/ domain/ source-api/   ← THE 5 GRADLE MODULES (working code)
├── REFERENCE/                              ← PRISTINE aniyomi copy (READ-ONLY, never edit)
├── REFERENCE-STAGING/                      ← empty (for upstream review)
├── .github/workflows/build-apk.yml         ← ONLY CI workflow (debug APK)
├── MEMORY/                                 ← session memory + rules + credentials (secrets gitignored)
├── DOCS/                                   ← all documentation
│   └── ENGINEERING/                        ← ⭐ PRIMARY ENGINEERING DOCS (read these)
├── SETUP/ BUILD-APK/                       ← (recently refreshed — see SETUP/README.md)
├── KNOWN-ISSUES.md worklog.md plan.md      ← issue triage + project history
└── README.md
```

---

## What changed recently (since the last structured session log)

`MEMORY/SESSION-LOGS/` stops at Session 31 (Library/History/Search revamp, 2026-07-15). Since then, the following landed on `main` (per `worklog.md`):

- Full download-system rebuild (modular engines, segment resume, foreground service, notifications, downloads page v1–v4, download button in player episode list). Merged via the now-deleted `player-experiment` branch (merge commit `a05d07c`).
- Library/History/Search revamp merge (commit `ca644ad`).
- Extension → AniList linking flow (auto-search + cache + manual fallback + adult filter).
- AniList browse uses `getTrending`, multi-select Year/Genre/Format filters.
- Removal of all `primaryContainer` colors from the player.

> **Note on `worklog.md`:** It is the project's historical narrative log (2,389 lines). Some of its older entries reference a "`player-experiment` branch" — those references are **historical** (written while the branch existed). That branch has been merged into `main` and deleted. All work is on `main` now.

---

## What's actually left (verified 2026-07-16)

The earlier "next" phase (7.5) is **already done**. The genuinely remaining work, in rough priority order:

| Feature | Status | Notes |
|---------|--------|-------|
| **Backup / restore** | Stub only | `BackupPreferences` + flag constants exist in `:domain/backup/service/`, but **no UI, no worker, no actual backup creation or restore logic**. The flag constants are manga-leftovers (`FLAG_CHAPTERS`) — need anime adaptation. User wants this as a **separate dedicated module/folder** with custom-backup management + ability to load backups from aniyomi and similar forks. |
| **Auto-download** | ✅ Done (Phases N-5 + N-6) | New-release auto-download (Phase N-5) + watch-flow auto-download (Phase N-6) are both implemented. The old `DownloadPreferences` stub keys (`downloadNewEpisodes()`, etc.) are NOT consumed — the new `NotificationPreferences` system handles auto-download instead. |
| **Episode notifications** | ✅ Done (Phase N) | Full smart release-tracking system in `app/anikuta/notification/`: weighted-average timing using AniList + extension release-time learning, 3 notify modes (AniList/extension/both), sub/dub detection + separate notifications, dub-lag handling, per-anime settings (three-dot menu on detail page), quiet hours, background battery exemption, BootReceiver, deep-linking to detail page from notifications. See `DOCS/PLAN/NOTIFICATIONS-PLAN.md`. |
| **Auto-download (new releases)** | ✅ Done (Phase N-5) | Wired into the release tracker: when a new episode is detected + user opted in, triggers `DownloadManager.enqueueDownload()`. Per-anime sub/dub toggles + global quality/audio preferences. |
| **Watch-flow auto-download** | ✅ Done (Phase N-6) | `WatchFlowAutoDownload.kt` in the player package: while watching a downloaded episode, pre-downloads the next one. One-line hook in `PlayerActivity.switchEpisode()`. Global-only setting. |
| **Theming (Phase 9)** | Not started | `Theme.kt` has a single hardcoded Material 3 Expressive scheme (Light/Dark only). **No AMOLED, no accent presets, no design picker, no DesignSpec/DesignProvider abstraction.** The 4 designs (Material3/Dark Neon/Neobrutalism/Coffee Notebook) are documented in `DOCS/PLAN/DESIGNS/` but not implemented. |
| **Quality-of-life features** | Various | To be enumerated as the user identifies them. |
| **PlayerActivity refactor** | Deferred | 2,430-LoC god object. High value, high risk. Awaiting user go-ahead; will explain plan before touching. |
| **Statistics (Phase 8)** | Planned | See `DOCS/PLAN/STATISTICS-PLAN.md`. |

## Immediate next steps (per user direction, 2026-07-16)

1. ✅ **Documentation cleanup** — DONE (commit `1cdf9f6`).
2. ✅ **Testing layer** — DONE (commit `0226996`); 4 unit-test files, 58 cases.
3. ✅ **Phase 7.5 verification** — DONE; confirmed implemented, docs updated.
4. ⏭️ **Decide which feature to implement next** — backup, auto-download, episode notifications, or theming. User has suggested a **branch-per-feature + dedicated-agent** workflow: a separate agent works in a new branch, implements the feature, then the main agent tests and merges if good. Awaiting user's pick + confirmation of the workflow.

---

## How to verify this file is current

- Check `git log -1 --format='%H %ci' main` — if the date is newer than this file's "Last updated" line, this file may need a refresh.
- Check `KNOWN-ISSUES.md` — if the open-issue counts above don't match, refresh.
- When in doubt, the truth is in the code (`app/`, `core/`, `data/`, `domain/`, `source-api/`) and `git log main`.

---

_Maintenance rule: update this file after every merge to `main` or any significant status change. Keep it to one screen._
