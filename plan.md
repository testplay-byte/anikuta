# Download System — Implementation Plan (Refined)

> **Status:** Planning — ready for execution once user confirms
> **Branch:** `player-experiment`
> **Based on:** `DOCS/PLAN/DOWNLOAD-PLAN.md` (original) + user's draft improvements + actual code analysis
> **Created:** 2026-07-13
> **Supersedes:** `DOCS/PLAN/DOWNLOAD-PLAN.md` (which was written before any download code existed; this plan reflects the actual current state)

---

## 0. The mpv-lib question (answered)

**User asked:** "What is different between our lib and the aniyomi lib? What is the difference and which one are we using? Which one are they using?"

**Answer: There is NO difference. We use the exact same library.**

| | ANI-KUTA | aniyomi |
|---|---|---|
| Dependency | `com.github.aniyomiorg:aniyomi-mpv-lib:1.18.n` | `com.github.aniyomiorg:aniyomi-mpv-lib:1.18.n` |
| Maven coordinate | identical | identical |
| Version | `1.18.n` | `1.18.n` |
| `Utils.findRealPath(fd)` | ✅ available | ✅ used for offline playback |
| `Utils.PROTOCOLS` | ✅ available | ✅ used for URI scheme detection |
| `BaseMPVView` | ✅ same class | ✅ same class |

**Proof from our code:**
- `gradle/libs.versions.toml`: `mpv-lib = "1.18.n"` → `com.github.aniyomiorg:aniyomi-mpv-lib`
- `app/src/main/java/app/anikuta/player/PlayerUtils.kt` already imports `is.xyz.mpv.Utils` and calls `Utils.findRealPath(fd)` — this is the same function aniyomi uses.
- `PlayerActivity.kt` already calls `resolveUrlForMpv(currentVideoUrl, this@PlayerActivity)` at all 4 `loadfile` call sites (lines 1259, 1645, 1875, 2014). This function handles `content://` URIs by converting them to `fd://` via `openContentFd()`.

**Conclusion:** The offline playback path (`content://` → `ParcelFileDescriptor` → `detachFd()` → `Utils.findRealPath(fd)` → `"fd://$fd"` or real path) is **already implemented and wired in**. No spike is needed. The `fd://` approach is proven to work with our exact mpv-lib version because it's the same library aniyomi uses.

**What was NOT done:** A standalone "spike" build to verify this in isolation. But since the code is already integrated and the library is identical, a spike would be redundant. We'll verify it works end-to-end as part of Phase 2 testing (download an episode → turn off network → play it → subtitles appear).

---

## 1. Current state assessment

**IMPORTANT:** The download system is NOT being built from scratch. It is already substantially implemented on the `player-experiment` branch (16 commits ahead of `main`). This plan focuses on **fixing bugs and completing missing features**, not building the foundation.

### What's already built and working

| Component | File | Status |
|---|---|---|
| Download model | `download/Download.kt` | ✅ Data class with StateFlows (status, progress), totalSize, downloadedBytes, error |
| Path scheme | `download/DownloadProvider.kt` | ✅ `downloads/<source>/<anime>/<episode>/video.mkv` via StorageManager (SAF) |
| Expired-link resolver | `download/DownloadVideoResolver.kt` | ✅ Re-resolves from source via getHosterList/getVideoList, applies priority prefs |
| Offline playback helper | `player/PlayerUtils.kt` | ✅ `openContentFd` → `Utils.findRealPath` → `fd://` (ported from aniyomi) |
| Filename sanitizer | `util/storage/DiskUtil.kt` | ✅ `buildValidFilename` |
| FFmpeg SAF bridge | `util/storage/FFmpegUtils.kt` | ✅ `toFFmpegString` (SAF URI → `saf://` parameter) |
| Queue manager | `download/DownloadManager.kt` | ✅ Facade: enqueue, cancel, retry, remove, clearCompleted, live queue StateFlow, restore on startup |
| Download engine | `download/DownloadWorker.kt` | ⚠️ FFmpeg-based CoroutineWorker, 3 retries, disk space check, tmp→episode move, statistics callback — **but has concurrency bug (D1) and no foreground service (D2)** |
| Persistent store | `download/DownloadStore.kt` | ✅ PreferenceStore-backed, serializable entries, CRUD operations |
| Queue UI | `ui/download/DownloadQueueScreen.kt` | ⚠️ Stats chips, progress bars, cancel/retry/remove — **but no pause/resume, no speed, no size** |
| Download buttons | `ui/detail/DetailScreen.kt` | ⚠️ DownloadButton on both episode paths with live state — **but no long-press menu, no "Download all" button, not configurable** |
| Download actions | `ui/detail/DetailViewModel.kt` | ✅ `downloadEpisode()`, `downloadAllEpisodes()`, `downloadStatus` flow, offline playback check |
| Offline playback | `player/PlayerActivity.kt` | ✅ `resolveUrlForMpv()` at all 4 loadfile call sites |
| Nav routing | `navigation/AnikutaNavGraph.kt` | ✅ `settings/downloads` → queue page, `settings/downloads/settings` → settings |
| DI registration | `di/AppModule.kt` | ✅ All 5 download singletons registered |

