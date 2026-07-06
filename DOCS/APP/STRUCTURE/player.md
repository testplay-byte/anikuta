# Player System (Phase 4)

> **Selective copy-paste from aniyomi (Decision D1).** This doc records exactly
> what was copied, what was dropped, and why — so each adoption is traceable.

## TL;DR

The aniyomi player is **not a self-contained module**. A bulk copy of its 64
player files (~12 000 lines) produced **2 277 compile errors** because every
file transitively depends on aniyomi's i18n (`AY`/`AYMR` moko strings),
`tachiyomi.presentation.core` UI kit, the custom `tachiyomi.core.preference`
system, the data layer, and the source/episode loader.

ANI-KUTA instead ships a **clean minimal player** (7 files, ~600 lines) that
depends only on the MPV lib + our own `PreferenceStore`. It is verifiable
today via a public-domain sample stream; real episode URLs arrive in Phase 5
when the source system is wired.

## Files

```
app/src/main/java/app/anikuta/player/
├── PlayerPreferences.kt      8 prefs via our PreferenceStore
├── PlayerEnums.kt            PlayerVideoAspect, PlayerLoadingState
├── AnikutaMPVView.kt         thin BaseMPVView wrapper (initOptions/observeProperties/postInitOptions)
├── PlayerObserver.kt         MPVLib.EventObserver + LogObserver -> Callback
├── PlayerViewModel.kt        state surface (position/duration/paused/buffering/error/controlsVisible)
├── PlayerActivity.kt         Compose host (AndroidView), MPV lifecycle, landscape, edge-to-edge
└── controls/
    └── PlayerControls.kt     single M3 Expressive overlay (top bar + center + bottom slider)
```

## What was copied from aniyomi

| aniyomi source | ANI-KUTA target | What we kept | What we dropped |
|---|---|---|---|
| `AniyomiMPVView.kt` (290 lines) | `AnikutaMPVView.kt` (~115) | `BaseMPVView` subclass, `initOptions` (hwdec/cache/profile/speed), `observeProperties`, property accessors (`duration`/`timePos`/`paused`/`volume`) | decoder panels (deband/yuv420p/filters), subtitle options (~20 setOptionString calls), audio options, `TrackDelegate`, `onKey` key mapping, `VideoFilters`, `toColorHexString` |
| `PlayerObserver.kt` | `PlayerObserver.kt` | `EventObserver` + `LogObserver` impls, all `eventProperty` overloads, `efEvent`, `logMessage` | hard `PlayerActivity` reference → replaced with a `Callback` interface (decoupled + testable) |
| `PlayerViewModel.kt` (2059 lines) | `PlayerViewModel.kt` (~90) | playback state surface (position/duration/paused/buffering/error/controlsVisible) | episode pre-fetching, hoster resolution, AniSkip, track selection, chapter sync, progress saving, `Event` flow, backup hooks |
| `PlayerActivity.kt` (1317 lines) | `PlayerActivity.kt` (~200) | MPV lifecycle (`initialize`→`addLogObserver`→`addObserver`→`loadfile`→release), landscape, edge-to-edge, keep-screen-on, system-bar control | PiP, media session, audio focus, orientation-lock pref, key mapping, screenshot, chapter sync, AniSkip, backup hooks, view-binding XML |
| `PlayerEnums.kt` (171 lines) | `PlayerEnums.kt` (~20) | `PlayerVideoAspect`, `PlayerLoadingState` | `StepMode`, `SeekDirection`, `AudioMode`, `SubtitleState`, `Pip` actions |
| `PlayerPreferences.kt` (~40 fields) | `PlayerPreferences.kt` (8 fields) | speed, hwdec, gpu-next, volume boost, audio lang, seek step, brightness, auto-hide | subtitle typography/colors/border/shadow/position, decoder banding/yuv/hwdec presets, gesture prefs, advanced (mpv.conf/input.conf/scripts/fonts), PiP, orientation, internal seek text |
| split controls (~20 files) | `PlayerControls.kt` (~210) | top bar (back+title), center (play/pause+seek±10s+buffering), bottom (slider+time) | TopLeft/TopRight/BottomLeft/BottomRight splits, sheets (quality/audio/subtitle/chapters/screenshot/more), panels (subtitle settings×4, video filters, audio/sub delay), dialogs (episode list, integer picker), `DoubleTapSeekTriangles`, `GestureHandler`, `BrightnessOverlay`, `AutoPlaySwitch`, `CurrentChapter`, `VerticalSliders` |

**Result:** 64 files → 7 files. ~12 000 lines → ~600 lines. 2 277 errors → 0.

## MPV lib API (verified)

