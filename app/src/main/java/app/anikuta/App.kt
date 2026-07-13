package app.anikuta

import android.app.Application
import android.util.Log
import app.anikuta.core.preference.AndroidPreferenceStore
import app.anikuta.di.AppModule
import app.anikuta.di.PreferenceModule
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
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Install the global crash handler first — before anything that
        // might throw — so the user always gets the error screen.
        Thread.setDefaultUncaughtExceptionHandler(AnikutaCrashHandler(this))

        Log.d("AnikutaApp", "=== App.onCreate started ===")
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
}
