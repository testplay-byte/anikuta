package app.anikuta.di

import android.app.Application
import android.content.Context
import app.anikuta.core.network.NetworkHelper
import app.anikuta.core.network.NetworkPreferences
import app.anikuta.core.preference.AndroidPreferenceStore
import app.anikuta.core.preference.PreferenceStore
import app.anikuta.data.AnimeDatabase
import app.anikuta.data.AnimeDatabaseFactory
import app.anikuta.data.cache.CacheManager
import app.anikuta.data.cache.LocalCache
import app.anikuta.data.handlers.anime.AndroidAnimeDatabaseHandler
import app.anikuta.data.handlers.anime.AnimeDatabaseHandler
import app.anikuta.data.anilist.repository.AniListRepository
import app.anikuta.data.supabase.SupabaseClient
import app.anikuta.domain.source.anime.service.AnimeSourceManager
import app.anikuta.extension.anime.AnimeExtensionManager
import app.anikuta.extension.anime.util.AnimeExtensionLoader
import app.anikuta.source.AndroidAnimeSourceManager
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

/**
 * AppModule — wires all backend components.
 *
 * KEY: register both Application AND Context so `get<Context>()` works.
 * (Injekt doesn't auto-resolve Application → Context; must register both.)
 */
class AppModule(val app: Application) : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        // Register both Application AND Context (Injekt needs both)
        addSingleton(app)
        addSingleton<Context>(app)

        // Preferences
        addSingletonFactory { AndroidPreferenceStore(get<Context>()) as PreferenceStore }
        addSingletonFactory { NetworkPreferences(get<PreferenceStore>()) }
        addSingletonFactory { NetworkHelper(get<Context>(), get<NetworkPreferences>()) }

        // Anime database (SQLDelight)
        addSingletonFactory { AnimeDatabaseFactory.create(app) }
        addSingletonFactory<AnimeDatabaseHandler> { AndroidAnimeDatabaseHandler(get(), get()) }

        // Extension + source management
        addSingletonFactory { AnimeExtensionLoader(get<Context>()) }
        addSingletonFactory { AnimeExtensionManager(get<Context>(), get()) }
        addSingletonFactory<AnimeSourceManager> { AndroidAnimeSourceManager(get<Context>(), get()) }

        // AniList client (ours — discovery layer)
        addSingletonFactory { AniListRepository(get()) }

        // 3-step cache: Local → Supabase → AniList
        addSingletonFactory { LocalCache(get<AnimeDatabase>()) }
        addSingletonFactory { SupabaseClient(get()) }
        addSingletonFactory { CacheManager(get(), get()) }
    }
}