### What's missing or broken (see KNOWN-ISSUES.md for full details)

| ID | Issue | Severity |
|---|---|---|
| D1 | Concurrency bug — downloads run 1-at-a-time despite `maxConcurrentDownloads` setting | CRITICAL |
| D2 | No foreground service — downloads die when app is backgrounded (Android 12+) | CRITICAL |
| D3 | No notification channels / no download notifications | HIGH |
| D4 | Progress estimation hardcoded to 24 minutes | HIGH |
| D5 | File size (`totalSize`) never populated from Content-Length | HIGH |
| D6 | No pause / resume | MEDIUM |
| D7 | No "Download all" button in UI | MEDIUM |
| D8 | No long-press menu on episode rows | MEDIUM |
| D9 | Download button placement not configurable | MEDIUM |
| D10 | No concurrent downloads selector in settings UI | LOW |
| D12 | No storage-revoked handling | LOW |
| D13 | No network-change handling (wifi → mobile) | LOW |
| D14 | No download speed indicator | LOW |
| D16 | No DownloadCache for fast lookups | LOW |
| D17 | DownloadStore.add() silent dedup | LOW |

---

## 2. Agreed decisions (from user's draft improvements)

These are the decisions confirmed in the user's response to the original draft plan. They are **final** unless the user revisits them.

### 2.1 Download button behavior
- **Single tap** on the download button → immediately starts the download (analyze + download in one action, no extra confirmation)
- **Long press** on the download button → shows a bottom sheet with options (Download / Cancel download / Delete download / Play downloaded)
- **"Download all" button** at the top of the episodes section → downloads all (or filtered: unwatched only)
- **User can configure** whether the download button is visible or hidden (use long-press only)

### 2.2 Download button placement (configurable)
- **Default:** inside the synopsis area
  - When synopsis is on the **right side** → button shows inside the synopsis section
  - When synopsis is set to **"below"** → button shows on the **left side**
- **Configurable** in detail settings (user can choose placement)
- For all episodes, the button is on the episode row (same configurable logic)

### 2.3 FFmpeg muxing
- **v1:** Mux video + audio + subtitles into a single `.mkv` file (already implemented)
- **Later (v2):** User can choose subtitle handling:
  - Keep subtitles separate (`.vtt` files alongside `.mkv`)
  - Join them (mux into `.mkv` — current behavior)
  - Both (mux + keep separate copies)
- **Decision deferred** to next planning phase — for now, keep the current mux-into-.mkv behavior

### 2.4 Storage
- Files go to `<user-selected-folder>/downloads/<source>/<anime>/<episode>/video.mkv` via StorageManager (SAF) — **already implemented**
- May revisit storage location later depending on how things go

### 2.5 Offline playback
- Uses `ParcelFileDescriptor` → `fd://` protocol — **already implemented and wired**
- mpv-lib is identical to aniyomi's — proven approach, no spike needed

