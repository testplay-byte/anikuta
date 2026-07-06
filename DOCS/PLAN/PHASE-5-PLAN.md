# Phase 5 — Library + History + Search + Source Wiring

> **Status: PLANNING (not started).** This document defines the goal, scope,
> tasks, open questions, and dependencies. Implementation begins after the user
> answers the open questions and gives the go-ahead.
>
> **Principle (from ROADMAP.md):** "get a thin vertical slice working end-to-end
> first, then thicken it." Phase 5 thickens the slice — the player works with a
> test stream; now we make real episodes playable, save progress, and fill in
> the other core pages.

---

## 1. Goal

**A usable anime app.** By the end of Phase 5, the user can:
1. Search AniList for an anime, find it, open its detail page.
2. Tap a **real episode** (resolved from an installed extension) → the player
   plays the actual stream (not a sample).
3. Save anime to the **Library**; see them on the Library page.
4. See **watch progress** on the History page; resume from where they left off.
5. Open basic **Settings** (AniList login toggle, clear cache, about).

**The thin vertical slice is complete:** Home → Search → Detail → Play →
Save → Resume.

---

## 2. Scope — what's IN Phase 5

### 2.1 Source wiring (carries over Phase 4 tasks 4.4 + 4.5)

This is the single most important piece of Phase 5. The player already accepts
any URL and plays it (verified on-device with Big Buck Bunny). What's missing
is the **pipeline that produces a real episode URL from an extension**:

```
Installed extension (AnimeSource)
   ↓ source.getEpisodeList(anime)        → List<SEpisode>
   ↓ source.getVideoList(episode)        → List<Video>
   ↓ video.videoUrl                       → String
   ↓ PlayerActivity.newIntent(ctx, url, title)
   ↓ MPV plays it
```

**Tasks:**
- **5.1 AndroidAnimeSourceManager wiring:** currently a stub with an empty
  `sourceMap`. Must register sources from installed extensions
  (`AnimeExtensionManager.installedExtensions` → each extension's sources →
  `sourceMap[source.id] = source`).
- **5.2 AniyomiSourceBridge / EpisodeLoader:** given an AniList anime, search
  installed extension sources for a matching `SAnime`, then fetch its
  `SEpisode` list. This bridges AniList (discovery) ↔ extension (streaming).
- **5.3 Detail page episode list:** replace the current "No streaming source
  available" + "Play sample" with a real episode list. Each episode → player
  with its resolved video URL.
- **5.4 Progress saving:** on player pause/exit, upsert `AnimeHistory`
  (animeId, episodeId, position, duration). On player open, read last position
  and seek to it (resume). "Continue watching" row on the History page.
- **5.5 Player → detail back navigation:** currently the player is a standalone
  Activity launched from detail. Verify back returns to detail (not home).

### 2.2 Library page (saved anime)

**Already there (copied from aniyomi):**
- `domain/library/anime/` — `LibraryAnime`, `AnimeLibrarySortMode`,
  `LibraryPreferences`, `LibraryDisplayMode`, `Flag`.
- `domain/entries/anime/interactor/` — `GetLibraryAnime`, `GetAnimeFavorites`,
  `SetAnimeSeasonFlags`, etc.
- `data/entries/anime/AnimeRepositoryImpl.kt` — the data-side repo.
- The `animes` SQLDelight table (from Phase 1).
- Detail screen already has a Save (bookmark) button that toggles `_isSaved`
  (currently in-memory only — TODO: persist to DB).

**Tasks:**
- **5.6 Persist save state:** DetailViewModel's `toggleSaved()` must upsert the
  anime into the `animes` table (set `favorite = 1`) via `AnimeRepository`.
  Currently it only flips a `MutableStateFlow<Boolean>` — lost on process death.
- **5.7 LibraryScreen UI:** replace the 14-line placeholder with a real screen:
  - Grid of saved anime (cover, title, episode count, badge for new episodes).
  - Sort dropdown (title, last read, last checked, unread, total episodes).
  - Filter chips (tracking status, source, genre — minimal for now).
  - Empty state: "Your library is empty. Save anime from the detail page."
  - Pull-to-refresh (re-checks for new episodes from extensions).
