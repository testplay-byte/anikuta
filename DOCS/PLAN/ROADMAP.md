# Roadmap — Phased Build Plan

> The sequence in which we build ANI-KUTA. Each phase has a clear goal and
> deliverable. No phase starts until the previous is verified.
>
> Principle: **get a thin vertical slice working end-to-end first, then thicken it.**

---

## Phase 0 — Foundation (current)

**Goal:** Planning + decisions + project setup. No app code yet.

- [x] aniyomi copied into `REFERENCE/`.
- [x] Reference docs complete (modules, app structure, architecture, 8 subsystems, dual-model analysis).
- [x] 4 build decisions **decided** (D1 selective copy-paste, D2 anime-only, D3 keep Injekt, D4 keep SQLDelight).
- [x] `DOCS/APP/` folder set up.
- [x] `DOCS/PLAN/` folder set up (this doc + 8 planning docs).
- [x] Plan subpage on the web (visual planning dashboard).
- [x] Supabase project set up (creds stored gitignored).
- [x] Extension repo + AniKoto 180 extension recorded.
- [x] 4 designs documented (Material 3, Dark Neon, Neobrutalism, Coffee Notebook).
- [x] Module structure + package layout + build environment planned.
- [ ] minSdk (26), module structure (user to confirm), GitHub Actions workflow — final confirm.

**Deliverable:** A documented, decided, planned foundation.

---

## Phase 1 — Skeleton app + onboarding (builds + runs)

**Goal:** An Android app that builds (via GitHub Actions), launches, runs the
7-step onboarding wizard, and lands on an empty home screen. Backend wired.
Material 3 design only (fallback for the other 3 designs later).

**Build target:** ARM64-v8a debug APK, built on GitHub Actions. minSdk 26.

- [ ] Create the Gradle project (5 modules: `:app`, `:core`, `:data`, `:domain`, `:source-api`).
- [ ] Set up GitHub Actions workflow (`.github/workflows/build-apk.yml`).
- [ ] Copy + adapt Injekt DI wiring (anime bindings only) → `:app` `app.anikuta.di`.
- [ ] Copy + adapt anime SQLDelight DB → `:data` `app.anikuta.data.db`.
- [ ] Copy + adapt `AnimeSourceManager` + `AnimeExtensionManager` → `:app`.
- [ ] Material 3 theme scaffold → `:app` `app.anikuta.ui.theme.material3`.
- [ ] 7-step onboarding wizard (Welcome, Permissions, Storage, Extension, Backup restore, Design, All set).
- [ ] Navigation shell (bottom nav: Home, Library, History, Search, More).
- [ ] App launches → onboarding (first boot) → empty home screen.
- [ ] GitHub Actions builds a working ARM64-v8a debug APK.

**Deliverable:** An APK the user can install, run onboarding, and land on an
empty home. Backend (DI, DB, extension system) is live. No streaming yet.

---

## Phase 2 — AniList layer + first UI

**Goal:** The home page pulls data from AniList and displays it (one design).

- [ ] AniList GraphQL client (queries: trending, popular, schedules, genres).
- [ ] AniList data models + mappers.
- [ ] Home page UI (Design 1 "Classic") — all 6 sections.
- [ ] Navigation shell (bottom nav — even if other tabs are empty).
- [ ] Tap a card → opens a placeholder detail page.

**Deliverable:** A home page that shows real AniList data. One design. No
streaming yet.

---

## Phase 3 — Detail page + episode list

**Goal:** The detail page shows AniList metadata + aniyomi extension episodes.

- [ ] AniyomiSourceBridge (AniList anime → extension lookup).
- [ ] Detail page UI (Design 1): hero, description, episode list.
- [ ] Episode list populated from the matched extension.
- [ ] Save/share buttons work (save → library, share → system sheet).

**Deliverable:** A detail page that merges AniList metadata + extension
episodes. Still no streaming.

---

## Phase 4 — Player (streaming works end-to-end)

**Goal:** The user can watch an episode.

- [ ] Wire aniyomi's PlayerActivity + PlayerViewModel (MPV).
- [ ] Episode tap → player opens → extension resolves stream → video plays.
- [ ] Player controls (seek, play/pause, skip, tracks) work.
- [ ] Progress saved to DB.
- [ ] Player reskinned for Design 1 (or wrapped as-is — TBD).

**Deliverable:** End-to-end streaming. Home → detail → play. The thin vertical
slice is complete.

---

## Phase 5 — Library + History + Search

**Goal:** The other core pages work.

- [ ] Library page (saved anime, categories).
- [ ] History page (watch progress, continue watching).
- [ ] Search page (AniList search by name).
- [ ] Settings page (basic — language, AniList login, player defaults).

**Deliverable:** A usable anime app. Home → detail → play → save → resume.

---

## Phase 6 — App Functionality + Polish (current)

**Goal:** Make the app fully functional and polished. Extension management,
AniList tracking, video downloads, settings reorganization, player UX.

- [ ] Settings reorganization (6 subpages: General, Player, Extensions,
      Downloads, Tracking, About).
- [ ] Player UX: preload before opening player, loading state, resume prompt.
- [ ] Extension management: install/uninstall/update from within the app.
- [ ] AniList tracking: OAuth login + progress sync + anime status.
- [ ] Video downloads: WorkManager queue + FFmpeg mux + offline playback.
- [ ] General polish: error states, empty states, skeletons, performance.

**Deliverable:** A fully functional anime app — extensions manageable,
progress tracked, episodes downloadable, settings organized.

---

## Phase 7 — (absorbed into Phase 6)

Downloads + Extensions management moved to Phase 6 (tasks 6.1-6.16).

---

## Phase 8 — The 4 designs + theming (moved from Phase 6)

**Goal:** The customization system lands.

- [ ] Design abstraction (DesignSpec interface + DesignProvider).
- [ ] Design 2 "Dark Neon", Design 3 "Neobrutalism", Design 4 "Coffee Notebook".
- [ ] Theme system (light/dark/AMOLED + accent presets).
- [ ] Custom theming panel (limited overrides).
- [ ] Settings: design picker, theme picker, accent picker.
- [ ] Section show/hide on home page.
- [ ] Player gestures (swipe volume/brightness/seek) + player settings panels.
- [ ] PiP, media session, AniSkip, screenshots, chapters.

**Deliverable:** The user can pick from 4 designs + themes + accents. Player
is fully featured.

---

## Phase 9 — Polish + backup + trackers

**Goal:** Production-ready.

- [ ] Backup/restore (anime data).
- [ ] Performance pass (startup, scroll, player).
- [ ] Bug bash + fixes.

**Deliverable:** A polished, releasable anime app.

---

## After launch

- Monthly upstream tracking (see `UPSTREAM-TRACKING.md`).
- Manga addition (if desired) — parallel stack, see `REFERENCE-DOCS/ANALYSIS-DUAL-MODEL.md`.
- New features as prioritized.

---

## Open questions (roadmap-level)

- [ ] Is the phase order right? (e.g. should downloads come before the 4 designs?)
- [ ] Phase 6 (4 designs): build all 4 at once, or 1 per phase?
- [ ] Should we do a "design preview" subpage on the web to mock the 4 designs before building?

---

_Last updated: Session 8 (initial draft). Refined as each phase approaches._
