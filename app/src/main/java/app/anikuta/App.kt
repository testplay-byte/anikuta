package app.anikuta

import android.app.Application
import app.anikuta.core.preference.AndroidPreferenceStore
import app.anikuta.di.AppModule
import app.anikuta.di.PreferenceModule
import uy.kohesive.injekt.Injekt

/**
 * ANI-KUTA Application class.
 * Wires Injekt DI on startup.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val preferenceStore = AndroidPreferenceStore(this)
        Injekt.importModule(PreferenceModule(preferenceStore))
        Injekt.importModule(AppModule(this))
    }
}