- **5.8 LibraryViewModel:** loads saved anime from `GetLibraryAnime` interactor,
  exposes `StateFlow<List<LibraryAnime>>`, sort mode, loading/error states.

### 2.3 History page (watch progress)

**Already there (copied from aniyomi):**
- `domain/history/anime/` — `AnimeHistory`, `AnimeHistoryWithRelations`,
  `AnimeHistoryUpdate`, `AnimeHistoryRepository`.
- Interactors: `GetAnimeHistory`, `RemoveAnimeHistory`, `UpsertAnimeHistory`,
  `GetNextEpisodes`.
- `data/history/anime/AnimeHistoryRepositoryImpl.kt` + `AnimeHistoryMapper.kt`.
- The `animehistory` SQLDelight table.

**Tasks:**
- **5.9 HistoryScreen UI:** replace the 14-line placeholder with:
  - "Continue watching" row at top (horizontal carousel of in-progress anime
    with a progress bar overlay + "Resume" button).
  - Chronological list below (grouped by day: Today, Yesterday, This week, etc.).
  - Each row: cover, title, "Episode X · Y min left", tap → resume player.
  - Swipe-to-delete (removes history entry) + "Clear all" overflow.
- **5.10 HistoryViewModel:** loads from `GetAnimeHistory`, exposes
  `StateFlow<List<AnimeHistoryWithRelations>>` grouped by day.
- **5.11 Resume on player open:** PlayerActivity reads the last saved position
  for the episode and seeks to it after `MPV_EVENT_FILE_LOADED`.

### 2.4 Search page (AniList-first)

**Already there:**
- `AniListRepository` already has the GraphQL plumbing (added in Phase 2).
- `SearchScreen.kt` is a 14-line placeholder.

**Tasks:**
- **5.12 AniList search query:** add `searchAnime(query: String): List<AniListAnime>`
  to `AniListRepository` (GraphQL `Page(search: $query, perPage: 25)`).
- **5.13 SearchScreen UI:**
  - Search bar at top (autofocus, debounced query — 400ms).
  - Results grid below (cover, title, format, score, year).
  - Recent searches (stored locally, shown when query is empty).
  - Empty state: "Search for anime by title." / "No results for '$query'."
  - Tap result → detail page.
- **5.14 SearchViewModel:** debounced query → AniList search via CacheManager
  (short TTL, e.g. 5 min). Exposes `StateFlow<SearchState>` (idle/loading/
  success/empty/error).

### 2.5 Settings page (basic — "More" tab)

**Tasks:**
- **5.15 MoreScreen UI:** replace placeholder with a settings list:
  - **AniList login** (deferred to Phase 8 — show "Coming soon" for now).
  - **Clear cache** (clears LocalCache + Supabase cache for current keys).
  - **Player defaults** (speed, hwdec toggle, audio language — reuse
    PlayerPreferences, minimal settings UI).
  - **About** (app version, build, GitHub link).
  - **Extension management** entry (deferred to Phase 7 — show "Coming soon").
  - **Design + theme** entry (deferred to Phase 6 — show "Coming soon").

---

## 3. Scope — what's NOT in Phase 5

These are explicitly deferred (tracked in ROADMAP.md):

- **Player gestures** (swipe for volume/brightness/seek, double-tap seek,
  pinch zoom) — deferred. The player works without them.
- **Player settings panels** (subtitle typography, decoder presets, gesture
  prefs, mpv.conf/scripts/fonts) — Phase 4.6, deferred to a later phase.
- **PiP, media session, AniSkip, screenshots, chapters** — deferred to a
  later phase.
- **Downloads** (offline viewing) — Phase 7.
- **4 designs + theming system** — Phase 6.
- **AniList tracker sync** (login + progress sync to AniList) — Phase 8.
- **Extension install/uninstall UI** — Phase 7. (In Phase 5 we assume the
  user has already installed the AniKoto extension from onboarding.)

---

## 4. Dependencies + risks

### 4.1 The big dependency: a working extension source

