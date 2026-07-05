package app.anikuta.di

import android.app.Application
import app.anikuta.core.network.NetworkHelper
import app.anikuta.core.network.NetworkPreferences
import app.anikuta.core.preference.AndroidPreferenceStore
import app.anikuta.core.preference.PreferenceStore
import app.anikuta.data.AnimeDatabase
import app.anikuta.data.AnimeUpdateStrategyColumnAdapter
import app.anikuta.data.DateColumnAdapter
import app.anikuta.data.FetchTypeColumnAdapter
import app.anikuta.data.StringListColumnAdapter
import app.anikuta.data.animehistory.Animehistory
import app.anikuta.data.animes.Animes
import app.anikuta.data.handlers.anime.AndroidAnimeDatabaseHandler
import app.anikuta.data.handlers.anime.AnimeDatabaseHandler
import app.anikuta.domain.source.anime.service.AnimeSourceManager
import app.anikuta.extension.anime.AnimeExtensionManager
import app.anikuta.extension.anime.util.AnimeExtensionLoader
import app.anikuta.source.AndroidAnimeSourceManager
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory

/**
 * Minimal AppModule — wires only what we have so far.
 * More bindings added as we copy more subsystems.
 */
class AppModule(val app: Application) : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)
        addSingletonFactory { AndroidPreferenceStore(get()) as PreferenceStore }
        addSingletonFactory { NetworkPreferences(get()) }
        addSingletonFactory { NetworkHelper(get(), get()) }

        // Anime database (SQLDelight)
        addSingletonFactory {
            val driver = AndroidSqliteDriver(
                schema = AnimeDatabase.Schema,
                context = app,
                name = "tachiyomi.animedb",
            )
            AnimeDatabase(
                driver = driver,
                animehistoryAdapter = Animehistory.Adapter(
                    last_seenAdapter = DateColumnAdapter,
                ),
                animesAdapter = Animes.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
                    fetch_typeAdapter = FetchTypeColumnAdapter,
                ),
            )
        }
        addSingletonFactory<AnimeDatabaseHandler> { AndroidAnimeDatabaseHandler(get(), get()) }

        // Extension + source management
        addSingletonFactory { AnimeExtensionLoader(get()) }
        addSingletonFactory { AnimeExtensionManager(get(), get()) }
        addSingletonFactory<AnimeSourceManager> { AndroidAnimeSourceManager(get(), get()) }
    }
}
