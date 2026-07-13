package app.anikuta.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import app.anikuta.MainActivity
import app.anikuta.data.notification.Notifications
import app.anikuta.download.progress.formatSpeed

/**
 * Manages download notifications: progress (foreground service),
 * error, and completion.
 *
 * Channels are created lazily on first use (and also in App.onCreate).
 *
 * The foreground service notification is updated frequently during download
 * to show overall progress. It uses [Notifications.ID_DOWNLOAD_PROGRESS]
 * (a single notification ID reused for all active downloads).
 */
class DownloadNotifier(
    private val context: Context,
) {
    companion object {
        private const val TAG = "DownloadNotifier"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Tracks whether channels have been created (avoids repeated createNotificationChannel calls). */
    @Volatile
    private var channelsCreated = false

    /**
     * Create notification channels. Called from App.onCreate and lazily
     * before posting any notification. Only creates once — subsequent calls are no-ops.
     */
    fun createChannels() {
        if (channelsCreated) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            channelsCreated = true
            return
        }

        val channels = listOf(
            NotificationChannel(
                Notifications.CHANNEL_DOWNLOADER_PROGRESS,
                "Download progress",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Ongoing notification showing download progress"
                setShowBadge(false)
            },
            NotificationChannel(
                Notifications.CHANNEL_DOWNLOADER_ERROR,
                "Download errors",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifications when downloads fail"
            },
            NotificationChannel(
                Notifications.CHANNEL_DOWNLOADER_COMPLETE,
                "Download complete",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications when downloads finish"
            },
        )

        channels.forEach { channel ->
            notificationManager.createNotificationChannel(channel)
        }
        channelsCreated = true
        Log.d(TAG, "createChannels: ✓ created ${channels.size} channels")
    }

    /**
     * Build the foreground service notification for active downloads.
     *
     * Shows: "Downloading: <anime> - <episode>"
     * + progress bar (if determinate) or indeterminate
     * + "X of Y downloads" if multiple
     */
    fun buildProgressNotification(
        activeDownloads: List<Download>,
    ): NotificationCompat.Builder {
        createChannels()

        val title = if (activeDownloads.size == 1) {
            "Downloading: ${activeDownloads[0].animeTitle}"
        } else {
            "Downloading ${activeDownloads.size} episodes"
        }

        // Use the first active download for the progress display
        val primary = activeDownloads.first()
        val content = if (activeDownloads.size == 1) {
            "${primary.episodeName} — ${primary.progress}%"
        } else {
            activeDownloads.joinToString("\n") { "${it.episodeName} (${it.progress}%)" }
        }

        // Overall progress = average of all active downloads
        val overallProgress = if (activeDownloads.isNotEmpty()) {
            activeDownloads.sumOf { it.progress } / activeDownloads.size
        } else 0

        // Speed display
        val speedText = if (activeDownloads.size == 1 && primary.speed > 0) {
            " · ${formatSpeed(primary.speed)}"
        } else ""

        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, Notifications.CHANNEL_DOWNLOADER_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(content + speedText)
            .setProgress(100, overallProgress, overallProgress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tapIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    }

    /**
     * Update the progress notification.
     */
    fun updateProgress(activeDownloads: List<Download>) {
        if (activeDownloads.isEmpty()) return

        val notification = buildProgressNotification(activeDownloads).build()
        notificationManager.notify(Notifications.ID_DOWNLOAD_PROGRESS, notification)
        Log.v(TAG, "updateProgress: ${activeDownloads.size} downloads, " +
            "overall=${activeDownloads.sumOf { it.progress } / activeDownloads.size}%")
    }

    /**
     * Cancel the progress notification (when all downloads finish).
     */
    fun cancelProgress() {
        notificationManager.cancel(Notifications.ID_DOWNLOAD_PROGRESS)
        Log.d(TAG, "cancelProgress: ✓")
    }

    /**
     * Post an error notification for a failed download.
     */
    fun postError(download: Download) {
        createChannels()

        val notificationId = Notifications.ID_DOWNLOAD_ERROR_BASE + download.id.hashCode()

        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_DOWNLOADER_ERROR)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed: ${download.episodeName}")
            .setContentText(download.error ?: "Unknown error")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "${download.animeTitle} — ${download.episodeName}\nError: ${download.error ?: "Unknown"}"
            ))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "postError: ✓ ${download.episodeName} — ${download.error}")
    }

    /**
     * Post a completion notification.
     */
    fun postComplete(download: Download) {
        createChannels()

        val notificationId = Notifications.ID_DOWNLOAD_COMPLETE_BASE + download.id.hashCode()

        val tapIntent = PendingIntent.getActivity(
            context,
            download.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_DOWNLOADER_COMPLETE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText("${download.animeTitle} — ${download.episodeName}")
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()

        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "postComplete: ✓ ${download.episodeName}")
    }
}