**5.1–5.5 all depend on the extension system actually returning a usable
`AnimeSource` that can fetch episode + video lists.** The extension loader +
manager were rewritten in Phase 4.0, but we have NOT verified end-to-end that
an installed extension (AniKoto 180) actually:
- Loads via `DexClassLoader`.
- Registers its `AnimeSource` in `AndroidAnimeSourceManager`.
- Can call `getEpisodeList(anime)` and return episodes.
- Can call `getVideoList(episode)` and return video URLs.

**Risk:** if the extension can't resolve a real anime → episode → video chain
(e.g., the AniKoto source's search doesn't find the anime, or returns no
videos), then 5.1–5.5 can't produce a real stream and the player keeps using
the sample. This is the #1 risk for Phase 5.

**Mitigation:** Step 5.1 includes a verification harness — manually trigger
the extension from a debug screen, log every step, confirm the chain works
before wiring it into the detail page.

### 4.2 Matching AniList anime ↔ extension SAnime

AniList gives us a title + AniList ID. Extensions search by title string and
return `SAnime` objects with their own URLs/IDs. There's no shared ID. aniyomi
matches by title (with fuzzy logic) + user confirmation. We need the same.

**Risk:** false matches (wrong anime) or no matches (extension doesn't have
that anime).

**Mitigation:** show the user a "Select the correct match" dialog when
multiple candidates exist; let them pick. Cache the mapping (AniList ID →
extension SAnime URL) in the `animes` table so we only match once.

### 4.3 SQLDelight schema for history

The `animehistory` table exists (copied in Phase 1). Need to verify the
columns match what `UpsertAnimeHistory` expects. If the schema drifted, we
may need a migration.

---

## 5. Task breakdown (proposed order)

| # | Task | Depends on | Est. complexity |
|---|------|------------|-----------------|
| 5.1 | AndroidAnimeSourceManager wiring + extension verification harness | — | High |
| 5.2 | AniyomiSourceBridge (AniList → extension SAnime match) | 5.1 | Medium |
| 5.3 | Detail page real episode list (replace "Play sample") | 5.1, 5.2 | Medium |
| 5.4 | Progress saving (upsert history on pause/exit) | 5.3 | Medium |
| 5.5 | Resume on player open (seek to last position) | 5.4 | Low |
| 5.6 | Persist save state (DetailViewModel → AnimeRepository) | — | Low |
| 5.7 | LibraryScreen UI (grid + sort + empty state) | 5.6 | Medium |
| 5.8 | LibraryViewModel | 5.6 | Low |
| 5.9 | HistoryScreen UI (continue watching + grouped list) | 5.4 | Medium |
| 5.10 | HistoryViewModel | 5.4 | Low |
| 5.11 | Resume on player open (seek) | 5.4 | Low |
| 5.12 | AniList search query | — | Low |
| 5.13 | SearchScreen UI (search bar + results grid) | 5.12 | Medium |
| 5.14 | SearchViewModel (debounced) | 5.12 | Low |
| 5.15 | MoreScreen (clear cache, player defaults, about) | — | Low |
| 5.16 | Phase 5 verification (end-to-end test) | all | — |

**Suggested execution order:** 5.1 → 5.2 → 5.3 → 5.4 → 5.5 (source + player
chain first — the riskiest part), then 5.6 → 5.7 → 5.8 (library), then
5.9 → 5.10 → 5.11 (history), then 5.12 → 5.13 → 5.14 (search), then 5.15
(settings), then 5.16 (verify).

---

## 6. Open questions for the user

