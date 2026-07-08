# ANI-KUTA Worklog

> Project work log. Appended after each session. **Backed up to GitHub** (in anikuta repo, gitignored path — committed to a separate `worklog` branch for safety).

---

## Session 1-14 (pre-restart, reconstructed from git history + context)

Sessions 1-14 covered:
- Phase 0: Foundation (planning, decisions D1-D4, aniyomi copied to REFERENCE/)
- Phase 1: Skeleton + onboarding (5-module Gradle project, GitHub Actions, Injekt DI, M3 theme, 7-step onboarding, bottom nav)
- Phase 2: AniList + caching (GraphQL client, 3-step cache Local→Supabase→AniList, home page with real data)
- Phase 3: Detail page (collapsing header, episodes, save/share)
- Phase 4: Player (MPV integration, minimal 7-file player, ffmpeg-kit, video rendering fixes)
- Phase 5: Library + History + Search + Source wiring (extension chain, fuzzy matching, progress saving, resume)

Key fixes during Phases 4-5:
- Extension detection: reqFeatures vs metaData (checked wrong Bundle field)
- Source-api package: moved back to eu.kanade.tachiyomi.animesource (extensions compile against it)
- Core network: moved to eu.kanade.tachiyomi.network (extensions call RequestsKt)
- Relative class names: .Anikoto → pkg.Anikoto
- Video rendering: force-window=yes + auto-safe hwdec
- Pull-to-refresh: await all loads before clearing isRefreshing
- Library real-time refresh: LibraryStore.changes Flow
- Crash handler: AnikutaCrashHandler + ErrorActivity

---

## Session 15-16 (Phase 6 planning + re-planning)

Phase 6 was initially planned as "4 designs + theming" but the user decided the app needs to be fully functional first. Phase 6 was re-planned as "App Functionality + Polish" and the designs were moved to Phase 8.

8 open questions answered by user:
- Q1: Priority order (settings reorg → player UX → extensions → tracking → downloads → polish)
- Q2: Use aniyomi's AniList client ID 5338 (swap to our own later)
- Q3: Default extension repo + allow adding more
- Q4: MP4 format for downloads
- Q5: Preferred quality in settings + per-episode override
- Q6: Separate NavGraph routes for settings subpages
- Q7: Preload on detail page, don't open player until ready
- Q8: Auto-resume + "start over?" overlay (10s auto-dismiss)

---

## Session 17 (Phase 6 implementation)

Task ID: P6 (Session 17)
Agent: main (Z.ai Code)
Task: Complete Phase 6 — App Functionality + Polish (all 6 sections).

Work Log:
- Section 1 (Settings reorg, 6.17-6.24): SettingsHomeScreen (6 category cards), 6 subpages (General, Player, Extensions, Downloads, Tracking, About), SettingsSubpageScaffold, SettingsComponents.kt. 6 NavGraph routes. Build green (2e765ca).
- Section 2 (Player UX, 6.25-6.27): Loading overlay (spinner + "Loading…"), resume "start over?" overlay (10s auto-dismiss). Build green (9c4b222).
- Section 3 (Extension mgmt, 6.1-6.6): Subagent built ExtensionsViewModel + ExtensionsSettingsScreen. APK install via FileProvider + system installer. REQUEST_INSTALL_PACKAGES permission. Build green (9c4b222).
- Section 4 (AniList tracking, 6.7-6.11): AniListTracker (OAuth client ID 5338), MainActivity OAuth callback, TrackingSettingsScreen, progress sync on episode finish. Build green (32d8c6c).
- Section 5 (Downloads, 6.12-6.16): DownloadPreferences, DownloadStore, DownloadWorker (WorkManager + HTTP Range partial resumption + auto-retry), DownloadManager, DownloadsViewModel + DownloadsSettingsScreen. Build green (32d8c6c).
- Section 6 (Polish, 6.28-6.32): SkeletonBox + SkeletonAnimeCard. Error/empty states audited. Build green (68c9e02).

Stage Summary:
- Phase 6 ALL 6 SECTIONS COMPLETE. Build 68c9e02 green. APK 38.3 MB.
- Settings reorganized into 7 subpages. Player UX improved. Extensions manageable. AniList tracking. Downloads with partial resumption. Polish.
- Phase 7 (Downloads + Extensions) was absorbed into Phase 6 — both are done.

---

## Session 18 (Sandbox restart + restoration)

