# Reference Navigation (DOCS/REFERENCE-NAVIGATION/)

This subfolder holds the **navigation guide for the `REFERENCE/` folder** —
i.e. the map of the aniyomi upstream source. It tells us where things are in
aniyomi and what we can learn or reuse from each part.

## Status

**Partially populated.** aniyomi has been copied into `REFERENCE/`
(snapshot: `main` @ `2f5cf77`, 2025-11-05). The top-level module map in
`NAVIGATION-GUIDE.md` is filled from the actual snapshot. Deeper subsystem
details (player, trackers, download manager, etc.) still need a code-reading
pass and remain marked TODO.

## Files in this subfolder

| File | Description |
|------|-------------|
| `README.md` | This file — overview of the reference-navigation subfolder. |
| `NAVIGATION-GUIDE.md` | Detailed aniyomi navigation guide. Top-level module map filled; subsystem details still TODO. |
| `SOURCE-SNAPSHOT.md` | Records exactly which aniyomi commit `REFERENCE/` is at, plus the refresh procedure. |

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
