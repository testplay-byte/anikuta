# Session Logs

One file per session, named `YYYY-MM-DD-SESSION-N.md`.

## Template

```markdown
# Session Log — YYYY-MM-DD (Session N)

## Goal
<what we set out to do this session>

## What happened
- <step 1>
- <step 2>

## Decisions made
- <decision> — because <reason>

## Files touched
- <path> — <why>

## Next session
- <follow-up item>
```

## Index

- `Session 0` — Setup: rules, folder structure, live preview page.
- `Session 1` — Repo setup: all-caps folders, REFERENCE-STAGING, MEMORY/DOCS expansion, GitHub push.
- `Session 2` — Copied aniyomi into REFERENCE/ (snapshot 2f5cf77); filled reference-navigation guide §1-2.
- `Session 3` — Reference documentation: restructured to REFERENCE-DOCS/, wrote MODULES.md + APP-STRUCTURE.md + ARCHITECTURE.md.
- `Session 4` — Deep reference docs: 8 subsystem deep-dives + dual-model coupling analysis + 4-decisions pros/cons.
- `Session 5` — REFERENCE-DOCS mind-map subpage (pan/zoom color-coded canvas, within / route).
- `Session 6` — Mind-map fixes: wheel=zoom, stable clicked node, controls in header, full descriptions.
- `Session 7` — Mind-map v3: centered controls, smooth animations (Framer Motion + CSS), cascading close, comprehensive app-structure tree.
- `Session 8` — Planning: D1/D2/D4 decided, D3 pending; created DOCS/PLAN/ (8 docs) + DOCS/APP/; built Plan subpage (visual dashboard).
- `Session 9` — Caching analysis (aniwatch reference → CACHING-STRATEGY.md); D3 decided (keep Injekt); D1 refined (selective copy-paste); plan subpage updated (caching section, designs, onboarding).
- `Session 10` — 4 designs documented (Material 3, Dark Neon, Neobrutalism, Coffee Notebook); onboarding (6-step wizard); Supabase setup guidance; Injekt backup plan (Koin).
- `Session 11` — Supabase set up (creds stored gitignored, access verified); extension repo + APK recorded; onboarding updated (7 steps + backup restore); AniList login deferred.
- `Session 12` — Module structure (single vs multi, pros/cons) + package layout (what we copy vs build) + build environment (GitHub Actions, arm64-v8a) planned. NO building.
- `Session 13` — Phase 1 plan (15 sub-steps, incremental copy) + build-progress webpage (`/#build`) + APK download page planned. NO implementation — user verifies first.
- `Session 14` — Step 1.1 (Gradle scaffold: 5 modules, Compose, Material 3) + Step 1.2 (GitHub Actions). BUILD SUCCEEDED (Run #3, 15.4 MB APK). APK downloadable.
- `Session 15` — Phase 6 planning: functionality-first (designs → Phase 8); 8 open questions drafted.
- `Session 16` — User answered 8 questions (priority, AniList ID 5338, extension repo, MP4, quality, nav, preload, resume). Plan locked.
- `Session 17` — Phase 6 implementation: all 6 sections (settings reorg, player UX, extensions, tracking, downloads, polish). Build 68c9e02 green.
- `Session 18` — Sandbox restart recovery: cloned from GitHub, restored credentials, rebuilt live preview.
- `Session 19` — Player pipeline fixes: extension URL crash, TLS, 403/headers, error overlay, stale header read. Builds b25c8fa→1d5f7d2 green.
- `Session 20` — User on-device verification (PLAYER FULLY WORKING) + documentation backup + GitHub push.
