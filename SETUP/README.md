# Environment Setup

How to set up a working development environment for the **ANI-KUTA** Android
app. This guide is for a contributor or a fresh sandbox session starting from
a clean machine.

> **Status note:** The Android app source (`app/`) does not exist yet — it
> will be added when development begins. Until then, this guide covers the
> environment and the APK-signing plan so we are ready to build as soon as the
> source lands.

## Prerequisites

| Tool | Requirement | Notes |
|------|-------------|-------|
| Android Studio | Latest stable | Required for IDE support and SDK management. |
| JDK | **JDK 17** baseline | Android Gradle Plugin (AGP) requires JDK 17. Verify the exact AGP version aniyomi uses once `REFERENCE/` is copied — the required JDK may differ; if so, update this guide. |
| Android SDK | Platforms + build-tools matching the project's `compileSdk` / `targetSdk` | Installed via Android Studio's SDK Manager once the project opens. |
| Git | Any recent version | For cloning and pushing `MEMORY/` + `DOCS/` backups. |
| (Optional) `keytool` | Bundled with the JDK | Used for the self-signed APK keystore (see signing plan below). |

Verify after install:

- `java -version` reports JDK 17 (or whatever we settle on).
- `git --version` works.
- Android Studio launches and the Android SDK location is configured.

## Clone & open

1. Clone the repo:
   ```bash
   git clone https://github.com/testplay-byte/anikuta.git
   cd anikuta
   ```
2. Open the `anikuta/` folder in Android Studio:
   `File → Open → select the anikuta folder`.
3. Let Gradle sync complete. Android Studio will prompt to install any missing
   SDK platforms / build-tools — accept.
4. (Once `app/` exists) Select the `app` run configuration and a device/emulator.

> **Do NOT build from `REFERENCE/`.** It is a read-only copy of the aniyomi
> source kept only for reference. Our working code lives under `app/` (to be
> added). Building from `REFERENCE/` is explicitly out of scope.

## Gradle / SDK configuration notes

- **`local.properties` must NOT be committed.** It contains machine-specific
  paths (notably `sdk.dir`). It is covered by `.gitignore`. Do not remove that
  ignore rule.
- **Secrets must NOT be committed.** Keystores, passwords, and tokens live
  under `MEMORY/CREDENTIALS/` (gitignored) and are referenced from the build
  via environment variables or a gitignored properties file. See the signing
  plan below.
- Verify: the project's `compileSdk`, `targetSdk`, `minSdk`, AGP version, and
  Kotlin version are determined by aniyomi's setup. Record the exact values
  here once `REFERENCE/` is copied. TODO.

## APK signing plan (TODO — not yet implemented)

We will temporarily **self-sign** APKs with a stable keystore so that updates
can be installed over the previous version on a device **without** uninstalling
and reinstalling. This is a development convenience, not a release-quality
signing setup.

Why: Android refuses to install an APK whose signing identity differs from the
already-installed app. Using one stable keystore (same key + alias across
builds) keeps the identity stable so successive debug/preview builds replace
each other cleanly.

### Plan (steps to execute when we implement this)

1. **Generate a keystore** with `keytool` (JDK bundled):
   ```bash
   keytool -genkeypair -v \
     -keystore anikuta-debug.keystore \
     -alias anikuta-debug \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -storepass <STOREPASS> -keypass <KEYPASS>
   ```
   Keep the alias (`anikuta-debug`) stable forever — changing it breaks
   update-over-install.
2. **Store the keystore and passwords securely** under
   `MEMORY/CREDENTIALS/` (gitignored, never committed). Record the file paths
   in `MEMORY/CREDENTIALS/README.md` (if present) — but never the passwords
   themselves.
3. **Reference the keystore from `signingConfigs`** in the app's Gradle build
   file via environment variables or a gitignored properties file, e.g.:
   ```kotlin
   signingConfigs {
       create("anikutaDebug") {
           storeFile = file(System.getenv("ANIKUTA_KEYSTORE") ?: "missing.keystore")
           storePassword = System.getenv("ANIKUTA_STORE_PASSWORD") ?: ""
           keyAlias = System.getenv("ANIKUTA_KEY_ALIAS") ?: "anikuta-debug"
           keyPassword = System.getenv("ANIKUTA_KEY_PASSWORD") ?: ""
       }
   }
   ```
4. **Wire the signing config** into the relevant build variants (debug first,
   release later).
5. **Document the passwords location** in `MEMORY/CREDENTIALS/` (managed by
   the main agent — sub-agents must not touch credentials).

> TODO — execute the steps above when we start producing installable APKs.
> Until then, builds that need signing will fall back to AGP's default debug
> signing, which does NOT allow update-over-install across different build
> environments.

## First-run checklist

- [ ] JDK 17 installed and `java -version` confirms it.
- [ ] Android Studio installed.
- [ ] Repo cloned: `git clone https://github.com/testplay-byte/anikuta.git`.
- [ ] Opened in Android Studio; Gradle sync succeeded.
- [ ] Missing SDK components installed via SDK Manager.
- [ ] `local.properties` exists locally and is **not** staged for commit.
- [ ] Read `MEMORY/CORE-RULES.md` and `DOCS/NAVIGATION-GUIDE.md`.
- [ ] (Once `app/` exists) Project builds and an emulator/device is configured.
- [ ] (Once signing is set up) Keystore generated and stored under
      `MEMORY/CREDENTIALS/`; signing config wired in.

## Related

- `DOCS/NAVIGATION-GUIDE.md` — top-level repo map.
- `BUILD-APK/README.md` — how to build APKs and where outputs land.
- `DOCS/ARCHITECTURE/README.md` — architecture principles (incl. reference
  folder strategy).