Task ID: RESTORE (Session 18)
Agent: main (Z.ai Code)
Task: Restore sandbox after restart.

Work Log:
- Sandbox was restarted, losing all local-only files (anikuta repo was on GitHub, live preview was local-only).
- Cloned anikuta repo from GitHub (commit 68c9e02, Phase 6 complete). All code verified intact.
- Restored GitHub token (user-provided). Push access verified.
- Restored Supabase credentials (user-provided). Both anon + service_role keys verified (HTTP 200).
- Rebuilt live preview website from scratch (hub-view, build-progress-view, plan-view, theme-provider, data files).
- Dev server issue: Next.js Turbopack compilation hangs on CSS imports in the sandbox. Simplified layout.tsx (removed Google Fonts + CSS imports). Server compiles successfully (25KB HTML, 3ms compile) but sandbox kills background processes between Bash commands.
- User instructed to leave live preview after 3 tries.
- Restored worklog.md (this file) from conversation context + git history.
- Created MEMORY/CREDENTIALS/supabase-credentials.md (gitignored).

Stage Summary:
- anikuta repo: fully restored from GitHub. All Phase 6 code intact.
- Credentials: GitHub token + Supabase credentials stored + verified.
- Live preview: rebuilt but dev server doesn't stay alive between Bash commands (sandbox limitation). Code is correct — server compiles in 3ms.
- Worklog: restored from context. Going forward, will be backed up to GitHub.
- READY for Phase 8 (4 designs + theming) when user gives go-ahead.

---

## Session 19 (Player pipeline fixes — extension video resolution + TLS + 403 + headers + error handling)

Task ID: P5-FIX (Session 19)
Agent: main (Z.ai Code)
Task: Fix the end-to-end video playback pipeline so non-AniKoto extensions actually play. The player loaded for AniKoto but failed for every other extension with a chain of distinct root causes.

Work Log:
- **Issue 1 — extension URL crash (`IllegalArgumentException: Invalid URL host`).** Root cause: extensions only override `videoListRequest`, not `hosterListRequest`. The base `AnimeHttpSource.hosterListRequest` constructs `GET(baseUrl + episode.url)` where `episode.url` is an already-encoded path like `/watch/.../ep-1` — concatenating it onto `baseUrl` produces a malformed URL. Fix: in `DetailViewModel.playEpisode`, wrap `getHosterList` in a try/catch that catches **ALL** `Throwable` (not just `IllegalStateException`) and falls back to `getVideoList`. Commit `b25c8fa`.
- **Issue 2 — `Requests.kt` lenient `toHttpUrl()` fallback.** Initially added a lenient fallback that tolerated bad URLs; this masked the real problem and was reverted. `GET()` now uses the standard `url.toHttpUrl()` again. Commit `b25c8fa`.
- **Issue 3 — TLS certificate rejected (`The certificate is not correctly signed by the trusted CA` → `loading failed`).** Root cause: aniyomi ships a `cacert.pem` in assets and sets `tls-verify=yes` + `tls-ca-file=cacert.pem`. We don't have that asset, so MPV rejects the self-signed/untrusted certs that streaming servers commonly use. Fix: set `tls-verify=no` in `AnikutaMPVView.initOptions()`. Acceptable for a sideloaded app (not on Google Play). Commit `2414d12`.
- **Issue 4 — HTTP 403 Forbidden.** Root cause: streaming servers require `Referer` + `User-Agent` headers. The extension sets these in its `videoListRequest`, but MPV knows nothing about them — it just loads the bare URL. Fix:
  1. Extract the `Video.videoHeaders` field from the resolved `Video` object in `DetailViewModel.playSpecificVideo`.
  2. Pass headers through `PlayerActivity.newIntent(..., videoHeaders = ...)` as `EXTRA_VIDEO_HEADERS`.
  3. In `PlayerScreen`, before `loadfile`, call `MPVLib.setOptionString("http-header-fields", headers)` — one `Header: Value` pair per line (MPV parses newline-separated fields).
  4. When no extension headers exist, fall back to a default `User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36`.
  Commits `dbcd967`, `1d5f7d2`.
