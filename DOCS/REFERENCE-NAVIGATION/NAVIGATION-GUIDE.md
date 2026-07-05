# Reference Navigation Guide — aniyomi source

> **Status: PARTIALLY FILLED.** The top-level module map (§2) is now filled
> from the actual `REFERENCE/` snapshot. Deeper subsystem details (§3) still
> need a code-reading pass — those remain TODO.

This guide is the index into `REFERENCE/` — the pristine, read-only copy of
the aniyomi upstream source. Use it to locate any aniyomi subsystem quickly
and to record what we can reuse or learn from each part.

**Current snapshot:** aniyomi `main` @ `2f5cf77` (2025-11-05).
See `SOURCE-SNAPSHOT.md` for full details.

---

## 1. Overview of aniyomi

aniyomi is an anime/manga streaming and reading app for Android. It is the
anime fork of **Mihon/Tachiyomi** — the long-running open-source manga reader
ecosystem. aniyomi extends the Tachiyomi/Mihon architecture with anime
playback (a video player, episode sources, and anime trackers) alongside the
original manga reading features.

- Upstream repo: https://github.com/aniyomiorg/aniyomi
- Ecosystem: Tachiyomi fork lineage (Mihon is the active manga-only successor;
  aniyomi is the anime + manga fork).
- License: Apache License 2.0 (see `REFERENCE/LICENSE`).
- Language: Kotlin. Build system: Gradle (Kotlin DSL).

What we want from aniyomi (initial focus, to be refined):
- TODO — confirm with user which subsystems we intend to reuse vs. rewrite.

---

## 2. Top-level module map

> Filled from the actual `REFERENCE/` snapshot + `settings.gradle.kts`.

| Path | Purpose | Notes |
|------|---------|-------|
| `app/` | The main Android application module. | Contains the entry point, UI, DI setup. Package roots: `eu/kanade/`, `mihon/`, `aniyomi/`. |
| `core/` | Shared core libraries. | Sub-module: `core/archive`, `core/common`. |
| `core-metadata/` | Metadata handling for entries (anime/manga). | |
| `data/` | Data layer — database, repositories, data models. | Likely SQLDelight (see `libs.plugins.sqldelight` in root build). |
| `domain/` | Domain layer — use cases, domain models. | Clean-architecture style separation. |
| `source-api/` | The source/extension API — defines how third-party sources plug in. | Foundation of the source-extension system. |
| `source-local/` | Local source implementation. | |
| `i18n/` | Shared translations. | |
| `i18n-aniyomi/` | aniyomi-specific translations. | |
| `presentation-core/` | Shared UI/presentation components (Material, design system). | |
| `presentation-widget/` | Home-screen widget UI. | |
| `macrobenchmark/` | Macrobenchmark module for performance testing. | |
| `buildSrc/` | Gradle build logic / plugins. | |
| `gradle/` | Version catalogs (`*.versions.toml`), wrapper. | Catalogs: kotlinx, androidx, compose, aniyomilibs. |
| `fastlane/` | Store listing metadata + screenshots. | |
| `build.gradle.kts` | Root build script. | |
| `settings.gradle.kts` | Defines the 13 included modules (above). | |
| `gradle.properties` | Gradle + Kotlin + Android JVM args. | |
| `gradlew` / `gradlew.bat` | Gradle wrapper. | |

### Gradle modules (from `settings.gradle.kts`)

13 modules are included in the build:
`:app`, `:core-metadata`, `:core:archive`, `:core:common`, `:data`,
`:domain`, `:i18n`, `:i18n-aniyomi`, `:macrobenchmark`,
`:presentation-core`, `:presentation-widget`, `:source-api`, `:source-local`.

### App package roots (inside `app/src/main/java/`)

| Package root | Likely purpose |
|--------------|----------------|
| `eu/kanade/tachiyomi/` | Main app code (legacy Tachiyomi namespace). |
| `eu/kanade/presentation/` | Compose presentation layer. |
| `eu/kanade/domain/` | App-level domain. |
| `eu/kanade/core/` | App-level core. |
| `mihon/` | Mihon-ecosystem features (e.g. `mihon/feature/upcoming`, `mihon/core/migration`, `mihon/core/designsystem`). |
| `aniyomi/` | aniyomi-specific utilities (`aniyomi/util`). |

---

## 3. Key subsystems to document later

> For each subsystem, record: **location** (path in `REFERENCE/`), **purpose**,
> and **what we can reuse / learn**. These need a code-reading pass.

### 3.1 Source extensions system
- Location: `source-api/`, `source-local/`; extension loading likely in `app/`.
- Purpose: how aniyomi loads third-party source/extension plugins to fetch
  catalog and episode/chapter data.
- What we can reuse: TODO (code-reading pass needed).

### 3.2 Player (anime playback)
- Location: TODO — likely under `app/src/main/java/eu/kanade/tachiyomi/` (search for `player`).
- Purpose: the video player, codec handling, seek/track controls.
- What we can reuse: TODO.

### 3.3 Trackers (sync services)
- Location: TODO — search for `track` package.
- Purpose: integration with tracking services (MyAnimeList, AniList, etc.).
- What we can reuse: TODO.

### 3.4 Data layer (database / repositories)
- Location: `data/` module + `domain/` for models.
- Purpose: persistence (SQLDelight), repositories, data models.
- What we can reuse: TODO.

### 3.5 Download manager
- Location: TODO — search for `download` package.
- Purpose: how aniyomi downloads/stores episodes/chapters offline.
- What we can reuse: TODO.

### 3.6 UI themes
- Location: `presentation-core/` (design system) + `mihon/core/designsystem/`.
- Purpose: theming system, Material setup, dark/light variants.
- What we can reuse: TODO.

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

- Coding style / lint config: TODO (check `.editorconfig`, any ktlint/detekt config).
- Package naming: `eu.kanade.*`, `mihon.*`, `aniyomi.*` (observed).
- Dependency injection approach: TODO.
- Kotlin coroutines / Flow usage patterns: TODO.
- Compose vs. legacy Views: TODO.
- Testing conventions: TODO.

---

## 5. How to diff upstream updates

Use this mini-procedure whenever we want to bring in a new aniyomi release.

1. **Take a fresh copy** of the new aniyomi version and place it in
   `REFERENCE-STAGING/` (do NOT touch `REFERENCE/` yet).
2. **Diff** `REFERENCE-STAGING/` against `REFERENCE/`:
   - `diff -qr REFERENCE/ REFERENCE-STAGING/` for a quick file-level list,
     or `git diff --no-index REFERENCE/ REFERENCE-STAGING/` for full content.
3. **Review** the changes by subsystem using the map in section 2 and the
   subsystem notes in section 3. Focus on:
   - security-relevant changes,
   - data-layer / persistence changes (migration implications),
   - player and source-extension changes (compatibility),
   - anything that touches code we have already modified in our working `app/`.
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
8. **Update `SOURCE-SNAPSHOT.md`** with the new commit hash + date, and
   update this guide if the upstream module structure changed.

---

## 6. Open questions

- TODO — do we mirror aniyomi's package layout (`eu.kanade.*`), or restructure
  to fit our feature-folder rule? (Affects how much we can copy vs. rewrite.)
- TODO — confirm license obligations (Apache 2.0) before distributing any APK:
  attribution, NOTICE, license text in the app.
