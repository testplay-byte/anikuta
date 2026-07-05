# Build APK (BUILD-APK/)

This folder holds **built APK outputs** for the ANI-KUTA app. APKs are large
binaries and are gitignored — only this README is tracked.

## What lives here

Built APK files, renamed to follow our naming convention (see below), copied
here from the Gradle build output directory after each build.

## Naming convention

Every APK copied here follows this pattern:

```
anikuta-<version>-<variant>-<date>-<short-git-sha>.apk
```

- `<version>` — the app version, e.g. `0.1.0` (matches `versionName`).
- `<variant>` — the build variant, e.g. `debug` or `release`.
- `<date>` — build date in `YYYYMMDD` (UTC).
- `<short-git-sha>` — the short git commit hash at build time (7 chars).

Example:

```
anikuta-0.1.0-debug-20250105-a1b2c3d.apk
```

## Build variants

| Variant | Signed with | Use |
|---------|-------------|-----|
| `debug` | Temporary self-signed keystore (stable alias) | Development and on-device testing. The stable signing identity allows installing a new build over the previous one without uninstalling. See `SETUP/README.md` → "APK signing plan". |
| `release` | TODO — proper release signing later | Production builds (planned). |

> The signing keystore is **not** committed. It lives under
> `MEMORY/CREDENTIALS/` (gitignored). See `SETUP/README.md` for the full
> signing plan.

## Gitignore

APK files are gitignored. A `.gitignore` rule covers `*.apk` (and specifically
this folder's binary contents) so only the README is tracked. Do not commit
APKs to the repo — they bloat history and GitHub is the backup for
documentation and memory, not for binaries.

Verify: ensure the `.gitignore` rule for `*.apk` exists; if not, request the
main agent to add it. TODO — confirm rule is in place.

## How to build

### From Android Studio

1. Select the build variant in the `Build Variants` tool window
   (e.g. `app -> debug`).
2. `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
3. Once the build finishes, click "locate" in the notification, or open the
   output directory directly (see below).

### From the command line (Gradle)

From the repo root (where `gradlew` lives once `app/` exists):

```bash
# Debug variant
./gradlew assembleDebug

# Release variant (requires signing config to be set up)
./gradlew assembleRelease
```

Verify: the exact Gradle task names depend on aniyomi's module setup. Confirm
once `REFERENCE/` is copied and `app/` is created. TODO.

## Where build outputs initially land

Gradle writes APKs to the standard output directory:

```
app/build/outputs/apk/<variant>/
```

For example:

```
app/build/outputs/apk/debug/app-debug.apk
```

After a successful build, **copy** the APK from there into this folder
(`BUILD-APK/`) and rename it using the naming convention above. Example:

```bash
cp app/build/outputs/apk/debug/app-debug.apk \
   BUILD-APK/anikuta-0.1.0-debug-$(date -u +%Y%m%d)-$(git rev-parse --short=7 HEAD).apk
```

> TODO — once builds are running, consider adding a small script (e.g.
> `scripts/copy-apk.sh`) to automate the rename+copy step so naming stays
> consistent.

## Related

- `SETUP/README.md` — environment setup and APK signing plan.
- `DOCS/NAVIGATION-GUIDE.md` — top-level repo map.
- `MEMORY/DECISIONS/` — record any decisions about variant/signing changes.
