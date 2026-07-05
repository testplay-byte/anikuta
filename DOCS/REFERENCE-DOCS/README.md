# REFERENCE-DOCS — aniyomi reference documentation

> This folder holds **all documentation for the `REFERENCE/` folder** (the
> pristine, read-only copy of aniyomi). It tells us where each part of
> aniyomi is, what it does, and how the parts relate.
>
> **Update policy:** these docs are NOT updated regularly. They are updated
> only when aniyomi changes are pulled in (via the `REFERENCE-STAGING/`
> review process) or when we discover something new during development.

---

## Status

**Populated (Session 3).** aniyomi `main` @ `2f5cf77` (2025-11-05) is in
`REFERENCE/`. The module map, app structure, and architecture are documented.
Deeper subsystem details (player internals, tracker protocols, etc.) will be
added as we need them.

---

## Files in this folder

| File | What it covers | Read this when… |
|------|----------------|-----------------|
| `README.md` | This file — index + update policy. | You're new to the reference docs. |
| `SOURCE-SNAPSHOT.md` | Exactly which aniyomi version is in `REFERENCE/`, + the refresh procedure. | You need to know the version, or you're updating the reference. |
| `NAVIGATION-GUIDE.md` | The high-level map: aniyomi overview + top-level module map + how to diff upstream updates. | You want the 5-minute orientation. |
| `MODULES.md` | **The 13 Gradle modules** — each one's location, purpose, key contents, dependencies, and used-by. | You need to find or understand a specific module. |
| `APP-STRUCTURE.md` | The `app/` module's internal package tree (952 files) — every sub-package with its purpose. | You're looking for a specific screen, feature, or file inside the app. |
| `ARCHITECTURE.md` | How the modules fit together — layering, the source/extension system, dual anime/manga model, request lifecycles, DI, persistence, UI architecture. | You want to understand the big picture / how data flows. |

### Recommended reading order

1. `SOURCE-SNAPSHOT.md` — know what version you're reading.
2. `NAVIGATION-GUIDE.md` — 5-minute orientation.
3. `ARCHITECTURE.md` — the big picture (layers, data flow).
4. `MODULES.md` — drill into a specific module.
5. `APP-STRUCTURE.md` — drill into a specific feature inside `app/`.

---

## How this folder is used

1. **Finding something in aniyomi:** start at `NAVIGATION-GUIDE.md`, then
   drill into `MODULES.md` or `APP-STRUCTURE.md`.
2. **Understanding how aniyomi works:** read `ARCHITECTURE.md`.
3. **When aniyomi updates are reviewed** (via `REFERENCE-STAGING/`): update
   these docs if the structure changed, and record the decision in
   `MEMORY/DECISIONS/`. Follow the refresh procedure in `SOURCE-SNAPSHOT.md`.
4. **Treat these docs as the index into `REFERENCE/`** — they should let a
   reader find any aniyomi subsystem quickly without grepping the source.

---

## Related

- `DOCS/NAVIGATION-GUIDE.md` — top-level repo map (where `REFERENCE-DOCS/`
  fits in the whole repo).
- `DOCS/ARCHITECTURE/README.md` — our project's architecture principles
  (UI/logic separation, feature folders, reference-folder strategy).
- `MEMORY/DECISIONS/` — decisions about how aniyomi patterns map to our build.
