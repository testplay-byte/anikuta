package app.anikuta.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase N-1 (Notifications) — WorkManager worker for release tracking.
 *
 * This is the entry point that WorkManager calls when a check is due.
 * It delegates the actual checking to [ReleaseTracker] (implemented in Phase N-2).
 *
 * Flow:
 *  1. WorkManager wakes this worker at the scheduled time.
 *  2. The worker calls ReleaseTracker.checkDueAnime().
 *  3. The tracker checks all due anime, fires notifications, triggers auto-downloads.
 *  4. The planner re-schedules the next check.
 *
 * This is a skeleton in Phase N-1 — the actual detection logic is added in
 * Phase N-2. For now, it just logs and reschedules.
 */
class ReleaseTrackerWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ReleaseTrackerWorker"
    }

    private val planner: ReleaseCheckPlanner = Injekt.get()
    private val store: app.anikuta.data.cache.ReleaseTrackingStore = Injekt.get()
    private val prefs: NotificationPreferences = Injekt.get()

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork: → release tracker worker started")

        // If notifications are globally disabled, don't check — just reschedule
        // in case the user re-enables them.
        if (!prefs.globalNotifyEnabled().get()) {
            Log.d(TAG, "doWork: notifications disabled globally — skipping check, rescheduling")
            planner.scheduleNextCheck()
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val dueAnime = store.getDueChecks(now)

        if (dueAnime.isEmpty()) {
            Log.d(TAG, "doWork: no anime due for check")
            planner.scheduleNextCheck()
            return Result.success()
        }

        Log.d(TAG, "doWork: ${dueAnime.size} anime due for check")

        // Phase N-2 will add the actual checking logic here via ReleaseTracker.
        // For now (Phase N-1 skeleton), just log and reschedule.
        for (anime in dueAnime) {
            Log.d(TAG, "doWork: would check: ${anime.title} (anilistId=${anime.anilistId})")
        }

        // Re-schedule the next check.
        planner.scheduleNextCheck()

        Log.d(TAG, "doWork: ← worker finished")
        return Result.success()
    }
}
