# Reference Navigation Guide — aniyomi source

> **Status: TEMPLATE.** This file is a structured template. It will be filled
> in once the aniyomi source is copied into `REFERENCE/`. Until then, every
> section below contains TODO markers describing what to record.

This guide is the index into `REFERENCE/` — the pristine, read-only copy of
the aniyomi upstream source. Use it to locate any aniyomi subsystem quickly
and to record what we can reuse or learn from each part.

---

## 1. Overview of aniyomi

> TODO — fill in after copying aniyomi into `REFERENCE/`. Keep this factual
> and brief; do not speculate.

aniyomi is an anime/manga streaming and reading app for Android. It is the
anime fork of **Mihon/Tachiyomi** — the long-running open-source manga reader
ecosystem. aniyomi extends the Tachiyomi/Mihon architecture with anime
playback (a video player, episode sources, and anime trackers) alongside the
original manga reading features.

- Upstream repo: https://github.com/aniyomiorg/aniyomi
- Ecosystem: Tachiyomi fork lineage (Mihon is the active manga-only successor;
  aniyomi is the anime + manga fork).
- License: TODO — record the upstream license so we stay compliant.

What we want from aniyomi (initial focus, to be refined):

- TODO — list the subsystems we actually intend to reuse or adapt.

---

## 2. Top-level module map

> TODO — fill in after copying aniyomi into `REFERENCE/`. For each top-level
> module, record its location and one-line purpose.

| Module path | Purpose | Notes |
|-------------|---------|-------|
| `app/` | TODO | TODO |
| `app/src/main/` | TODO | TODO |
| `source-api/` (or equivalent) | TODO | TODO |
| `data/` | TODO | TODO |
| `domain/` | TODO | TODO |
| `presentation/` or `ui/` | TODO | TODO |
| `macrobenchmark/` | TODO | TODO |
| `core/` | TODO | TODO |
| Gradle / build files | TODO | TODO |

---

## 3. Key subsystems to document later

> For each subsystem, record: **location** (path in `REFERENCE/`), **purpose**,
> and **what we can reuse / learn**.

### 3.1 Source extensions system
- Location: TODO
- Purpose: TODO — how aniyomi loads third-party source/extension plugins to
  fetch catalog and episode/chapter data.
- What we can reuse: TODO

### 3.2 Player (anime playback)
- Location: TODO
- Purpose: TODO — the video player, codec handling, seek/track controls.
- What we can reuse: TODO

### 3.3 Trackers (sync services)
- Location: TODO
- Purpose: TODO — integration with tracking services (e.g. MyAnimeList,
  AniList, etc.).
- What we can reuse: TODO

### 3.4 Data layer (database / repositories)
- Location: TODO
- Purpose: TODO — persistence (likely Room), repositories, data models.
- What we can reuse: TODO

### 3.5 Download manager
- Location: TODO
- Purpose: TODO — how aniyomi downloads and stores episodes/chapters for
  offline use.
- What we can reuse: TODO

### 3.6 UI themes
- Location: TODO
- Purpose: TODO — theming system, Material setup, dark/light variants.
- What we can reuse: TODO

### 3.7 Other subsystems (add as discovered)
- Backup/restore: TODO
- Library & categories: TODO
- Search & catalog browsing: TODO
- Notifications: TODO
- Updates / extension updates: TODO

---

## 4. Conventions to note when reviewing upstream changes

> TODO — fill in after first review pass. Record conventions worth following
> so our reviews are consistent.

- Coding style / lint config: TODO
- Package naming: TODO
- Dependency injection approach: TODO
- Kotlin coroutines / Flow usage patterns: TODO
- Compose vs. legacy Views: TODO
- Testing conventions: TODO

---

## 5. How to diff upstream updates

Use this mini-procedure whenever we want to bring in a new aniyomi release.

1. **Take a fresh copy** of the new aniyomi version and place it in
   `REFERENCE-STAGING/` (do NOT touch `REFERENCE/` yet).
2. **Diff** `REFERENCE-STAGING/` against `REFERENCE/`:
   - `git diff --no-index REFERENCE/ REFERENCE-STAGING/` (or a directory-diff
     tool of your choice).
3. **Review** the changes by subsystem using the map in section 2 and the
   subsystem notes in section 3. Focus on:
   - security-relevant changes,
   - data-layer / persistence changes (migration implications),
   - player and source-extension changes (compatibility),
   - anything that touches code we have already modified in our working
     `app/`.
4. **Decide what to adopt.** For each non-trivial adoption, record a short
   decision entry in `MEMORY/DECISIONS/` (ADR style: context → decision →
   consequence).
5. **Promote into our working code** (`app/`), NOT into `REFERENCE/`. Apply
   and adapt the changes, keeping our UI/logic separation and feature-folder
   rules.
6. **Refresh `REFERENCE/`** by replacing the whole copy with the contents of
   `REFERENCE-STAGING/`. (`REFERENCE/` is only ever refreshed wholesale; it
   is never hand-edited.)
7. **Clear `REFERENCE-STAGING/`** so it is empty until the next review cycle.
8. **Update this guide** if the upstream module structure changed.

---

## 6. Open questions

- TODO — which aniyomi version/tag do we start from?
- TODO — do we mirror aniyomi's package layout, or restructure to fit our
  feature-folder rule?
- TODO — license obligations to record before distributing any APK.
