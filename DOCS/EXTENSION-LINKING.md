# Extension-to-AniList Linking Architecture

> How the app handles anime from extension sources.
> Last updated: Session 31 (Phase I).

---

## Overview

When a user discovers anime through extension sources (search or browse),
the app needs to decide how to show the detail page. There are two scenarios:

1. **The anime exists on AniList** → show the full AniList detail page (with
   episodes, player, downloads, tracking, etc.)
2. **The anime does NOT exist on AniList** → show a source-only detail page
   (episodes + playback from the extension, but no AniList data)

The linking system handles both cases transparently.

---

## The Flow

```
User taps extension anime result
         │
         ▼
  Check ExtensionLinkStore cache
  (sourceId:animeUrl → AniList ID)
         │
    ┌────┴────┐
    │         │
  HIT       MISS
    │         │
    ▼         ▼
Navigate to  Show SourceLinkingScreen
DetailScreen  (cover + "Processing...")
(anilistId)         │
              Search AniList by title
                    │
              ┌─────┴─────┐
              │           │
           Found       Not Found
              │           │
              ▼           ▼
          Cache link   Navigate to
          Navigate to  SourceDetailScreen
          DetailScreen (extension-only:
                        episodes + playback)
```

---

## Files

| File | Role |
|------|------|
| `ExtensionLinkStore.kt` | SharedPreferences cache: `sourceId:animeUrl` → AniList ID |
| `SourceLinkingScreen.kt` | Loading card + AniList search + auto-link |
| `SourceDetailScreen.kt` | Fallback detail page (episodes + playback from extension) |
| `AnikutaNavGraph.kt` | Routes: `source-link/{...}` and `source-detail-fallback/{...}` |
| `SearchScreen.kt` | Constructs the `source-link` route when extension results are tapped |

---

## ExtensionLinkStore

- **Key format:** `"$sourceId:$animeUrl"` → AniList ID (Int)
- **Persistence:** SharedPreferences (survives app restart)
- **Methods:**
  - `getAniListId(sourceId, animeUrl): Int?` — check if linked
  - `link(sourceId, animeUrl, anilistId)` — cache a link
  - `unlink(sourceId, animeUrl)` — remove a link
- **Reactive:** `changes` Flow for observing link updates

---

## SourceLinkingScreen

Shown when the user taps an extension anime that is NOT yet linked.

**UI:**
1. Anime cover (from the extension search result)
2. Anime name (from the extension search result)
3. Source name ("From: [extension name]")
4. "Processing... Just wait a moment" + spinner
5. "Searching AniList for '[title]'"

**Logic:**
1. Searches AniList by the anime title (`repo.searchAnime(title)`)
2. AniList's `SEARCH_MATCH` sort returns best matches first
3. Picks the first result
4. Caches the link via `ExtensionLinkStore.link()`
5. Navigates to `detail/$anilistId` (the normal AniList DetailScreen)

**On failure (AniList search returns no results):**
1. Shows "Not found on AniList. Opening extension details..."
2. Navigates to `source-detail-fallback/{sourceId}/{animeUrl}/{title}/{thumbnailUrl}`

---

## SourceDetailScreen (Fallback)

Shown when AniList search fails — the anime exists in the extension but not on AniList.

**UI:**
1. Anime cover, title, genres, status, description (from `source.getAnimeDetails()`)
2. Episode list (from `source.getEpisodeList()`)
   - Alternating bg (surfaceContainerLow/High)
   - Episode thumbnail (if available)
   - Episode name + number
   - Loading spinner while resolving videos
3. Tapping an episode:
   - Resolves videos via `source.getVideoList(episode)` or `source.getHosterList()` fallback
   - Launches `PlayerActivity` with the first video URL

**What it does NOT have (vs the AniList DetailScreen):**
- No AniList score, season year, format
- No library bookmark (can't save to library without AniList ID)
- No AniList tracking
- No downloads (requires AniList ID for storage path)
- No watch progress persistence (WatchProgressStore is AniList-keyed)

---

## Future Improvements

- **Re-link option:** If the AniList match was wrong, the user should be able to
  search again and pick a different match (currently the link is permanent)
- **Manual search:** Let the user manually search AniList and pick the right entry
  instead of auto-selecting the first result
- **SourceDetailScreen enhancements:** Add watch progress (keyed by sourceId+url
  instead of AniList ID), downloads, etc.
- **Backup:** Decide how extension-linked anime are handled in the backup system
