package app.anikuta.data.notification

/**
 * Notification channel IDs + notification IDs for the download system.
 *
 * Channels are created in [app.anikuta.App.onCreate].
 */
object Notifications {
    const val CHANNEL_EXTENSION_UPDATES = "extension_updates"

    // ---- Download channels ----

    /** Low importance — progress updates (updates frequently, silent). */
    const val CHANNEL_DOWNLOADER_PROGRESS = "downloader_progress"

    /** High importance — download errors (user should see these). */
    const val CHANNEL_DOWNLOADER_ERROR = "downloader_error"

    /** Default importance — download completion. */
    const val CHANNEL_DOWNLOADER_COMPLETE = "downloader_complete"

    // ---- Notification IDs ----

    /** Foreground service notification (single, reused for active downloads). */
    const val ID_DOWNLOAD_PROGRESS = -301

    /** Per-download error notifications (offset by download hashCode for uniqueness). */
    const val ID_DOWNLOAD_ERROR_BASE = -400

    /** Per-download completion notifications. */
    const val ID_DOWNLOAD_COMPLETE_BASE = -500
}