### 2.6 Download queue page
- **More section > Downloads** → shows the **queue page** by default (NOT settings)
- **Settings gear icon** at the top of the queue page → leads to **Downloads settings** subpage
- **NOT configurable** — fixed layout (user decided not to give an option here to avoid complexity)

### 2.7 Resume / retry
- WorkManager handles restart-after-kill — **implemented**
- Expired links re-resolved from source via `DownloadVideoResolver` — **implemented**
- **Partial downloads** (half-downloaded video) need proper handling — **not yet implemented** (see D11; architecturally difficult with FFmpeg copy-muxing; may need to reconsider approach)

### 2.8 Notifications
- Foreground service notification with download progress — **NOT YET IMPLEMENTED** (D2, D3)
- Error notifications with retry action — **NOT YET IMPLEMENTED**
- Notifications should be **beautiful and proper**

### 2.9 File size estimation
- Show **estimated total file size** + **current downloaded amount**
- Updates in **real-time** during download
- Based on `Content-Length` header + FFmpeg progress callbacks — **NOT YET IMPLEMENTED** (D4, D5)

### 2.10 Concurrent downloads
- **Default: 2**
- **Configurable: 1-4** (no more than 4)
- Setting exists in `DownloadPreferences.maxConcurrentDownloads()` but:
  - The concurrency is broken (D1 — runs 1-at-a-time)
  - No UI selector exists (D10)

### 2.11 Auto-download new episodes
- **Deferred to v2** (user agreed with this recommendation)
- Includes: auto-download new episodes + auto-download next episode while watching
- Will discuss implementation details in a future planning phase

### 2.12 Delete after watching
- **NO automatic delete** when episode reaches 100% watched
- **Confirm with user:** "Do you want to delete this episode?" with episode details
- **Undo window: 10-20 seconds** (not 5 seconds) — user explicitly requested longer
- **Advanced rules** (delete when watching next-next episode): deferred, will discuss later
- **Never auto-deletes** unless the user explicitly configures it in download settings
- **Deferred** to a later phase — not v1

---

## 3. Rules & principles

These rules govern HOW we implement. They are non-negotiable.

### 3.1 Decision-making rules
1. **No rash decisions** — analyze before acting
2. **No decisions without proper understanding** — research first, confirm with user on big changes
3. **No decisions without verification** — verify assumptions against the codebase
4. **One issue at a time** — verify, fix, document, verify again
5. **Build incrementally** — each phase should produce a testable APK

### 3.2 Error-handling protocol (when something breaks)
When an error or bug is found, follow this exact sequence — do NOT jump straight to fixing:

1. **Determine what the error is** — reproduce it, read the logs, understand the symptom
2. **Determine how it is affecting us** — what breaks for the user? What's the severity?
3. **Determine the root cause** — not the symptom, the actual cause. Use logs, code reading, and debugging
4. **Analyze the codebase** — understand how the root cause fits into the larger system, what the fix approach should be, and what else might be affected
5. **Plan the fix** — write down what files to change, what the change is, and how to verify
6. **Execute the fix** — implement, then verify (build + test)

### 3.3 Branch strategy
- All download work stays on `player-experiment` — **never push code to `main`** directly
- `main` is only for documentation (KNOWN-ISSUES.md, plan docs) — and even then, only when explicitly instructed
- Merge `player-experiment` → `main` only when the user approves and the build is verified

### 3.4 Build & verification
- **Build:** GitHub Actions (triggered on push to `player-experiment`)
- **Notification:** `ntfy.sh` with topic `TASKISDONE` after build completes
- **Verification:** After each phase, the build must succeed AND the feature must be tested on-device (or at minimum, the APK must install without crashing)

### 3.5 Documentation
- Update `WORKLOG.md` / `worklog.md` after each completed task
- Keep this `plan.md` updated as decisions change
- New known issues → add to `KNOWN-ISSUES.md` (on `main`)

---

## 4. Implementation phases

Each phase is designed to be independently shippable — after each phase, push to `player-experiment`, trigger a build, and verify.

