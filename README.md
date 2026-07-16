# ANI-KUTA

> An anime streaming app, built **on the foundations of**
> [aniyomi](https://github.com/aniyomiorg/aniyomi) — reusing aniyomi's
> extension system, SQLDelight DB pattern, Injekt DI, and MPV player, but
> using its own techniques in most places.

| Field | Value |
|-------|-------|
| Display name | ANI-KUTA |
| App ID | `app.anikuta` |
| Built on | aniyomi foundations (extension system, SQLDelight, Injekt, MPV) |
| Account | `testplay-byte` (test account — may migrate later) |

---

## ⭐ Start here

| If you are… | Read |
|-------------|------|
| **A new AI agent or contributor** | `DOCS/ENGINEERING/AI-AGENT-ONBOARDING.md` (the 5-minute entry point) |
| **Looking for current status** | `DOCS/CURRENT-STATE.md` (single source of truth) |
| **Need the full technical map** | `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md` |
| **About to make a change** | `DOCS/ENGINEERING/WORKING-RULES.md` (binding rules) |
| **Wondering what's fragile** | `DOCS/ENGINEERING/MODULARIZATION-ASSESSMENT.md` |

---

## What's in this repo

| Folder | Purpose |
|--------|---------|
| `app/` `core/` `data/` `domain/` `source-api/` | **The 5 working Gradle modules** — the actual Android app (Phases 0–7 complete). |
| `DOCS/ENGINEERING/` ⭐ | **Primary engineering docs** — verified technical map, modularization assessment, working rules, AI-agent onboarding. The single source of truth. |
| `DOCS/CURRENT-STATE.md` | ⭐ Single-source-of-truth status snapshot. |
| `MEMORY/` | Session memory, rules, decisions, credentials. Read `MEMORY/CORE-RULES.md` for the project philosophy. (Secrets gitignored.) |
| `DOCS/` | All documentation (planning, reference analysis, design specs, engineering). |
| `SETUP/` | Environment setup + APK signing detail. |
| `BUILD-APK/` | Built APK outputs (binaries gitignored; only README tracked). |
| `REFERENCE/` | Pristine, READ-ONLY copy of aniyomi (commit `2f5cf77`, 1,988 files). **Never edit, never build from.** |
| `REFERENCE-STAGING/` | Landing zone for incoming upstream copies for review (currently empty). |
| `.github/workflows/build-apk.yml` | The only CI workflow (debug APK build). |

---

## How we work

See `DOCS/ENGINEERING/WORKING-RULES.md` for the full rules. Short version:

1. **Understand before acting** — research, then confirm with the user.
2. **Modular complexity** — small, focused, documented files.
3. **Minimize changes** — only modify files that need to change.
4. **One issue at a time** — verify, fix, document, verify again.
5. **Maintain consistency** — follow existing style, naming, architecture.

UI and logic are kept strictly separate (compiler-enforced via the 5-module
Gradle split) so the UI can be changed independently.

---

## Reference strategy

- `REFERENCE/` is a **read-only** snapshot of aniyomi (commit `2f5cf77`). We
  never edit it and never build from it.
- When upstream updates, a fresh copy goes into `REFERENCE-STAGING/`. We diff
  it against `REFERENCE/`, decide what to adopt, apply to our working code
  (`app/`, `core/`, `data/`, `domain/`, `source-api/`), then refresh `REFERENCE/`.

---

## Build & CI

- **Build:** `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
  (arm64-v8a, signed with committed debug keystore).
- **CI:** `.github/workflows/build-apk.yml` builds the debug APK on push to `main`
  (path-filtered), on PRs, and on manual dispatch. Artifact retained 90 days.
  Notifies `ntfy.sh/TASKISDONE` on completion.
- **Signing:** Debug = committed `app/debug.keystore` (alias `debug`, password
  `android`). Release = unsigned (not yet set up). Distribution target: GitHub
  releases (NOT Play Store).
- Full detail: `SETUP/README.md` + `BUILD-APK/README.md` + `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md` §11–12.

---

## Current status

| Phase | Status |
|-------|--------|
| 0 — Foundation | ✅ Done |
| 1 — Skeleton + onboarding | ✅ Done |
| 2 — AniList + home | ✅ Done |
| 3 — Detail page + episodes | ✅ Done |
| 4 — Player (MPV streaming) | ✅ Done + **user-verified on-device** (play, seek, resume all work) |
| 5 — Library / History / Search | ✅ Done |
| 6 — Functionality + Polish | ✅ Done |
| 7 — Backend improvements | ✅ Done (extensions, trust, repos, downloads, caching, video picker, search, filter, floating nav, onboarding) |
| 7.5 — Episode list enhancements | ⏭️ Next (thumbnails, titles, summaries, auto-fetch) |
| 8 — Statistics & watch tracking | Planned |
| 9 — 4 designs + theming | Planned |
| 10 — Final polish | Planned |

> **For the live, always-current status, read `DOCS/CURRENT-STATE.md`.**

**Latest verified build:** `27053e1` — floating bottom nav, all Phase 7 features working.
**Latest main commit:** `ca644ad` (2026-07-16).

**Recovery:** `git clone` this repo + restore gitignored credentials = full
recovery after a sandbox crash. All source, docs, session logs, and the
worklog are on `origin/main`.

---

## Known issues

31 triaged issues in `KNOWN-ISSUES.md` (3 CRITICAL, 4 HIGH, 8 MEDIUM, 16 LOW).
Most are open/deferred. The 3 CRITICAL are player race conditions found in the
Session 44 code review — the player works but is fragile. See
`DOCS/ENGINEERING/MODULARIZATION-ASSESSMENT.md` for the structural debt list.
