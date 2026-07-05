# Architecture

High-level architecture documentation for the **ANI-KUTA** Android app.

ANI-KUTA is a copy (not a git fork) of the open-source app **aniyomi**
(https://github.com/aniyomiorg/aniyomi). We keep aniyomi as a read-only
reference and build our own working code (`app/`, planned) on top of the
lessons and reusable parts we identify there.

## Core principles

### 1. UI / Logic separation (very important)

Backend logic, processing, and core workings are **fully decoupled** from the
UI/UX layer. The same logic must be reusable anywhere, and the UI must be
swappable independently. The long-term goal is a highly customizable UI
(multiple design languages, themes) without touching the logic layer.

**Concrete example (illustrative — actual module layout is TODO until
development starts):**

- Logic layer: `app/data/` and `app/domain/` hold data models, repositories,
  and use-cases. They depend only on each other and on platform libraries,
  never on UI classes.
- UI layer: `app/ui/<feature>/` holds Compose screens / ViewModels. A ViewModel
  calls a use-case from the logic layer; it does not know how data is fetched.
- Swapping the UI (e.g. replacing a Compose screen with a different theme or a
  totally different design language) must not require touching `app/data/` or
  `app/domain/`.

When reviewing any new code, ask: *"Could I reuse this logic with a completely
different UI?"* If the answer is no, refactor.

### 2. Feature-based folders

Each feature gets its own folder under the relevant top-level module. A
"feature" is a coherent slice of user-facing functionality (e.g. library,
player, tracker, downloads). Cross-feature dependencies go through clearly
defined interfaces, not direct imports of internal classes.

### 3. Reference-folder strategy

- **`REFERENCE/`** — a pristine, READ-ONLY copy of the aniyomi upstream
  source. **Never edited.** When we refresh upstream, the whole copy is
  replaced.
- **`REFERENCE-STAGING/`** — landing zone for a fresh copy of aniyomi when
  reviewing upstream updates. We diff `REFERENCE-STAGING/` against
  `REFERENCE/`, review the changes, and promote the parts we want into our
  working code (NOT into `REFERENCE/`). After the review is complete, the
  staging copy is discarded and `REFERENCE/` is replaced wholesale with the
  new version.

This keeps a clear "upstream source vs. our work" separation and makes it easy
to see exactly what we have changed.

### 4. Modular-complexity rule

No single file should be too large. Each file has one clear responsibility
("this file does X"). Long or complex tasks are split into multiple
functions/files. Everything is documented — if a file's purpose is not obvious
from its name and a short header comment, it is not done.

### 5. GitHub backup strategy

`MEMORY/` and `DOCS/` are pushed to GitHub regularly so context survives
sandbox resets. If the sandbox resets, the user re-links the GitHub repo and
the agent reads `MEMORY/` to restore full context and resume work.

`MEMORY/CREDENTIALS/` (including the GitHub PAT) is gitignored and never
committed.

## Diagrams

Diagrams will be added here as the project structure takes shape. The live
preview web page (Next.js, route `/`) also renders a visual folder mind map
that mirrors the structure described in `DOCS/NAVIGATION-GUIDE.md`.

Planned diagrams (TODO):

- Module dependency graph (logic layer vs. UI layer).
- Reference-folder refresh flow (REFERENCE-STAGING → diff → promote → replace
  REFERENCE).
- Feature folder layout once `app/` exists.

## Where to go next

- `DOCS/NAVIGATION-GUIDE.md` — top-level repo map.
- `DOCS/REFERENCE-NAVIGATION/NAVIGATION-GUIDE.md` — aniyomi source map
  (template until aniyomi is copied into `REFERENCE/`).
- `MEMORY/CORE-RULES.md` — the rules this architecture is built on.