### Phase 1: Fix critical bugs (must-fix before any feature work)

**Goal:** Make the existing download system actually work reliably.

**Why first:** D1 (concurrency) and D2 (foreground service) mean downloads are 1-at-a-time AND die when the app is backgrounded. No amount of new features matters if the core doesn't work. D3 (notifications) is required for D2 to function (foreground service needs a notification).

| Task | Issue | Files | What to do |
|---|---|---|---|
| 1.1 | D1 | `DownloadWorker.kt` | Fix concurrency: wrap `forEach` in `coroutineScope { pending.forEach { launch { semaphore.withPermit { processDownload(it) } } } }` so downloads run in parallel child coroutines |
| 1.2 | D2 | `AndroidManifest.xml`, `DownloadWorker.kt` | Add `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` permissions. Call `setForeground(ForegroundInfo(...))` in `doWork()` with a progress notification |
| 1.3 | D3 | `Notifications.kt`, `App.kt`, new `DownloadNotifier.kt` | Add `CHANNEL_DOWNLOADER_PROGRESS` + `CHANNEL_DOWNLOADER_ERROR` constants. Create channels in `App.onCreate()`. Create `DownloadNotifier.kt` to wrap notification updates. Wire into `DownloadWorker` |

**Verification:**
- Build succeeds on GitHub Actions
- Download 2 episodes simultaneously → both progress bars advance at the same time (D1 fixed)
- Background the app during a download → download continues (D2 fixed)
- Notification appears with download progress (D3 fixed)
- Send `ntfy.sh` notification to `TASKISDONE`

---

### Phase 2: Complete core features (user-requested)

**Goal:** Implement the features the user explicitly asked for in the draft improvements.

| Task | Issue | Files | What to do |
|---|---|---|---|
| 2.1 | D4 | `DownloadWorker.kt` | Replace hardcoded 24-minute estimate with real video duration. Use `FFprobeKit.getMediaInformation()` before download, or read duration from the first FFmpeg statistics callback. Calculate progress as `processedTime / actualDuration` |
| 2.2 | D5 | `DownloadWorker.kt`, `DownloadQueueScreen.kt` | Populate `totalSize` from `Content-Length` header (HEAD request before download). Update `downloadedBytes` from FFmpeg statistics. Display "≈ 350 MB" + "120 MB downloaded" in queue UI, updating in real-time |
| 2.3 | D6 | `Download.kt`, `DownloadManager.kt`, `DownloadWorker.kt`, `DownloadQueueScreen.kt` | Add `PAUSED` to State enum. Add `pauseDownload()` / `resumeDownload()` / `pauseAll()` / `resumeAll()` to DownloadManager. In DownloadWorker, skip PAUSED downloads. Add pause/resume buttons to queue UI |
| 2.4 | D7 | `DetailScreen.kt` | Add "Download all" button at the top of the episodes section. Include a filter option (all / unwatched only). Wire to `DetailViewModel.downloadAllEpisodes()` |
| 2.5 | D8 | `DetailScreen.kt`, new bottom sheet | Add `Modifier.combinedClickable(onLongClick = ...)` to episode rows. Create a `ModalBottomSheet` with: Download / Cancel download / Delete download / Play downloaded |
| 2.6 | D10 | `DownloadsSettingsScreen.kt` | Add concurrent downloads selector (1-4 stepper or dropdown). Wire to `DownloadPreferences.maxConcurrentDownloads()` |

**Verification:**
- Progress bar matches actual video length (D4)
- File size shows and updates during download (D5)
- Pause a download → it stops; resume → it continues (D6)
- "Download all" button downloads all episodes (D7)
- Long-press an episode → bottom sheet appears with options (D8)
- Concurrent downloads selector works in settings (D10)
- Send `ntfy.sh` notification to `TASKISDONE`

---

### Phase 3: UX polish

**Goal:** Make the download experience smooth and handle edge cases.

