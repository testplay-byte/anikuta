# Decisions Log (ADR-style)

Record key decisions and **why** they were made, so we don't relitigate them.

## Format

```markdown
## Decision <N> — <short title>
- **Date:** YYYY-MM-DD
- **Status:** Proposed | Accepted | Superseded by <N>
- **Context:** <why we needed to decide>
- **Decision:** <what we chose>
- **Consequences:** <trade-offs / impact>
```

## Index

- **Decision 1** — Two reference folders (REFERENCE/ + REFERENCE-STAGING/) — Accepted (Session 1).
- **Decision 2** — GitHub token stored gitignored, never committed — Accepted (Session 1).
- **Decision 3** — All-caps naming for top-level project folders — Accepted (Session 1).
- **Decision 4** — Shallow clone for REFERENCE/ (no git history) — Accepted (Session 2).
- **Decision 5** — Force-add `.idea/icon.png` + `app/.idea/*` (part of aniyomi's committed snapshot) — Accepted (Session 2).
- **Decision 6** — Defer APK signing until we build our own app — Accepted (Session 2).
- **Decision 7 (deferred)** — On test-account migration, rewrite repo URLs in CORE-RULES §0, PROJECT-CONTEXT, CREDENTIALS/README, live-preview data.ts (projectStatus.repoUrl), and anikuta/.git/config remote. — Pending future migration.
- **Decision 8** — REFERENCE-DOCS/ is flattened (no nested REFERENCE-NAVIGATION/ subfolder); old folder removed. — Accepted (Session 3).
- **Decision 9** — Reference docs updated only when aniyomi changes (staging review) or on discovery — NOT regularly. — Accepted (Session 3).
- **Decision 10** — Subsystem docs use a strict 10-section template (consistent across all 9 docs). — Accepted (Session 4).
- **Decision 11** — DECISIONS-ANALYSIS.md is analysis-only; actual build decisions deferred to when we start building, per user. — Accepted (Session 4).
- **Decision 12** — Subpage implementation = client-side view router within `/` (hash-synced), not separate routes (env forbids) nor overlay (user rejected canvas-takeover). — Accepted (Session 5).
- **Decision 13** — Mind-map layout = horizontal tree, parent centered over children, SVG bezier connectors, collapsible, color-coded by 8 categories. — Accepted (Session 5).
- **Decision 14** — Mind-map wheel = zoom-toward-cursor (plain wheel, no modifier); pan is drag-only. — Accepted (Session 6).
- **Decision 15** — Mind-map canvas is controlled: zoom + showLegend in the view; pan + expanded in the canvas; fit() via ref. — Accepted (Session 6).
- **Decision 16** — Mind-map position animation = CSS transition on left/top only; enter/exit = Framer Motion opacity+scale. — Accepted (Session 7).
- **Decision 17** — Cascading close: deepest descendants collapse first (staggered), then clicked node; pan compensation per step; pending cascades cancelled on new interaction. — Accepted (Session 7).
- **Decision 18** — Mind-map data covers the FULL project structure (repo → 13 modules → packages → sub-packages → key files), 7 color categories, every node documented. — Accepted (Session 7).
- **Decision 19 (D1)** — Selective copy-paste with documentation: own `app.anikuta.*` packages throughout; copy needed aniyomi parts in, adapting + documenting each. (Refined from "hybrid" in Session 9.) — Accepted (Session 8, refined Session 9).
- **Decision 20 (D2)** — Anime-only now; manga re-addable later as a parallel stack. — Accepted (Session 8).
- **Decision 21 (D4)** — Keep SQLDelight (copy-friendly, no migration). — Accepted (Session 8).
- **Decision 22** — AniList is the discovery layer (NOT aniyomi's model); aniyomi extensions for streaming only. — Accepted (Session 8).
- **Decision 23** — Compose-first UI (no legacy Views). — Accepted (Session 8).
- **Decision 24** — 4-design customization system planned from day one. — Accepted (Session 8).
- **Decision 25** — Monthly upstream tracking (selective adoption). — Accepted (Session 8).
- **Decision 26 (D3)** — Keep Injekt (copy aniyomi's DI wiring). Limitations (niche, no compile-time checks, less IDE tooling) accepted. — Accepted (Session 9).
- **Decision 27** — 3-step data management: AniList → Supabase → local (SQLDelight), with fallback chain. Learned from aniwatch reference. — Accepted (Session 9).
- **Decision 28** — Extensions: user picks primary + secondary; setup wizard enforces selection on first boot. — Accepted (Session 9).
- **Decision 29** — Search: AniList-first with extension fallback; user-selectable engine in settings. — Accepted (Session 9).
- **Decision 30** — Onboarding/setup wizard is a required first-boot flow (Phase 1 of roadmap). — Accepted (Session 9).
- **Decision 31** — 4 designs finalized: Material 3, Dark Neon, Neobrutalism, Coffee Notebook. All documented in DOCS/PLAN/DESIGNS/. — Accepted (Session 10).
- **Decision 32** — Onboarding = 6-step wizard; steps 3/4/5 required (storage, extension, design). — Accepted (Session 10).
- **Decision 33** — Supabase project name = "anikuta", free tier; set up in Phase 2. — Accepted (Session 10).
- **Decision 34** — Injekt backup plan = migrate to Koin (not Hilt) if Injekt fails; trigger conditions defined. — Accepted (Session 10).
- **Decision 35** — Supabase project created: `anikuta` (Singapore, free tier, RLS + Data API). Credentials stored gitignored. — Accepted (Session 11).
- **Decision 36** — Recommended extension repo: Confused-Creature-180/aniyomi-extensions (pre-added in onboarding). Recommended extension: AniKoto 180. — Accepted (Session 11).
- **Decision 37** — Onboarding = 7 steps (added Backup restore as step 5). AniList login deferred to Settings → Trackers. — Accepted (Session 11).
- **Decision 38** — Supabase = database + cache only (no Auth / user accounts for now). — Accepted (Session 11).
