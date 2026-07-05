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