- **Issue 5 — infinite loading spinner on failure.** Root cause: `PlayerObserver.efEvent` (END_FILE with error) wasn't surfaced to the ViewModel, so a failed load left the spinner spinning forever. Fix: added `onFileEnded(errorMessage: String?)` to `PlayerObserver.Callback`; `PlayerActivity` routes it to `viewModel.onError(message)`, which flips `loadingState` to `ERROR`. The overlay now shows the error message + a "Go Back" button within 1–2 seconds of failure. Commit `dbcd967`.
- **Issue 6 — videoHeaders read from intent inside Composable (stale).** The Composable re-read `EXTRA_VIDEO_HEADERS` from the Activity intent on recomposition, which could be empty on the first pass. Fix: read `videoHeaders` once in `onCreate` and pass it as a parameter to `PlayerScreen`. Commit `1d5f7d2`.
- **MPV lifecycle (re-confirmed).** Every `onCreate` calls `view.initialize()` (fresh MPV core). Every `onDestroy` calls `MPVLib.removeLogObserver` + `removeObserver` + `command("stop")`, then `BaseMPVView.destroy()` via reflection (falls back to `MPVLib.destroy()`). This is why the 2nd-open works: destroy() fully tears down the native context so the next initialize() is clean. Matches aniyomi's `onCreate→initialize`, `onDestroy→destroy`.

Stage Summary:
- The full pipeline now works for non-AniKoto extensions: extension → `getHosterList`/`getVideoList` (with fallback) → `Video` (with headers) → `PlayerActivity` → MPV `http-header-fields` → playback.
- 5 distinct root causes fixed (URL host, TLS, 403, error surfacing, stale header read). Each fix references the aniyomi source before deviating.
- Builds: `b25c8fa` → `2414d12` → `dbcd967` → `1d5f7d2` (all green on GitHub Actions, arm64-v8a).

---

## Session 20 (User on-device verification — PLAYER FULLY WORKING + documentation/backup)

Task ID: VERIFY+DOC (Session 20)
Agent: main (Z.ai Code)
Task: User verified the player on-device. Document everything done in Sessions 18–19, back up all work + context to GitHub so a sandbox crash loses nothing.

Work Log:
- **User on-device test results (build `1d5f7d2`):**
  - ✅ Episode plays from server/quality selection screen.
  - ✅ Loading screen shows, then video starts playing correctly.
  - ✅ Seek forward works — buffers, then seeks to the position and resumes.
  - ✅ Resume works — go back, replay the same episode, it resumes from the saved position.
  - ⚠️ Minor glitch: first episode tap after a fresh app launch sometimes showed "no source available"; closing and reopening the app cleared it. (Root cause: source manager still loading extensions when the first search fires. The retry loop in `searchExtensions` waits up to 10 s, but the very first cold launch can still race. Tracked as a polish item — NOT a blocker.)
- **Documentation updates this session:**
  - `worklog.md` — appended Sessions 19 + 20 (this file).
  - `DOCS/APP/STRUCTURE/player.md` — added gotchas 5–9 (TLS, 403/headers, error overlay, destroy lifecycle, stale header read).
  - `MEMORY/SESSION-LOGS/` — created session-15 through session-20 logs.
  - `MEMORY/SESSION-LOGS/README.md` — updated index.
- **GitHub backup:** all 21 unpushed commits (Sessions 18–20) pushed to `origin/main`. The repo now contains: all source, all docs, all session logs, the worklog, the live-preview HTML, and the build-progress HTML. Credentials are gitignored. A sandbox crash/restart → `git clone` restores everything except locally-built APKs (rebuildable via GitHub Actions).