The `com.github.aniyomiorg:aniyomi-mpv-lib:1.18.n` artifact exposes (package
`is.xyz.mpv`):

- `BaseMPVView(Context, AttributeSet)` — abstract surface view. Three abstract
  hooks: `initOptions(vo: String)`, `observeProperties()`, `postInitOptions()`.
  Constructor requires a **non-null** `AttributeSet` — ANI-KUTA creates an
  empty one via `Xml.asAttributeSet(Xml.newPullParser())` for the Compose
  `AndroidView` factory.
- `MPVLib` — static façade: `create`, `command(String[])`, `setOptionString`,
  `getProperty{Int,Boolean,Double,String}`, `setProperty{Int,Boolean,Double,String}`,
  `observeProperty(name, format)`, `addObserver`, `removeObserver`,
  `addLogObserver`, `removeLogObserver`.
- `MPVLib.EventObserver` / `MPVLib.LogObserver` — callback interfaces.
- `MPVLib.mpvFormat.MPV_FORMAT_{NONE,STRING,FLAG,INT64,DOUBLE,…}` — observe formats.
- `MPVLib.mpvEventId.MPV_EVENT_{FILE_LOADED,SEEK,PLAYBACK_RESTART,IDLE,…}` — events.
- `MPVLib.mpvLogLevel.MPV_LOG_LEVEL_{FATAL,ERROR,WARN,INFO,…}` — log levels.

## Gotchas encountered (and fixed)

1. **`AttributeSet?` vs `AttributeSet`** — `BaseMPVView`'s constructor is
   non-nullable. Passing `null` from Compose's `AndroidView` factory fails to
   compile. Fix: create an empty `AttributeSet` via `Xml.asAttributeSet(...)`.
2. **`app.anikuta.player.PlayerPreferences` in `AppModule`** — the
   fully-qualified name collided with the module's `app: Application`
   parameter (`app.anikuta` parsed as property access on `app`). Fix: add an
   import and use the short name.
3. **`mapOf` + forward-referenced property + for-loop destructuring** — caused
   a Kotlin resolver cascade ("ambiguous component1/iterator", "unresolved
   observedProps", phantom "missing brace"/"unclosed comment"). The
   `MPVLib.mpvFormat.MPV_FORMAT_*` symbols are correct (verified by inspecting
   the AAR); the failure was the resolver combination. Fix: call
   `MPVLib.observeProperty(name, format)` directly per property — no map, no
   for-loop, no destructuring.
4. **`collectAsStateWithLifecycle`** — requires `lifecycle-runtime-compose`,
   which isn't in our version catalog. The rest of the app uses
   `collectAsState()`; the player matches.

## Launch contract

`PlayerActivity` is launched with two intent extras:

- `EXTRA_VIDEO_URL` (required) — direct stream URL.
- `EXTRA_TITLE` (optional) — shown in the overlay top bar.

Helper: `PlayerActivity.newIntent(context, videoUrl, title)`.

The detail screen's "Play sample" button currently passes a public-domain Big
Buck Bunny stream so the player is verifiable before the source pipeline
exists. **Phase 5** swaps this for the first resolved `Video.videoUrl` from
the extension.

## Deferred to later phases

These aniyomi player features are intentionally absent and tracked as future
work (not bugs):

- **Episode/source loading** — `EpisodeLoader`, `HosterLoader`, hoster
  resolution, quality selection. (Phase 5 — source wiring.)
- **Progress saving** — upsert `AnimeHistory` on pause/exit; resume prompt.
  (Phase 5.)
- **Subtitle settings panels** — typography, colors, border, shadow, position,
  delay, secondary delay. (Phase 6 — settings.)
- **Audio/subtitle track selection** — sheets listing `track-list` entries.
  (Phase 6.)
- **Gestures** — brightness/volume/seek swipe, double-tap seek, pinch zoom.
  (Phase 6.)
- **PiP** — `PictureInPictureParams`, PiP actions. (Phase 7.)
- **Media session + audio focus** — lock-screen controls, focus handling.
  (Phase 7.)
- **AniSkip** — skip intro/outro via skip times API. (Phase 7.)
- **Screenshots + chapters** — `screenshot-directory`, chapter list. (Phase 7.)
- **mpv.conf / input.conf / scripts / fonts** — user customization files.
  (Phase 7.)

## Verification

- GitHub Actions build **#`28812131592`** (commit `f96e8a3`): ✅ success.
- Artifact: `anikuta-debug-arm64-v8a` — 25.9 MB (MPV native libs add ~10 MB
  over the 15.4 MB pre-player build).
- The player compiles, registers in the manifest, and is reachable from the
  detail screen. Runtime playback verification happens on-device once the user
  installs the APK (the sandbox cannot boot Android).
