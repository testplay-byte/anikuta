# Decisions — ANI-KUTA build

> The architectural decisions for our app, with reasoning. One-line summaries
> live in `MEMORY/DECISIONS/`; deeper reasoning lives here.
>
> The 4 build decisions (D1-D4) were analyzed in
> `DOCS/REFERENCE-DOCS/DECISIONS-ANALYSIS.md`. This file records the final
> choices.

---

## D1 — Package layout: Hybrid

**Decision:** Hybrid layout.
- **Backend layer** (data, domain, source-api, core): mirror aniyomi's package
  names (`eu.kanade.*`, `tachiyomi.*`) so we can copy code with minimal edits
  and track upstream diffs easily.
- **UI layer** (screens, components, theme): use our own `app.anikuta.*`
  packages for a clean custom identity.

**Reasoning:**
- Matches our UI/logic separation rule: the backend is the aniyomi-reusable
  part (mirror it), the UI is our custom part (our own packages).
- Keeps upstream adoption cheap (backend diffs map 1:1).
- Keeps manga re-add cheap (anime backend stays close to aniyomi's structure).
- Gives us a custom UI identity (`app.anikuta.ui.*`).

**Trade-offs accepted:**
- Slight inconsistency (two naming systems). Mitigated by the clear
  backend/UI boundary.
- The UI layer can't copy aniyomi UI code directly (it's a port). Accepted —
  we're building our own 4 designs anyway.

**Linked decisions:** D2 (anime-only re-add depends on backend mirroring),
D3 + D4 (mirroring backend favors keeping Injekt + SQLDelight).

---

## D2 — Anime-only now, add manga later

**Decision:** Ship anime-only. Manga can be added later as a parallel stack.

**Reasoning:**
- The dual-model analysis (`REFERENCE-DOCS/ANALYSIS-DUAL-MODEL.md`) proved
  anime & manga are parallel, not coupled. Anime-only is mechanically clean.
- Smaller, focused codebase → faster to a working app.
- Re-adding manga later is feasible because we mirror the backend (D1) — the
  manga packages can be copied back with minimal porting.

**Trade-offs accepted:**
- Manga users aren't served until manga is added.
- The 5 cross-cutting feeds (Library/History/Updates/Stats/Storage) need
  simplifying to anime-only now, re-generalizing when manga returns. Some
  rework, but bounded.

**Forward-compat:** Keep backup proto's manga field numbers reserved so old
backups survive a future manga re-add.

---

## D3 — DI framework: [PENDING USER DECISION]

**Status:** Awaiting user's decision after DI explanation.

**Options:**
- **Keep Injekt** (recommended) — copy-friendly, works today, niche.
- **Switch to Hilt** — industry standard, costly migration.
- **Switch to Koin** — middle ground.

**Recommendation:** Keep Injekt. We're mirroring the backend (D1), so we can
copy aniyomi's DI wiring directly. Switching DI = rewriting all wiring for no
immediate benefit. If Injekt becomes a pain point, we can revisit later.

> See the chat message (Session 8) for the plain-language DI explanation.

---

## D4 — Persistence: SQLDelight

**Decision:** Keep SQLDelight (aniyomi's choice).

**Reasoning:**
- We're mirroring the data layer (D1), so we copy aniyomi's `.sq` schemas +
  repository implementations directly. Zero migration cost.
- SQLDelight is type-safe, has good multiplatform story, works well.
- Switching to Room = full data-layer rewrite for no immediate benefit.

**Trade-offs accepted:**
- SQLDelight is less widely known than Room. Mitigated by the fact that we're
  copying aniyomi's working data layer (we don't need to be SQLDelight experts
  to use it).
- `.sq` files are less familiar to some contributors. Accepted.

---

## How these decisions interact

- **D1 (hybrid) + D2 (anime-only) + D4 (SQLDelight)** form a coherent "copy
  the backend, build our own UI" strategy.
- **D3 (if keep Injekt)** completes the "copy-friendly backend" picture.
- If D3 were "switch to Hilt", it would partially undermine the copy-friendly
  backend (we'd rewrite DI wiring). Still viable, just costlier.

---

## Other architectural decisions (beyond D1-D4)

These are made/recorded in `MEMORY/DECISIONS/` and detailed here as needed:

- **AniList as discovery layer** — NOT aniyomi's model. Our key differentiator.
  See `PLAN/PRODUCT-VISION.md` + `PLAN/ANIYOMI-INTEGRATION.md`.
- **Compose-first UI** — no legacy Views. aniyomi is mid-migration; we start clean.
- **4-design customization system** — planned from day one. See `PLAN/CUSTOMIZATION.md`.
- **Monthly upstream tracking** — selective adoption. See `PLAN/UPSTREAM-TRACKING.md`.

---

_Last updated: Session 8. D3 pending; all others decided._
