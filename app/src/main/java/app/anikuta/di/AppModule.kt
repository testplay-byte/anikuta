package app.anikuta.di

import android.app.Application
import app.anikuta.core.network.NetworkHelper
import app.anikuta.core.network.NetworkPreferences
import app.anikuta.core.preference.AndroidPreferenceStore
import app.anikuta.core.preference.PreferenceStore
import app.anikuta.data.handlers.anime.AndroidAnimeDatabaseHandler
import app.anikuta.data.handlers.anime.AnimeDatabaseHandler
import app.anikuta.extension.anime.AnimeExtensionManager
import app.anikuta.source.AndroidAnimeSourceManager
import app.anikuta.domain.source.anime.service.AnimeSourceManager
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import app.anikuta.data.AnimeDatabase

/**
 * Minimal AppModule — wires only what we have so far:
 * - Preferences
 * - Network
 * - Anime database (SQLDelight)
 * - Extension manager
 * - Source manager
 *
 * More bindings added as we copy more subsystems (download, backup, trackers, etc.)
 */
class AppModule(val app: Application) : InjektModule {
    override fun InjektRegistrar.registerImports() {
        addSingletonFactory { app }
        addSingletonFactory { AndroidPreferenceStore(get()) as PreferenceStore }
        addSingletonFactory { NetworkPreferences(get()) }
        addSingletonFactory { NetworkHelper(get(), get()) }

        // Anime database (SQLDelight)
        addSingletonFactory {
            val driver = AndroidSqliteDriver(
                get<Application>(),
                "tachiyomi.animedb",
                AnimeDatabase.Schema,
            )
            AnimeDatabase(
                driver,
                app.anikuta.data.DateColumnAdapter,
                app.anikuta.data.StringListColumnAdapter,
                app.anikuta.data.AnimeUpdateStrategyColumnAdapter,
                app.anikuta.data.FetchTypeColumnAdapter,
            )
        }
        addSingletonFactory { AndroidAnimeDatabaseHandler(get(), get()) as AnimeDatabaseHandler }

        // Extension + source management
        addSingletonFactory { AnimeExtensionManager(get(), get()) }
        addSingletonFactory { AndroidAnimeSourceManager(get(), get()) as AnimeSourceManager }
    }
}