These need your input before implementation starts. (Also shown on the live
preview's Phase 5 section.)

**Q1 — Extension verification:** Do you want me to add a hidden "Debug /
Extension test" screen (accessible from Settings → long-press version) that
lets us manually trigger an extension's search + episode list + video list
and log every step? This would make diagnosing the AniKoto 180 extension
chain much easier. (Recommended: yes.)

**Q2 — AniList ↔ extension matching UI:** When you open an anime's detail
page and we search the extension for it, what should happen if (a) there's
exactly one match, (b) there are multiple matches, (c) there are no matches?
My proposal: (a) auto-select + show a small "matched from [source]" badge,
(b) show a "Select the correct anime" bottom sheet with covers, (c) show
"Not available on installed extensions" + a button to install more. Agree?

**Q3 — History retention:** How long should we keep watch history? Options:
(a) forever, (b) last 100 items, (c) last 30 days, (d) user-configurable in
settings. My proposal: (a) forever, with a "Clear history" button in the
overflow menu. Agree?

**Q4 — Library sort + filter scope for Phase 5:** aniyomi's library has
~10 sort modes + many filters (by source, status, genre, tracking, etc.).
For Phase 5 I propose a minimal set: sort by (Title, Last watched, Unread
episodes) + no filters (filters come in a later phase). Or do you want more
upfront?

**Q5 — Search behavior:** Should search be (a) AniList-only (search AniList,
show results, tap → detail), or (b) AniList-first with an extension fallback
tab (if AniList has no results, search installed extensions)? My proposal:
(a) AniList-only for Phase 5 (simpler, faster), extension search comes in
Phase 7. Agree?

**Q6 — Settings scope for Phase 5:** Confirm the MoreScreen should have
just: Clear cache, Player defaults (speed/hwdec/audio lang), About. AniList
login, design picker, and extension management show "Coming soon" badges.
Agree, or do you want something added/removed?

**Q7 — Onboarding permissions:** You mentioned the onboarding permissions
step (notifications + storage) doesn't actually request permissions yet.
Should I fix that as part of Phase 5 (it's a small task), or leave it for a
dedicated polish phase?

**Q8 — Player gestures:** You mentioned swipe-for-volume/brightness is
missing. Should I add basic gestures (vertical swipe left = brightness,
vertical swipe right = volume, horizontal swipe = seek) as part of Phase 5,
or defer to a later player-polish phase? My proposal: defer — Phase 5 is
already large and gestures are isolated work.

---

## 7. How we'll manage Phase 5

- **Same incremental copy-paste (D1):** each task references what's already
  copied from aniyomi (domain/data layers are there) and what we build fresh
  (UI). No bulk copy-paste of aniyomi UI files — those are too tightly coupled
  to aniyomi's i18n/presentation kits (the lesson from the player).
- **UI/logic separation (your core rule):** ViewModels in `app.anikuta.ui.*`,
  interactors/repos in `domain`/`data`. ViewModels never touch SQLDelight or
  network directly — always through interactors.
- **M3 Expressive:** all new screens use the existing `AnikutaTheme`,
  `AnikutaTypography`, `AnikutaShapes`, `AnikutaSprings`. No new design system
  (that's Phase 6).
- **Caching:** search results + library refreshes go through `CacheManager`
  (Local → Supabase → AniList) with short TTLs.
- **Error handling:** every screen has Loading / Success / Error / Empty
  states. No silent failures. The global crash handler (Phase 4) catches
  anything unexpected.
- **GitHub:** each task = one commit. Push after each. Build must be green
  before moving to the next task.
- **Documentation:** each task updates this file's task table (status) +
  appends to `worklog.md`. A new `DOCS/APP/STRUCTURE/library.md`,
  `history.md`, `search.md` will be written as each subsystem completes.
- **ntfy notifications:** sent at the end of each task + when the build goes
  green + if a build fails.

---

## 8. Verification (Phase 5 done = these all pass)

- [ ] Install APK. Onboarding still works. Home still loads AniList data.
- [ ] Search for an anime by title → results appear → tap → detail page.
- [ ] Detail page shows a **real episode list** (not "No streaming source").
- [ ] Tap an episode → player opens → **real episode video plays** (not sample).
- [ ] Pause player, leave, come back → **resumes from where you left off**.
- [ ] Save an anime from detail → appears on **Library** page.
- [ ] Library page: sort works, empty state shows when nothing saved.
- [ ] Watch part of an episode → appears on **History** page with progress.
- [ ] History page: "Continue watching" row + grouped list work.
- [ ] Search page: debounced search, results grid, recent searches.
- [ ] More/Settings: clear cache works, player defaults editable, about shows
      version.
- [ ] No crashes (or if any, the error screen shows with copyable report).

---

_Last updated: Session 18 (Phase 5 planning). Implementation starts after user
answers the open questions._