| Task | Issue | Files | What to do |
|---|---|---|---|
| 3.1 | D9 | `DownloadPreferences.kt`, `DetailScreen.kt`, `DetailsSettingsScreen.kt` | Add download button placement preference (inside synopsis / left side / right side / hidden). Implement the configurable placement logic in DetailScreen. Add the setting to detail settings |
| 3.2 | D14 | `DownloadQueueScreen.kt`, `DownloadWorker.kt` | Track download speed (bytes/sec over a rolling window). Display "2.3 MB/s" in queue rows |
| 3.3 | D17 | `DownloadStore.kt`, `DownloadManager.kt` | Make `add()` return whether the entry was actually added (vs. deduplicated). Surface "already in queue" feedback to the user (toast or snackbar) |
| 3.4 | D13 | `DownloadWorker.kt` | Register a `NetworkCallback` during download. If WiFi drops and "WiFi only" is set, pause the download. Resume when WiFi reconnects |
| 3.5 | D12 | `DownloadProvider.kt`, `DownloadManager.kt`, UI | Detect when SAF folder is revoked (null return). Show a "Storage not available" banner. Prompt user to re-select a folder |
| 3.6 | D16 | new `DownloadCache.kt`, `DownloadManager.kt` | Create in-memory index of downloaded episodes (episodeUrl → bool). Populate on startup by scanning the downloads directory. Use for fast `isEpisodeDownloaded()` lookups instead of SAF listing |

**Verification:**
- Download button appears in the configured location (D9)
- Download speed shows and updates (D14)
- Re-tapping download on a queued episode shows feedback (D17)
- WiFi drop pauses download if "WiFi only" is set (D13)
- Revoking storage shows a banner (D12)
- Episode rows load fast even with 100+ downloaded episodes (D16)
- Send `ntfy.sh` notification to `TASKISDONE`

---

### Phase 4: Advanced features (v2 — deferred)

**Goal:** Features explicitly deferred by the user. Will be planned in detail in a future session.

| Feature | Notes |
|---|---|
| Auto-download new episodes | With category filters. User interested — discuss implementation next session |
| Auto-download next episode while watching | User interested — discuss next session |
| Delete after watching | NO auto-delete. Confirm dialog + 10-20s undo. Advanced rules (next-next episode). Never unless configured |
| Subtitle handling options | Keep separate / join / both. User wants to decide in next planning phase |
| Partial download resume (D11) | Architecturally difficult with FFmpeg copy-muxing. May need segment-based download approach |
| External downloader integration | 1DM/ADM — deferred |
| Download scheduler | Specific hours only — deferred |

---

## 5. File-by-file change summary

### New files to create

| File | Phase | Purpose |
|---|---|---|
| `download/DownloadNotifier.kt` | 1 | Progress + error notification wrapper |
| `download/DownloadCache.kt` | 3 | In-memory index for fast `isEpisodeDownloaded()` lookups |

### Existing files to modify

| File | Phase | Changes |
|---|---|---|
| `download/DownloadWorker.kt` | 1, 2 | Fix concurrency (D1), add foreground service (D2), real progress (D4), file size (D5), pause checking (D6), network monitoring (D3.4) |
| `download/Download.kt` | 2 | Add `PAUSED` state (D6) |
| `download/DownloadManager.kt` | 2, 3 | Add pause/resume methods (D6), add() feedback (D17), storage-revoked detection (D12) |
| `download/DownloadStore.kt` | 3 | Make add() return bool (D17) |
| `download/DownloadPreferences.kt` | 3 | Add download button placement pref (D9) |
| `data/notification/Notifications.kt` | 1 | Add download channel constants (D3) |
| `App.kt` | 1 | Create notification channels (D3) |
| `AndroidManifest.xml` | 1 | Add FOREGROUND_SERVICE permissions (D2) |
| `di/AppModule.kt` | 1, 3 | Register DownloadNotifier, DownloadCache |
| `ui/download/DownloadQueueScreen.kt` | 2, 3 | Pause/resume buttons (D6), file size display (D5), speed indicator (D14) |
| `ui/detail/DetailScreen.kt` | 2, 3 | "Download all" button (D7), long-press menu (D8), configurable button placement (D9) |
| `ui/detail/DetailViewModel.kt` | 2 | Wire downloadAllEpisodes to UI (D7) |
| `ui/settings/DownloadsSettingsScreen.kt` | 2 | Concurrent downloads selector (D10) |
| `ui/settings/DetailsSettingsScreen.kt` | 3 | Download button placement setting (D9) |

