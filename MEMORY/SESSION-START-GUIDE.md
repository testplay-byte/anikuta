# Session Start Guide

> Read this **first** at the start of every new session.
> It tells you exactly what to load into memory before doing anything.

---

## Why this exists

The sandbox sometimes forgets or deletes things. We survive by keeping
everything in `MEMORY/` + `DOCS/` on GitHub. This guide is the reliable
entry point that never changes location.

---

## Step-by-step (do these in order)

1. **Read the rules.**
   - `MEMORY/CORE-RULES.md` — the contract for how we work.

2. **Read the project context.**
   - `MEMORY/PROJECT-CONTEXT.md` — what we're building, links, status.

3. **Read the latest session log.**
   - `MEMORY/SESSION-LOGS/` — find the most recent file. It tells you what
     happened last and what the next step is.

4. **Check past decisions.**
   - `MEMORY/DECISIONS/` — so we don't relitigate settled choices.

5. **Check the top-level repo map.**
   - `DOCS/NAVIGATION-GUIDE.md` — where everything lives.

6. **Check the GitHub token.**
   - `MEMORY/CREDENTIALS/github-token.txt` — if it's missing, ask the user
     to re-paste it. (It is gitignored, so it won't be in the repo backup.)

7. **Confirm the current goal with the user** before making changes.

---

## Working rules (reminder)

- Understand before acting. Never assume.
- Split big tasks into small, documented files.
- One issue at a time. Verify before fixing. Don't hand fixes to sub-agents.
- Summarize when done. Clarify before big/breaking changes.
- Ask questions in batches of 5. Keep wording simple.
- Be honest and direct. Don't sugarcoat.

---

## When you finish a session

1. Update (or create) the session log in `MEMORY/SESSION-LOGS/`.
2. Record any new decisions in `MEMORY/DECISIONS/`.
3. Commit and push `MEMORY/` + `DOCS/` to GitHub.
4. Send the done-notification: `ntfy.sh/THEANIMEAPPTASKISDONE`.
5. Update the live preview web page if the structure/status changed.

---

_Last updated: Session 1._
