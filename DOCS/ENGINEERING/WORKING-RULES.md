# WORKING RULES — Primary Engineer Deployment Rules

> The binding rules the primary engineer follows on **every** change to the
> ANI-KUTA repository. These supplement (do not replace) `MEMORY/CORE-RULES.md`.
> Saved here persistently so context survives sandbox resets.
>
> _Established 2026-07-16. Change only by explicit agreement with the user._

---

## A. Understand before changing

1. **Understand before acting.** Analyze → research → comprehend → confirm. Never assume, never guess.
2. **Never modify code you do not understand.** If a file/module is unclear, investigate further before editing; ask the user if still unclear.
3. **Trace the execution flow before editing.** Know who calls what, what state is shared, what persists.
4. **Base every conclusion on actual project files.** Do not rely on memory or assumptions.

## B. Minimize & isolate changes

5. **Minimize changes.** Only modify files that need to change. Touch as little as possible.
6. **Avoid unnecessary refactoring unless requested.** "While I'm here" refactors are forbidden.
7. **Maintain consistency.** Follow existing coding style, file organization, naming, architecture, error handling, and logging conventions.
8. **Consider backward compatibility.** Especially for persisted data (SQLDelight schema, SharedPreferences keys, cache formats) and for the extension binary contract (`eu.kanade.tachiyomi.animesource.*` names — never rename these).

## C. Plan before coding

9. **Think before coding.** For any task, follow this sequence:
   1. Analyze the project.
   2. Determine every file that must change.
   3. Explain the implementation plan.
   4. Mention possible side effects.
   5. Mention any better alternatives if relevant.
   6. Wait for confirmation if the change is significant.
   7. Implement.
   8. Verify the build still works and existing functionality is not broken.
   9. Update documentation if necessary.
10. **Consider performance.** Look for hot paths, allocations, DB queries on the main thread, recomposition triggers.
11. **Consider maintainability.** Keep things future-proof and easy to manage.
12. **Explain major decisions.** When you make an architectural decision, explain why. If there are multiple good solutions, compare them and recommend one.

## D. GitHub Actions / CI safety (high caution)

13. **Pay specific attention to workflow files, build pipelines, APK generation, release automation, secrets, CI failures, and build optimizations.**
14. **Do not break existing CI.** Any change to `app/**`, `core/**`, `data/**`, `domain/**`, `source-api/**`, `gradle/**`, root gradle files, or `.github/workflows/**` triggers the `build-apk.yml` workflow on `main`. Ensure `./gradlew assembleDebug` will pass before pushing to `main`.
15. **The cache key** uses `hashFiles('gradle/libs.versions.toml', 'gradle/wrapper/gradle-wrapper.properties', '**/*.gradle.kts')`. Note the `**/*.gradle.kts` glob also matches `REFERENCE/**/*.gradle.kts` (the committed aniyomi snapshot) — edits to reference build files invalidate the cache. Do not edit `REFERENCE/`.
16. **Single architecture.** CI builds `arm64-v8a` only. Do not assume x86_64/emulator builds without changing `ndk.abiFilters`.
17. **Signing.** Debug builds are signed with the committed `app/debug.keystore` (alias `debug`, passwords `android`). Release builds are **unsigned** (no `signingConfig`). Never commit a release keystore. Never hardcode a real release password.
18. **Secrets are gitignored.** `MEMORY/CREDENTIALS/**`, `*.keystore`, `*.jks`, `*.env`, `secrets.properties`, `keystore.properties` are all gitignored. The Supabase DB password currently committed in `DOCS/APP/STRUCTURE/supabase-schema.md` is a **known security issue** to be redacted + rotated — do not propagate it anywhere.

## E. Communication

19. **If anything is unclear, stop.** Explain what is unclear, ask focused questions, do not blindly guess.
20. **Ask questions in batches of 5** (per `MEMORY/CORE-RULES.md`). Keep wording simple and short.
21. **Be honest and direct.** Do not sugarcoat. Do not blindly agree. If something can't be done, say so and explain why.
22. **Proactively highlight concerns, limits, and risks** before the user asks.
23. **Do not begin implementing new features until explicitly asked.**

## F. Documentation discipline

24. **Document every change** in the appropriate `DOCS/` file.
25. **Keep `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md` current** when structure/build changes.
26. **Update `DOCS/ENGINEERING/MODULARIZATION-ASSESSMENT.md`** when a structural issue is resolved or found.
27. **Do not create stale docs.** If you change behavior, update the doc that describes it in the same change.

## G. Reference folder (read-only)

28. **`REFERENCE/` is pristine aniyomi, READ-ONLY.** Never edit, never build from it. Only refresh by replacing the whole copy during upstream review (via `REFERENCE-STAGING/`).
29. **`REFERENCE-STAGING/`** is the landing zone for incoming upstream copies. Currently empty.

## H. Issue workflow (one at a time)

30. **One issue at a time.** Pick one → understand fully → verify it exists → plan the fix → implement → document → verify → move on. (Per `MEMORY/CORE-RULES.md` Rule 4. Do not hand issue-fixing to sub-agents.)

---

## Quick pre-change checklist

- [ ] Read `MEMORY/CORE-RULES.md` + `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md`.
- [ ] Traced the execution flow of the code I'm about to touch.
- [ ] Listed every file that must change (and confirmed each is necessary).
- [ ] Considered side effects: persisted data? extension contract? CI trigger? performance?
- [ ] Plan explained to the user (if significant) and confirmation received.
- [ ] Will `./gradlew assembleDebug` still pass?
- [ ] Documentation updated if behavior changed.
