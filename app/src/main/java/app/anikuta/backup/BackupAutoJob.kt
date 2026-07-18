package app.anikuta.backup

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.anikuta.core.util.system.logcat
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * WorkManager periodic worker for automatic backups.
 *
 * Runs on a schedule (every N hours, configured by [AutoBackupPreferences.frequencyHours]).
 * Creates a backup in the user's chosen format + directory, then prunes old
 * backups to keep only [AutoBackupPreferences.maxBackups] files.
 *
 * ## Constraints
 *  - `NetworkType.NOT_REQUIRED` (backup is local — doesn't need network).
 *  - `RequiresBatteryNotLow(true)` — don't drain the battery.
 *
 * ## Naming convention
 * Auto-backup files are named: `anikuta_auto_YYYY-MM-DD_HH-mm.<ext>` where ext
 * is `.anikuta` or `.tachibk` based on the format preference.
 *
 * ## Pruning
 * After creating a backup, the worker lists all `anikuta_auto_*` files in the
 * target directory, sorts by name (which encodes the timestamp), and deletes
 * all but the newest N (where N = maxBackups).
 *
 * ## Scheduling
 * [schedule] enqueues a `PeriodicWorkRequest` with `UPDATE` policy (if the
 * frequency changed, the existing schedule is updated). [cancel] removes it.
 * Called from [BackupSettingsScreen] when the user changes frequency.
 *
 * Per user decision #4 (2026-07-18): auto-backup with configurable frequency
 * + retention count.
 */
class BackupAutoJob(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "BackupAutoJob"
        private const val WORK_NAME = "AnikutaAutoBackup"

        /**
         * Schedule (or reschedule) the periodic auto-backup worker.
         *
         * @param context application context.
         * @param frequencyHours how often to run (hours). If <= 0, cancels the schedule.
         */
        fun schedule(context: Context, frequencyHours: Int) {
            val workManager = WorkManager.getInstance(context)
            if (frequencyHours <= 0) {
                workManager.cancelUniqueWork(WORK_NAME)
                logcat(LogPriority.DEBUG) { "Auto-backup schedule cancelled (frequency=$frequencyHours)" }
                return
            }

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<BackupAutoJob>(
                frequencyHours.toLong(),
                TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, // update if already scheduled (frequency changed)
                request,
            )
            logcat(LogPriority.DEBUG) { "Auto-backup scheduled: every $frequencyHours hours" }
        }

        /** Cancel the auto-backup schedule (e.g. when frequency set to 0). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            logcat(LogPriority.DEBUG) { "Auto-backup schedule cancelled" }
        }
    }

    override suspend fun doWork(): Result {
        logcat(LogPriority.DEBUG) { "Auto-backup job started" }

        return try {
            val autoPrefs = Injekt.get<AutoBackupPreferences>()
            val backupManager = Injekt.get<BackupManager>()
            val context = applicationContext

            val frequencyHours = autoPrefs.frequencyHours().get()
            if (frequencyHours <= 0) {
                logcat(LogPriority.DEBUG) { "Auto-backup disabled (frequency=0) — skipping" }
                return Result.success()
            }

            val directoryUriStr = autoPrefs.directoryUri().get()
            if (directoryUriStr.isEmpty()) {
                logcat(LogPriority.WARN) { "Auto-backup directory not set — skipping (user must configure in settings)" }
                return Result.success()
            }

            val directoryUri = Uri.parse(directoryUriStr)
            val format = autoPrefs.format().get()
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
            val ext = if (format == "tachibk") "tachibk" else "anikuta"
            val filename = "anikuta_auto_${timestamp}.${ext}"

            // Create the backup file in the target directory via SAF
            val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, directoryUri)
                ?: run {
                    logcat(LogPriority.WARN) { "Auto-backup: could not access directory $directoryUriStr" }
                    return Result.success()
                }

            val backupFile = docFile.createFile("application/octet-stream", filename)
            if (backupFile == null) {
                logcat(LogPriority.WARN) { "Auto-backup: could not create file '$filename' in directory" }
                return Result.success()
            }

            val success = when (format) {
                "tachibk" -> backupManager.createAniyomiBackup(backupFile.uri)
                else -> backupManager.createAnikutaBackup(backupFile.uri)
            }

            if (success) {
                logcat(LogPriority.DEBUG) { "✓ Auto-backup created: $filename" }
                autoPrefs.lastBackupTimestamp().set(System.currentTimeMillis())

                // Prune old backups
                pruneOldBackups(context, docFile, autoPrefs.maxBackups().get())
            } else {
                logcat(LogPriority.WARN) { "❌ Auto-backup creation failed for $filename" }
                backupFile.delete()
            }

            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "❌ Auto-backup job failed" }
            Result.retry()
        }
    }

    /**
     * Delete old auto-backup files, keeping only the newest [maxBackups].
     * Files are identified by the `anikuta_auto_` prefix. Sorted by name
     * (which encodes the timestamp), oldest deleted first.
     */
    private fun pruneOldBackups(
        context: Context,
        directory: androidx.documentfile.provider.DocumentFile,
        maxBackups: Int,
    ) {
        try {
            val autoBackups = directory.listFiles()
                .filter { it.name?.startsWith("anikuta_auto_") == true }
                .sortedByDescending { it.name ?: "" } // newest first (timestamp in name)

            if (autoBackups.size <= maxBackups) {
                logcat(LogPriority.DEBUG) {
                    "Prune: ${autoBackups.size} auto-backups, max=$maxBackups — nothing to delete"
                }
                return
            }

            val toDelete = autoBackups.drop(maxBackups)
            for (file in toDelete) {
                val name = file.name ?: "unknown"
                if (file.delete()) {
                    logcat(LogPriority.DEBUG) { "Prune: deleted old backup '$name'" }
                } else {
                    logcat(LogPriority.WARN) { "Prune: could not delete '$name'" }
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Prune: error listing/deleting old backups" }
        }
    }
}
