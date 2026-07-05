# Reference Navigation (DOCS/REFERENCE-NAVIGATION/)

This subfolder holds the **navigation guide for the `REFERENCE/` folder** —
i.e. the map of the aniyomi upstream source. It tells us where things are in
aniyomi and what we can learn or reuse from each part.

## Status

**TODO — not yet populated.** The aniyomi source has not yet been copied into
`REFERENCE/` (the folder currently only holds a `.gitkeep`). The files in this
subfolder are templates that will be filled in once the copy is made.

## Files in this subfolder

| File | Description |
|------|-------------|
| `README.md` | This file — overview of the reference-navigation subfolder. |
| `NAVIGATION-GUIDE.md` | Template for the detailed aniyomi navigation guide. Filled in after aniyomi is copied into `REFERENCE/`. |

## How this subfolder is used

1. When aniyomi is first copied into `REFERENCE/`, walk the source tree and
   fill in `NAVIGATION-GUIDE.md` (modules, key subsystems, locations,
   reuse notes).
2. When upstream updates are reviewed (via `REFERENCE-STAGING/`), update the
   guide if the structure changed and record the decision in
   `MEMORY/DECISIONS/`.
3. Treat the guide as the index into `REFERENCE/` — it should always let a
   reader find any aniyomi subsystem quickly without grepping the source.

## Related

- `DOCS/NAVIGATION-GUIDE.md` — top-level repo map (includes `REFERENCE/` and
  `REFERENCE-STAGING/`).
- `DOCS/ARCHITECTURE/README.md` — the reference-folder strategy (read-only
  `REFERENCE/` vs. review-only `REFERENCE-STAGING/`).
