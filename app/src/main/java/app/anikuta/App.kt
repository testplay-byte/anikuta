package app.anikuta

import android.app.Application

/**
 * ANI-KUTA Application class.
 * Currently minimal — DI (Injekt) + backend wiring will be added in Step 1.8.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // TODO (Step 1.8): Injekt DI setup here
    }
}
