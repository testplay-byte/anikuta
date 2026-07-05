# Onboarding / Setup Wizard

> The first-boot experience. Required — the user cannot proceed to the app
> until they complete it. 6 steps, short + simple + fun.

---

## The 6 steps

### Step 1 — Welcome / Introduction
- App logo + name (ANI-KUTA).
- One-line tagline.
- "Let's get you set up" → Next.
- Short, welcoming, 1 screen.

### Step 2 — Permissions
- Request necessary Android permissions:
  - **Notifications** (for download/schedule updates).
  - **Storage / Media access** (for downloads — scoped storage).
  - **Network** (implicit, but mention it).
- Each permission: what it's for + grant button.
- "Maybe later" allowed for non-essential (notifications), but storage is
  required for downloads.

### Step 3 — Storage folder selection
- User picks where ANI-KUTA stores downloads + cache.
- Default: `Android/data/app.anikuta/files/` (scoped storage default).
- Option: pick a custom folder (SAF — Storage Access Framework).
- Explain: "This is where downloaded episodes will live."

### Step 4 — Extension selection
- **The critical step.** The user must select at least one extension (primary).
  Can optionally select a second (secondary).
- If the user has no extensions installed:
  - Options to get one:
    - **Install from repo** (user provides a repo URL — we have one in mind, TBD).
    - **Install from APK** (user picks an APK file — we have one in mind, TBD).
    - **Pick a pre-determined extension** (a default we bundle/recommend).
- After installing, the user selects primary (+ optional secondary).
- **Cannot proceed** until at least a primary extension is selected.
- Guidance: "Extensions are how ANI-KUTA finds anime streams. Pick one to get
  started. You can add more later."

### Step 5 — Design selection
- Show the 4 designs (Material 3, Neon, Neobrutalism, Coffee).
- Each as a preview card (mini mockup + name + vibe).
- User taps one to select. Can preview before confirming.
- After selecting: quick theme variant pick (light/dark/AMOLED) + accent color.
- "You can change this anytime in Settings."

### Step 6 — All set! (Quick heads-up)
- "You're ready to watch!"
- Quick tips (2-3 bullets): how to search, how the home page works, where
  settings are.
- "Start watching" button → enters the app (home page).

---

## Rules

- **Required:** the user cannot skip steps 3, 4, 5. They must pick a storage
  folder, select an extension, and pick a design.
- **Skippable:** step 1 (welcome) can be skipped on re-entry; step 2 permissions
  can be deferred (except storage).
- **Re-entry:** if the user force-closes during onboarding, they resume where
  they left off on next launch.
- **After onboarding:** the user can change everything in Settings (design,
  theme, extensions, storage) — onboarding is just the first-time setup.

---

## Technical notes

- Onboarding state: stored in preferences (`onboarding_complete: Boolean`,
  `onboarding_step: Int`).
- The setup wizard is a separate Compose navigation graph, shown when
  `onboarding_complete == false`.
- Extension installation: uses aniyomi's `AnimeExtensionManager` (copied +
  adapted per D1).
- Storage folder: SAF document URI stored in preferences.

---

## Open questions

- [ ] The extension repo URL + APK — user will provide later.
- [ ] Pre-determined/bundled extension — which one?
- [ ] Step 2 permissions: any beyond notifications + storage? (Network is
  implicit on Android.)
- [ ] AniList login: should it be part of onboarding (a step 5.5?), or deferred
  to when the user first uses a tracker feature?
