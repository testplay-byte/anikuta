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
- [x] 4 build decisions analyzed.
- [ ] 4 build decisions **decided** (D1 hybrid, D2 anime-only confirmed; D3 pending user, D4 SQLDelight).
- [ ] `DOCS/APP/` folder set up.
- [ ] `DOCS/PLAN/` folder set up (this doc).
- [ ] Plan subpage on the web (visual planning dashboard).

**Deliverable:** A documented, decided, planned foundation.

---

## Phase 1 — Skeleton app (builds + runs)

**Goal:** An empty Android app that builds and launches, with the backend
layer wired up (Injekt DI, anime DB, source/extension system from aniyomi).

- [ ] Create the working `app/` Gradle project (hybrid layout).
- [ ] Wire Injekt DI (anime bindings only, from aniyomi's AppModule — pruned).
- [ ] Wire the anime SQLDelight database (`AnimeDatabase`).
- [ ] Wire `AnimeSourceManager` + `AnimeExtensionManager`.
- [ ] App launches to a blank screen without crashing.

**Deliverable:** An APK that installs and launches. Backend is live (DB + DI +
extension system), no UI yet.

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

## Phase 6 — The 4 designs + theming

**Goal:** The customization system lands.

- [ ] Design 2 "Compact", Design 3 "Cinematic", Design 4 "Minimal".
- [ ] Theme system (light/dark/AMOLED + accent presets).
- [ ] Custom theming panel (limited overrides).
- [ ] Settings: design picker, theme picker, accent picker.
- [ ] Section show/hide on home page.

**Deliverable:** The user can pick from 4 designs + themes + accents.

---

## Phase 7 — Downloads + Extensions management

**Goal:** Offline viewing + extension management.

- [ ] Wire `AnimeDownloadManager` (WorkManager + FFmpeg → .mkv).
- [ ] Download UI (queue, progress).
- [ ] Extensions page (install/enable/disable).
- [ ] Offline playback (player reads downloaded files).

**Deliverable:** Downloads work; extensions are manageable.

---

## Phase 8 — Polish + backup + trackers

**Goal:** Production-ready.

- [ ] Backup/restore (anime data).
- [ ] AniList tracker sync (login + progress sync) — if we decide to.
- [ ] Onboarding flow (first launch).
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
