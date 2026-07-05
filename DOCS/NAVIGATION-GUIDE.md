# Navigation Guide — ANI-KUTA repo

This is the top-level map of the **anikuta** repository. Read this first when
you enter the project. It tells you what every top-level folder is for, who
edits it, and where to go next.

- **Repository:** https://github.com/testplay-byte/anikuta
- **App display name:** ANI-KUTA
- **Application ID:** `app.anikuta`
- **Origin:** A copy (not a git fork) of **aniyomi**
  (https://github.com/aniyomiorg/aniyomi).

## Top-level folder map

| Folder | Purpose | Who edits it | Read this first |
|--------|---------|--------------|-----------------|
| `MEMORY/` | Persistent project memory: core rules, context, session logs, decisions, credentials. The "brain" that survives sandbox resets. | Main agent only. Sub-agents must NOT touch `MEMORY/`. | `MEMORY/CORE-RULES.md`, then `MEMORY/PROJECT-CONTEXT.md`, then latest entry in `MEMORY/SESSION-LOGS/`. |
| `DOCS/` | All project documentation (this folder). | Any agent (per scope). | `DOCS/NAVIGATION-GUIDE.md` (this file). |
| `SETUP/` | Environment setup guide for new contributors/sessions. | Documentation agents. | `SETUP/README.md`. |
| `BUILD-APK/` | Where built APK outputs land (renamed with our naming convention). APKs themselves are gitignored; only the README is tracked. | Build/scripts. | `BUILD-APK/README.md`. |
| `REFERENCE/` | Pristine, READ-ONLY copy of the aniyomi upstream source (1,988 files, commit `2f5cf77`). **Never edited, never built from.** Only refreshed by replacing the whole copy. | Nobody edits it. The main agent replaces the whole copy when refreshing upstream. | `DOCS/REFERENCE-DOCS/NAVIGATION-GUIDE.md` (orientation) → then `MODULES.md` / `APP-STRUCTURE.md` / `ARCHITECTURE.md`. |
| `REFERENCE-STAGING/` | Landing zone for a fresh copy of aniyomi when reviewing upstream updates. Diffed against `REFERENCE/`, then good changes get promoted into our working code (NOT into `REFERENCE/`). | Main agent during upstream review. | See "How to diff upstream updates" in `DOCS/REFERENCE-DOCS/NAVIGATION-GUIDE.md`. |
| `app/` (planned) | The Android app source — our actual working code. Added later when development begins. | All agents working on app code. | TODO — does not exist yet. |

## How to navigate as a newcomer

1. **First time on the project?**
   Read `MEMORY/CORE-RULES.md` and `MEMORY/PROJECT-CONTEXT.md` for the rules
   and high-level context.
2. **Starting a new session?**
   Read the latest entry in `MEMORY/SESSION-LOGS/` to see where work left off,
   and check `MEMORY/DECISIONS/` for prior decisions.
3. **Need to know where files live?**
   You are in the right place — see the table above.
4. **Setting up a build environment?**
   Go to `SETUP/README.md`.
5. **Building an APK?**
   Go to `BUILD-APK/README.md`.
6. **Understanding architecture?**
   Go to `DOCS/ARCHITECTURE/README.md` (our project's principles) and
   `DOCS/REFERENCE-DOCS/ARCHITECTURE.md` (how aniyomi is architected).
7. **Looking at aniyomi internals / planning reuse?**
   Go to `DOCS/REFERENCE-DOCS/` — start at `NAVIGATION-GUIDE.md`, then drill
   into `MODULES.md` / `APP-STRUCTURE.md` / `ARCHITECTURE.md`.

## Notes

- The Android app source (`app/`) does not exist yet. It will be added when
  development begins.
- `REFERENCE/` holds the pristine aniyomi copy (commit `2f5cf77`, 1,988 files).
  Its documentation lives in `DOCS/REFERENCE-DOCS/`.
- `MEMORY/CREDENTIALS/` holds secrets (e.g. GitHub PAT). It is gitignored and
  must never be committed or touched by sub-agents.
