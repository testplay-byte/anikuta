package app.anikuta

import android.app.Application
import android.util.Log
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
        Log.d("AnikutaApp", "=== App.onCreate started ===")
        try {
            val preferenceStore = AndroidPreferenceStore(this)
            Log.d("AnikutaApp", "PreferenceStore created")
            Injekt.importModule(PreferenceModule(preferenceStore))
            Log.d("AnikutaApp", "PreferenceModule imported")
            Injekt.importModule(AppModule(this))
            Log.d("AnikutaApp", "AppModule imported — DI ready")
        } catch (e: Exception) {
            Log.e("AnikutaApp", "❌ DI setup FAILED", e)
        }
        Log.d("AnikutaApp", "=== App.onCreate finished ===")
    }
}
