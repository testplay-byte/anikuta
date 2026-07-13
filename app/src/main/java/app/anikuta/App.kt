package app.anikuta

import android.app.Application
import android.util.Log
import androidx.work.WorkManager
import app.anikuta.core.preference.AndroidPreferenceStore
import app.anikuta.di.AppModule
import app.anikuta.di.PreferenceModule
import app.anikuta.download.DownloadManager
import app.anikuta.download.DownloadNotifier
import app.anikuta.error.AnikutaCrashHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * ANI-KUTA Application class.
 * Wires Injekt DI on startup + installs the global crash handler.
 *
 * The crash handler is installed FIRST, before DI, so if DI itself throws
 * the user still gets the ErrorActivity instead of a silent crash.
 *
 * Notification channels are created after DI is ready (DownloadNotifier
 * needs to be resolvable via Injekt).
 *
 * Crash loop prevention: if the last crash was a foregroundServiceType
 * error (from WorkManager trying to start the download foreground service),
 * all download WorkManager jobs are cancelled on startup to break the loop.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Install the global crash handler first — before anything that
        // might throw — so the user always gets the error screen.
        Thread.setDefaultUncaughtExceptionHandler(AnikutaCrashHandler(this))

        Log.d("AnikutaApp", "=== App.onCreate started ===")

        // ---- Crash loop prevention ----
        // Check if the previous crash was a foregroundServiceType error.
        // If so, cancel all download WorkManager jobs BEFORE DI restores
        // the queue (which would trigger the worker to restart → crash again).
        checkForForegroundServiceCrashLoop()

        try {
            val preferenceStore = AndroidPreferenceStore(this)
            Log.d("AnikutaApp", "PreferenceStore created")
            Injekt.importModule(PreferenceModule(preferenceStore))
            Log.d("AnikutaApp", "PreferenceModule imported")
            Injekt.importModule(AppModule(this))
            Log.d("AnikutaApp", "AppModule imported — DI ready")

            // Create notification channels for the download system
            try {
                val notifier = Injekt.get<DownloadNotifier>()
                notifier.createChannels()
                Log.d("AnikutaApp", "✓ Download notification channels created")
            } catch (e: Exception) {
                Log.w("AnikutaApp", "⚠ Could not create notification channels: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("AnikutaApp", "❌ DI setup FAILED", e)
            // Re-throw so the crash handler catches it and shows ErrorActivity.
            throw e
        }
        Log.d("AnikutaApp", "=== App.onCreate finished ===")
    }

    /**
     * Check if the last crash was a foregroundServiceType mismatch error.
     * If so, cancel all WorkManager download jobs to prevent a crash loop.
     *
     * The crash happens when DownloadWorker calls setForeground() with
     * FOREGROUND_SERVICE_TYPE_DATA_SYNC but the manifest doesn't declare
     * the service with that type. WorkManager keeps retrying the worker,
     * causing the app to crash repeatedly.
     *
     * This is a safety net — the manifest fix (declaring SystemForegroundService
     * with foregroundServiceType="dataSync") is the primary fix. This check
     * handles edge cases where the manifest fix regresses or doesn't apply.
     */
    private fun checkForForegroundServiceCrashLoop() {
        try {
            val lastCrash = AnikutaCrashHandler.getLastCrash(this) ?: return
            if (lastCrash.contains("foregroundServiceType") ||
                lastCrash.contains("SystemForegroundService")) {
                Log.w("AnikutaApp", "⚠ Detected foregroundServiceType crash loop — cancelling all download work")
                val workManager = WorkManager.getInstance(this)
                workManager.cancelAllWorkByTag("AnikutaDownloader")
                workManager.cancelUniqueWork("AnikutaDownloader")
                Log.d("AnikutaApp", "✓ Download work cancelled to break crash loop")
                AnikutaCrashHandler.clearLastCrash(this)
            }
        } catch (e: Exception) {
            Log.w("AnikutaApp", "⚠ Could not check for crash loop: ${e.message}")
        }
    }
}