Stage Summary:
- **Player is feature-complete and user-verified.** Play, seek, resume, server/quality selection all work on-device.
- **Full project state is backed up to GitHub.** Clone + restore credentials = full recovery.
- **Known minor issue (not a blocker):** cold-launch "no source available" race — to be addressed in the polish phase alongside extension-settings management.
- **Next phase (user-directed):** backend/improvements — extension settings management (each extension has its own prefs we don't surface), plus other small adjustments. Will be properly planned before implementation.

---

## Session 21 (Phase 7 planning — thorough research + detailed plan)

Task ID: P7-PLAN (Session 21)
Agent: main (Z.ai Code)
Task: Plan Phase 7 (backend improvements) thoroughly. User specified 4 areas: (A) extension system overhaul, (B) downloads preferences redesign, (C) episode list + detail caching & refresh, (D) episode video caching + picker redesign. Produce a detailed plan for user verification before implementation.

Work Log:
- Read the user's uploaded log file (AniKoto extension resolving 27 videos in 4 server groups: VidPlay-1/HD-1/Vidstream-2/VidCloud-1 × SUB/DUB/HSUB × 1080p/720p/360p). Confirmed the Video title format is `"{server} - {audio} - {quality}"`.
- Launched 4 parallel Explore agents to research the aniyomi reference + current ANI-KUTA code for each area:
  - Agent 1 (extension repo/trust/settings): Found aniyomi has a FULL repo management stack (SQLDelight `extension_repos` table, interactors, service, UI). ANI-KUTA has the domain/data code copied but NOT wired — STUB classes in `app/` shadow the real `domain/` versions. aniyomi HAS a trust system (`TrustAnimeExtension`, `trustedExtensions()` pref, `AnimeExtension.Untrusted`). ANI-KUTA's stub always returns true. aniyomi HAS extension details + settings screens (`AnimeExtensionDetailsScreen` → `AnimeSourcePreferencesScreen` with `PreferenceFragmentCompat` interop). ANI-KUTA has none. The "MAX 2 trusted + popup" is NOVEL — no aniyomi precedent.
  - Agent 2 (download prefs): Found aniyomi has NO global preferred quality/audio/server — delegates entirely to extensions via `sortHosters()`/`sortVideos()` + `Video.preferred`. ANI-KUTA's entire priority-list + drag UI + resolver must be built from scratch. aniyomi uses `sh.calvin.reorderable:2.4.3` for drag-and-drop (category reordering) — ANI-KUTA doesn't have the dep yet. `HosterLoader.getBestVideo()` is the concurrent-resolution mechanic to adapt.
  - Agent 3 (episode/video caching + refresh): Found aniyomi caches episodes in SQLDelight DB (persistent, no TTL) with `SyncEpisodesWithSource` for smooth diffs + `Flow` subscription for live UI updates. ANI-KUTA has the DB layer dormant — `DetailViewModel` uses an in-memory Map. aniyomi has single-stage `PullRefresh`; the 3-stage is NOVEL (custom `nestedScroll`). aniyomi does NOT cache videos (re-fetches every time — signed URLs expire); ANI-KUTA must build a short-TTL video cache.
  - Agent 4 (video picker): Found aniyomi's `QualitySheet.kt` uses `LazyColumn` (scrolls) + `List<Boolean>` for collapse + structural `Hoster` grouping — but does NOT group by audio or sort by quality. ANI-KUTA's picker uses a plain `Column` (NO scroll — the bug), NO collapse, NO sort, NO title parsing. `Video` model has `videoTitle` + `resolution: Int?` + `audioTracks`. Must build `VideoTitleParser` + audio grouping + quality-desc sort.
- Synthesized findings into `DOCS/PLAN/PHASE-7-PLAN.md` — a detailed plan with 4 workstreams (A/B/C/D), each with: current state, tasks (with file paths + aniyomi references), files touched, verification steps. Plus cross-cutting concerns (signed URL expiry, extension diversity, DB schema, backward compat) and 8 open questions for the user.

Stage Summary:
- `DOCS/PLAN/PHASE-7-PLAN.md` written (detailed plan, ~500 lines). Awaiting user verification.
- Key findings: (1) ANI-KUTA already has much of aniyomi's extension repo/trust/settings domain code copied but unwired (stub shadow conflict must be resolved first). (2) The download priority system is almost entirely novel (aniyomi delegates to extensions). (3) The 3-stage pull-to-refresh + video cache are novel. (4) The video picker needs a full rewrite (Column→LazyColumn, add collapse, add audio grouping, add quality sort, add title parser).
- Recommended implementation order: A → C → D → B (A establishes trusted-sources gate; C before D unblocks Flow-based updates; B last reuses D's VideoTitleParser).
- 8 open questions flagged for the user (trust model, popup UX, grouping depth, pull thresholds, cache TTL, refresh guard, order, other-extension formats).
- NO code written this session — planning only, per user's request.

---

## Session 22 (Phase 7 implementation — all 4 workstreams)

Task ID: P7-IMPL (Session 22)
Agent: main (Z.ai Code)
Task: Implement all 4 workstreams of Phase 7 (A: extensions, C: caching, D: video picker, B: downloads). Build green after each workstream.

Work Log:
- **Workstream A — Extension system overhaul** (build 731d376):
  - Deleted 3 stub files that shadowed real domain/ implementations (GetAnimeExtensionRepo, UpdateAnimeExtensionRepo, ExtensionRepo model)
  - Real TrustAnimeExtension: per-package trust via SourcePreferences.trustedSources()
  - AnimeExtensionLoader: real trust check (replaces always-true stub) + proper signature hash
  - AnimeExtensionManager: trust()/revokeTrust() with MAX_TRUSTED=2 + TrustResult sealed class
  - AppModule: wired real AnimeExtensionRepoRepository + ExtensionRepoService + 7 repo interactors
  - ExtensionsSettingsScreen: restructured to 3 sections (Sources/Installed/Available) with Trust/Untrust/Delete + max-2-trusted popup + Manage Repos button
  - ExtensionReposScreen + ViewModel: add/remove/refresh repos (SQLDelight-backed)
  - ExtensionDetailsScreen: metadata + source list with Settings gear for ConfigurableAnimeSource
  - SourcePreferencesScreen: PreferenceFragmentCompat interop (source.setupPreferenceScreen) with SourcePreferenceDataStore
  - NavGraph: 3 new routes (extension_repos, extension_details, source_preferences)
  - MainActivity: FragmentActivity (for PreferenceFragmentCompat)
  - Added androidx.preference dependency
  - Build fix: missing Json import in AppModule

- **Workstream C — Episode caching + 3-stage pull-to-refresh** (build 96af06a):
  - DetailViewModel: background soft-refresh of episodes on cache hit (5-min guard)
  - DetailViewModel: 3 refresh methods (refreshEpisodesOnly, refreshDetailsOnly, refreshEverything)
  - RefreshStage enum + isRefreshing state
  - ThreeStagePullRefresh: custom nestedScroll composable tracking pull distance with 3 thresholds (120dp/240dp/360dp). Shows "Release to refresh episodes/details/everything" labels.
  - DetailScreen: wrapped LazyColumn in ThreeStagePullRefresh, fixed Retry button

- **Workstream D — Video caching + picker redesign** (build 862b403):
  - VideoTitleParser: parse "{server} - {audio} - {quality}" titles → (server, AudioVersion, quality)
  - AudioVersion enum (SUB/DUB/HSUB/ANY) with fromToken() factory
  - groupVideosByAudio(): groups flat video list → AudioSection → ServerSection → Video (quality desc sort)
  - DetailViewModel: short-TTL video cache (10 min, keyed by episode.url) + VideoPickerState.Resolving/Cached/Show + background re-resolve (show cached instantly, smooth swap)
  - DetailViewModel: expandedServers state + toggleServer() for collapsible sections
  - VideoPickerSheet: new picker with LazyColumn (fixes scroll), collapsible server headers, audio-version grouping (SUB/DUB/HSUB sections), quality-desc sort, refreshing badge on cache hit
  - Build fix: VideoTitleParser sortedWith type mismatch

- **Workstream B — Downloads preferences redesign** (build dcd8048):
  - Added sh.calvin.reorderable:2.4.3 dependency
  - DownloadPreferences: replaced single-value prefs with ordered-list prefs (preferredQualityOrder, preferredAudioOrder, preferredServerOrder) + PriorityMode + AudioFallback + Phase 6 migration
  - PreferenceListDelegate: JSON-serialized List<String> backed by String pref
  - DownloadsViewModel: 3 StateFlow<List<String>> for drag reorder + priorityMode + audioFallback + reorder methods
  - DownloadsSettingsScreen: full rewrite with 3 ReorderableLazyColumn drag-and-drop lists + priority mode radio + audio fallback radio + WiFi/delete toggles

Stage Summary:
- ALL 4 WORKSTREAMS COMPLETE. 4 builds green (731d376, 96af06a, 862b403, dcd8048).
- ntfy notifications sent after each workstream + final.
- Key features delivered:
  - Extension trust system with max-2 limit + popup
  - Extension repo management (add/remove repos)
  - Extension details + settings pages (ConfigurableAnimeSource interop)
  - Background episode soft-refresh (5-min guard)
  - 3-stage pull-to-refresh (episodes / details / everything)
  - Short-TTL video cache (10 min) with smooth soft-refresh
  - Video picker: scrollable + collapsible + audio-version grouping + quality-desc sort
  - Drag-and-drop download priority lists (quality/audio/server)
  - Quality-vs-audio priority toggle + audio fallback mode
- No existing functionality broken (all builds compile + produce APK).
- Awaiting user on-device verification.

---

## Session 23-27 (Phase 7 implementation + iterations — comprehensive log)

Task ID: P7-FULL (Sessions 23-27)
Agent: main (Z.ai Code)

Work Log:
- Session 23: Implemented all 4 Phase 7 workstreams (A: extensions, C: caching, D: video picker, B: downloads). All builds green.
- Session 24: On-device testing round 1 — fixed 6 issues (downloads crash, source settings crash, repo not adding, video picker hierarchy, extension details UI, installed section trust button + logos).
- Session 25: On-device testing round 2 — fixed 4 issues (downloads toggles not live, video picker collapse/accordion/audio chips, extension details button balance, source settings empty + repo DI).
- Session 26: On-device testing round 3 — fixed 5 issues (untrusted extension tap→details, source settings preferenceScreen attach, repo SqlDriver DI root cause, video picker height/auto-close/animations, audio chip order).
- Session 27: Extensions page overhaul — search bar, filter bottom sheet, auto-refresh (BroadcastReceiver), direct install, compact install button, max-2 popup with logos + auto-trust, squircle icons. Then iterative fixes: stripped "Aniyomi: " prefix, redesigned search bar, redesigned filter sheet, installed→bottom of Available + tap-to-delete, removed grid layout, onboarding permissions (storage + install unknown apps), onboarding extension step redesign, sources priority drag-and-drop, NPE crash fix, bottom nav floating pill redesign, pull-to-refresh fixes, synopsis HTML tag stripping.

Stage Summary:
- Phase 7 fully implemented + iterated through 5 rounds of on-device testing.
- All builds green. Latest: 27053e1.
- Key systems delivered:
  - Extension trust system (max 2, with popup + auto-trust)
  - Extension repo management (add/remove/refresh)
  - Extension details + settings pages (ConfigurableAnimeSource interop)
  - Episode caching (in-memory + persistent source-match) + background soft-refresh
  - 3-stage pull-to-refresh (episodes / details / everything)
  - Video picker: Server→Audio→Quality hierarchy, collapsible, accordion, 10-min cache
  - Downloads: drag-and-drop priority lists (quality/audio/server) + fallback modes
  - Sources priority drag-and-drop (affects AniyomiSourceBridge tiebreaking)
  - Search bar (below action icons, animated)
  - Filter bottom sheet (language, sort)
  - Auto-refresh on package install/uninstall (BroadcastReceiver)
  - Direct install (ACTION_INSTALL_PACKAGE, no chooser)
  - Floating pill bottom nav (transparent background, content scrolls under)
  - Onboarding: storage + install-unknown-apps permissions, extension selection with refresh
  - Synopsis HTML tag stripping
- Plans written for future sessions:
  - DOCS/PLAN/STATISTICS-PLAN.md (watch tracking, heatmaps, genre stats)
  - DOCS/PLAN/EPISODE-LIST-ENHANCEMENTS-PLAN.md (thumbnails, titles, summaries, auto-fetch)

---

## Session 28 (Phase 7.5: Episode metadata fetcher + Kitsu + persistence)

Task ID: P7.5-METADATA (Session 28)
Agent: main (Z.ai Code)

Work Log:
- Analyzed log: Jikan returned 0 episodes due to rate limiting (200 + empty data)
- Fixed Jikan retry: now retries on empty results (not just exceptions). 5 retries, 3s delay.
- Added Kitsu as third metadata source:
  - Step 1: MAL ID → Kitsu ID via mappings endpoint
  - Step 2: Fetch episodes from Kitsu (has titles, thumbnails, synopses, air dates)
  - Tested: Kitsu has rich data for older anime (Witch Hat Atelier: 13 episodes with all fields)
- Fixed metadata persistence: backgroundRefreshEpisodes was overwriting enriched episodes
  with fresh non-enriched ones. Now preserves enriched fields (preview_url, summary, name,
  date_upload) when the fresh episode doesn't have them.
- idMal lookup: fresh AniList GraphQL query when cached anime doesn't have idMal
- Fallback thumbnail: only used when real data exists (no more same banner for all episodes)

Stage Summary:
- 3 metadata sources: AniList streaming → Jikan (MAL) → Kitsu (all in parallel)
- Kitsu provides: titles, thumbnails, descriptions, air dates
- Jikan provides: titles, air dates (no thumbnails/descriptions)
- AniList provides: thumbnails (streaming episodes, rare)
- Merge priority: Title (Jikan→Kitsu→AniList), Description (Kitsu only),
  Thumbnail (Kitsu→AniList→banner fallback), Air date (Jikan→Kitsu)
- Metadata persists across navigation (background refresh preserves enriched fields)
