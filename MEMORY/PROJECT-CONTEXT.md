# Project Context — ANI-KUTA

> High-level, non-technical summary of what we're building.
> Updated as we learn more from the user.

---

## What we're building

- **Name:** ANI-KUTA (display name).
- **GitHub repo:** https://github.com/testplay-byte/anikuta
- **App ID (applicationId):** `app.anikuta`
- **Type:** Android application.
- **Origin:** A **copy** (not a git fork) of the open-source app **aniyomi**.
  - aniyomi source: https://github.com/aniyomiorg/aniyomi
  - aniyomi is the anime fork of the Mihon/Tachiyomi ecosystem
    (anime + manga streaming/reading app, extension-based source system).
- **Fork style:** We copy the whole aniyomi repo into `REFERENCE/` inside our
  repo and build on top of it. `REFERENCE/` stays read-only.

## How we handle upstream updates

- `REFERENCE/` = stable, read-only copy (our trusted snapshot).
- `REFERENCE-STAGING/` = where fresh upstream copies land for review.
- Diff staging vs reference → decide what to adopt → apply to our working code.
- (See `MEMORY/CORE-RULES.md` §5 for the full flow.)

## Backup & continuity

- `MEMORY/` and `DOCS/` are pushed to GitHub regularly.
- **Secrets (GitHub token) are gitignored** — never committed.
- If the sandbox resets, the user re-links the GitHub repo and I read `MEMORY/`
  to restore full context. The token must be re-pasted (it's local-only).

## APK signing (temporary plan)

- Use a **temporary self-signed keystore** with a **stable alias** so signed
  updates can be installed over the previous build **without
  uninstall/reinstall**.
- Keystore + passwords stored in `MEMORY/CREDENTIALS/` (gitignored).
- Full plan + steps: see `SETUP/README.md`.

## Visual progress

- A live web page (route `/`) shows folders, mind map, rules, and status.
- Purpose: help the (non-coder) user see & understand architecture visually.

## Account note

- The repo currently lives on a **test account** (`testplay-byte`).
- May migrate to a permanent account later — keep this in mind for any
  hardcoded URLs or remotes (they may need updating on migration).
- GitHub token: fine-grained PAT, 90-day expiry, all permissions on this repo.
  Stored locally at `MEMORY/CREDENTIALS/github-token.txt` (gitignored).

## Current status

- **Phase:** Player complete (subtitles working), folder selection implemented, settings reorganized. All on `main`.
- **Build:** GitHub Actions (`.github/workflows/build-apk.yml`). JDK 17 + Android SDK. ntfy.sh notifications on completion (topic: `TASKISDONE`).
- **Branch:** `main` is stable (commit `52ed14b`). `player-experiment` preserved for reference.
- **Subtitles:** ✅ Working. Root cause was a fake HTML `subfont.ttf` — replaced with real DejaVu Sans TTF. See `SUBTITLES_FIX.md`.
- **Storage:** ✅ SAF folder selection implemented. `StorageManager` creates `downloads/`, `data/`, `backups/`, `cache/` subdirs. See `STORAGE.md`.
- **Settings:** ✅ Reorganized into hub + subpages. `SelectableOptionCard` + `StyledSegmentedRow` for consistent design language.
- **Next:** Downloads migration (File → UniFile), offline playback (mpv file-path limitation), backup creation/restore.

## Open questions (to confirm with user when relevant)

- [x] Target Android min SDK / compile SDK? → minSdk 26, compileSdk 35
- [x] Languages: Kotlin only? → Yes
- [x] Build system: Gradle KTS? → Yes
- [x] Design language preference for the app UI? → Material 3, matches details-page LayoutSettingsScreen style
- [ ] Release target (Play Store? F-Droid? sideload only)?
- [ ] When to migrate off the test account?

---

_Last updated: Session 30 (2026-07-12). Subtitles fixed, folder selection implemented, settings reorganized, merged to main._
