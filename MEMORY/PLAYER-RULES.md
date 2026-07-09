# Player Development Rules

> **Read this before working on the video player.** This file survives sandbox resets (backed up to GitHub).
> Last updated: Session 28 (Phase 1 complete, starting Phase 2)

---

## Architecture: One Screen, Two Modes

The player is a **single screen** (`PlayerActivity`) with two modes:
- **MINIMIZED**: Portrait. Video (16:9) at top, server/version dropdowns, episodes list below.
- **FULLSCREEN**: Landscape. Video fills screen, overlay controls on top.

### Critical: MPV View Lifecycle
- MPV can only be initialized **ONCE per process**. `BaseMPVView.initialize()` a second time = SIGABRT.
- The `AnikutaMPVView` (via `AndroidView`) is **always present** in the layout — it is NEVER disposed/recreated during mode transitions.
- In MINIMIZED mode, the MPV view fills the entire screen. The bottom portion (below 16:9 video area) is covered by an opaque `Surface` showing dropdowns + episodes list.
- `onDestroy()` calls `MPVLib.destroy()` for full cleanup, allowing fresh `initialize()` on next open.

### Mode Switching
- `PlayerViewModel.setPlayerMode(mode)` changes the state → Compose recomposes with new layout.
- Video keeps playing during transition (MPV view is not touched).
- **Orientation MUST change** when switching modes:
  - MINIMIZED → `SCREEN_ORIENTATION_SENSOR_PORTRAIT`
  - FULLSCREEN → `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`
- The `PlayerActivity` must call `requestedOrientation` when mode changes (not just in `onCreate`).

---

## Track API (MPV)

MPV exposes tracks via the `track-list` property:
- `track-list/count` — number of tracks
- `track-list/N/type` — "audio", "sub", or "video"
- `track-list/N/id` — track ID (int)
- `track-list/N/title` — track title (may be empty)
- `track-list/N/lang` — language code (e.g., "jpn", "eng"; may be empty)

Select tracks:
- `MPVLib.setPropertyInt("sid", trackId)` — set subtitle track (-1 = off)
- `MPVLib.setPropertyInt("aid", trackId)` — set audio track (-1 = off)

The `track-list` property is observed as `MPV_FORMAT_NONE` — changes fire `eventProperty(property: String)` (no value). The Activity calls `view.loadTracks()` and pushes results to `PlayerViewModel`.

---

## Files (Phase 1)

```
app/anikuta/player/
├── PlayerActivity.kt           # Host + MPV lifecycle + PlayerScreen composable
├── PlayerViewModel.kt          # State: mode, episodes, tracks, servers, playback
├── PlayerPreferences.kt        # All player prefs (mode, skip, gestures, etc.)
├── AnikutaMPVView.kt           # MPV wrapper + track API
├── PlayerObserver.kt           # MPV event → Activity callbacks
├── WatchProgressStore.kt       # Resume position persistence
├── PlayerEnums.kt              # PlayerMode, DefaultPlayerView, VideoTrack, etc.
└── controls/
    ├── PlayerControls.kt       # Fullscreen overlay (existing, will be refactored in Phase 2)
    ├── MinimizedControls.kt    # Minimized video overlay (play/pause, seek, maximize)
    ├── ServerVersionDropdowns.kt # Server + audio version dropdown selectors
    ├── EpisodeListView.kt      # Scrollable episodes list
    └── FirstTimePlayerPrompt.kt # First-time view selection dialog
```

---

## Known Issues (to fix in Phase 2)

1. **First-time prompt not wired** — `PlayerActivity.onCreate` reads `defaultPlayerView` but doesn't show the prompt dialog when set to "ask".
2. **Black video in minimized** — MPV view fills entire screen but only top 16:9 should show. The video may not be rendering if the surface is covered.
3. **Server dropdown empty** — `availableServers` is not populated from actual video data.
4. **Audio version switching no-op** — `onAudioVersionSelected` is a placeholder.
5. **Orientation not switching** — `requestedOrientation` only set in `onCreate`, not when toggling modes.

---

## Phases

- **Phase 1** ✅ (complete): Foundation — enums, prefs, VM, track API, minimized controls, dropdowns, episodes list, prompt, settings, combined activity
- **Phase 2** (in progress): Fullscreen mode + transition + lock + auto-hide
- **Phase 3**: Selection sheets (quality, subtitle tracks, audio tracks, server, speed, more)
- **Phase 4**: Gestures + pinch zoom
- **Phase 5**: Subtitle customization
- **Phase 6**: Polish + MPV config + PiP

---

## Notification Rule
- Send ntfy after EACH step (not just each phase)
- Topic: `ntfy.sh/THEANIMEAPPTASKISDONE`
- When ALL phases complete: send 3 consecutive notifications (1 per second)

---

## Design Decisions
- **No Palette API** — dynamic theming uses AniList `coverImage.color` + HSL variant generation
- **Blur stays** — the banner blur on the detail page is a user-requested feature, do NOT remove it
- **Modular** — each component in its own file, easy to find and edit
- **Episode list has separate settings** from the detail page (future: PlayerEpisodePreferences)
- **Server dropdown** drops down from where tapped (not a bottom sheet)
