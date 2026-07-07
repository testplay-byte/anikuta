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
