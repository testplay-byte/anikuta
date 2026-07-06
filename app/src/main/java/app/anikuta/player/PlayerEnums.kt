package app.anikuta.player

/**
 * ANI-KUTA PlayerEnums — minimal.
 *
 * Selective copy-paste from aniyomi's `PlayerEnums.kt` (D1): only the states a
 * minimal player needs. aniyomi's version adds StepMode, SeekDirection,
 * PlayerVideoAspect, AudioMode, etc. — deferred.
 *
 * Source: REFERENCE/app/.../ui/player/PlayerEnums.kt (reduced).
 */
enum class PlayerVideoAspect {
    /** Original aspect ratio (may letterbox). */
    ORIGINAL,
    /** Stretch to fill the screen. */
    STRETCH,
    /** Crop to fill the screen. */
    CROP,
}

enum class PlayerLoadingState {
    INITIALIZING,
    LOADING,
    READY,
    ERROR,
}
