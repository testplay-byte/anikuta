# ANI-KUTA

> An anime app, built as a copy (not a fork) of
> [aniyomi](https://github.com/aniyomiorg/aniyomi).

| Field | Value |
|-------|-------|
| Display name | ANI-KUTA |
| App ID | `app.anikuta` |
| Origin | https://github.com/aniyomiorg/aniyomi (copied, not forked) |
| Account | `testplay-byte` (test account — may migrate later) |

---

## What's in this repo

| Folder | Purpose |
|--------|---------|
| `MEMORY/` | Session memory, rules, decisions, credentials. Read `MEMORY/SESSION-START-GUIDE.md` first. |
| `DOCS/` | All documentation. Start at `DOCS/NAVIGATION-GUIDE.md`. |
| `SETUP/` | Environment setup + APK signing plan. |
| `BUILD-APK/` | Built APK outputs (binaries gitignored). |
| `REFERENCE/` | Pristine, read-only copy of aniyomi. **Never edit.** |
| `REFERENCE-STAGING/` | Incoming upstream copies for review/diffing. |
| `app/` | Working Android app source (5-module Gradle project: `:app`, `:core`, `:data`, `:domain`, `:source-api`). |
| `core/` `data/` `domain/` `source-api/` | The other 4 Gradle modules. |

---

## How we work

See `MEMORY/CORE-RULES.md` for the full rules. Short version:

1. **Understand before acting** — research, then confirm with the user.
2. **Modular complexity** — small, focused, documented files.
3. **Summarize** after each task; clarify before big changes.
4. **One issue at a time** — verify, fix, document, verify again.

UI and logic are kept strictly separate so the UI can be changed independently.

---

## Reference strategy

- `REFERENCE/` is a **read-only** snapshot of aniyomi. We never edit it and
  never build from it.
- When upstream updates, a fresh copy goes into `REFERENCE-STAGING/`. We diff
  it against `REFERENCE/`, decide what to adopt, apply to our working `app/`
  code, then refresh `REFERENCE/`.

---

## Backup

`MEMORY/` and `DOCS/` are pushed to GitHub regularly so context survives
sandbox resets. **Secrets (tokens, keystores) are gitignored and never
committed.**

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
| 6 — Functionality + Polish | ✅ Done (settings, extensions, tracking, downloads, error handling) |
| 7 — Backend improvements | ✅ Done (extensions, trust, repos, downloads, caching, video picker, search, filter, floating nav, onboarding) |
| 7.5 — Episode list enhancements | ⏭️ Next (thumbnails, titles, summaries, auto-fetch) |
| 8 — Statistics & watch tracking | Planned (see DOCS/PLAN/STATISTICS-PLAN.md) |
| 9 — 4 designs + theming | Planned |
| 10 — Final polish | Planned |

**Latest verified build:** `27053e1` — floating bottom nav, all Phase 7 features working.

**Recovery:** `git clone` this repo + restore gitignored credentials = full
recovery after a sandbox crash. All source, docs, session logs, and the
worklog are on `origin/main`.