---

## 6. Verification checklist (per phase)

Before moving to the next phase, ALL of these must pass:

- [ ] `./gradlew :app:compileDebugKotlin` succeeds (no compile errors)
- [ ] GitHub Actions build succeeds (green checkmark)
- [ ] `ntfy.sh` notification sent to `TASKISDONE`
- [ ] APK installs without crashing
- [ ] The specific feature for this phase works end-to-end on-device (or as close as we can get in the sandbox)
- [ ] `WORKLOG.md` updated with what was done
- [ ] No new known issues introduced (if any, add to `KNOWN-ISSUES.md` on `main`)

---

## 7. Open questions for user

These need clarification before or during implementation:

### Q1: plan.md placement
This `plan.md` is on the `player-experiment` branch (following the "work on player-experiment" rule). The original `DOWNLOAD-PLAN.md` is on `main`. Would you prefer this `plan.md` to also be on `main` for easier access, or keep it on `player-experiment` since it's the active working document?

### Q2: Partial download resume (D11)
You said: "if there are some issues and half of the video is downloaded but the other half is not downloaded, then that needs to be handled properly too." 

The current approach uses FFmpeg with `-c copy` (no re-encoding) to mux video + subtitles into one `.mkv`. FFmpeg **cannot resume** a copy-mux halfway — if it fails at 90%, it must restart from 0%.

Options:
- **(a)** Accept the restart-from-0 limitation (same as aniyomi). On retry, delete the tmp file and restart. Simple, reliable.
- **(b)** Download the video stream to a separate file first (HTTP download with byte-range resume), then run FFmpeg to mux. More complex but supports resume.
- **(c)** Use FFmpeg's segment support (download in 30-second chunks, then concatenate). Most complex.

Which approach do you prefer? (a) is the simplest and matches aniyomi.

### Q3: Download button placement — default location
You said the default should be "inside the synopsis area" and it should move depending on synopsis position (right vs below). 

Currently, the download button is on each **episode row** (not in the synopsis area). Do you want:
- **(a)** Keep download buttons on episode rows AND add one in the synopsis area (for "download all" or the current episode)?
- **(b)** Move the download button OFF the episode rows and INTO the synopsis area only?
- **(c)** Keep download buttons on episode rows, and the "synopsis area" placement refers to where the "Download all" button goes?

I want to make sure I understand where you want the button before implementing D9.

### Q4: "Download all" filter
You mentioned "download all episodes at the top or configure the unwatched ones." Should the "Download all" button:
- **(a)** Have a dropdown/menu: "Download all" / "Download unwatched" / "Download next 5"?
- **(b)** Be a single button that downloads all, with the filter configurable in settings?
- **(c)** Be two buttons: "Download all" + "Download unwatched"?

### Q5: Phase sequencing confirmation
The plan sequences as: Phase 1 (critical bugs) → Phase 2 (core features) → Phase 3 (polish) → Phase 4 (v2 deferred). 

Do you agree with this order, or would you prefer to tackle some Phase 2 features (like the long-press menu or "Download all" button) before Phase 1 (foreground service + notifications)? The user-facing features are more visible, but the critical bugs mean downloads don't work reliably.

---

## 8. What happens next

1. **User reviews this plan** and answers the open questions (Q1-Q5)
2. **User confirms** the phase sequencing and priorities
3. **I begin Phase 1** (critical bugs: D1, D2, D3) — one issue at a time, following the error-handling protocol
4. **After each task:** build → verify → `ntfy.sh` → update worklog → move to next task
5. **After each phase:** user reviews → approve or adjust → move to next phase

**I will NOT start implementing until the user confirms the plan and answers the open questions.**
