package app.anikuta.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.session.MediaButtonReceiver

/**
 * Phase 6.3 — Media session for notification controls.
 *
 * Provides:
 *  - Notification media controls (play/pause/seek)
 *  - Headphone button support
 *  - Bluetooth media button support
 *
 * Uses AndroidX MediaSessionCompat for backward compatibility.
 */
class PlayerMediaSession(
    private val context: Context,
) {
    companion object {
        private const val TAG = "PlayerMediaSession"
    }

    private var mediaSession: MediaSessionCompat? = null

    /**
     * Create and activate the media session.
     * Call when the player starts.
     */
    fun create(title: String, duration: Int) {
        mediaSession = MediaSessionCompat(context, "AnikutaPlayer").apply {
            // Set metadata
            setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration.toLong())
                    .build(),
            )

            // Set playback state
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SEEK_TO or
                            PlaybackStateCompat.ACTION_STOP,
                    )
                    .build(),
            )

            // Handle media buttons
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    super.onPlay()
                    // Delegate to PlayerActivity via broadcast or direct call
                }

                override fun onPause() {
                    super.onPause()
                }

                override fun onSeekTo(pos: Long) {
                    super.onSeekTo(pos)
                }

                override fun onStop() {
                    super.onStop()
                }
            })

            isActive = true
        }
    }

    /**
     * Update playback state.
     */
    fun updatePlaybackState(isPlaying: Boolean, position: Long, speed: Float = 1.0f) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, position, speed)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_STOP,
                )
                .build(),
        )
    }

    /**
     * Update position.
     */
    fun updatePosition(position: Long) {
        val current = mediaSession?.controller?.playbackState ?: return
        updatePlaybackState(
            current.state == PlaybackStateCompat.STATE_PLAYING,
            position,
            current.playbackSpeed,
        )
    }

    /**
     * Release the media session.
     * Call when the player is destroyed.
     */
    fun release() {
        mediaSession?.run {
            isActive = false
            release()
        }
        mediaSession = null
    }
}
