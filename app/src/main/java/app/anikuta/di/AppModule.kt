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
import app.anikuta.player.PlayerPreferences
import app.anikuta.source.bridge.AniyomiSourceBridge
import app.anikuta.domain.extension.anime.interactor.TrustAnimeExtension
import app.anikuta.domain.mihon.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import app.anikuta.domain.mihon.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import app.anikuta.domain.source.anime.service.AnimeSourceManager
import app.anikuta.domain.source.service.SourcePreferences
import app.anikuta.extension.anime.AnimeExtensionManager
import app.anikuta.extension.anime.util.AnimeExtensionLoader
import app.anikuta.source.AndroidAnimeSourceManager
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class AppModule(val app: Application) : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)
        addSingleton<Context>(app)

        // Preferences
        addSingletonFactory { AndroidPreferenceStore(get<Context>()) as PreferenceStore }
        addSingletonFactory { NetworkPreferences(get<PreferenceStore>()) }
        addSingletonFactory { NetworkHelper(get<Context>(), get<NetworkPreferences>()) }

        // Player preferences + MPV player surface
        addSingletonFactory { PlayerPreferences(get<PreferenceStore>()) }
        addSingletonFactory { app.anikuta.player.WatchProgressStore(get<PreferenceStore>()) }

        // Anime database
        addSingletonFactory { AnimeDatabaseFactory.create(app) }
        addSingletonFactory<AnimeDatabaseHandler> { AndroidAnimeDatabaseHandler(get(), get()) }

        // Extension + source management
        addSingletonFactory { SourcePreferences(get<PreferenceStore>()) }
        addSingletonFactory { TrustAnimeExtension() }
        addSingletonFactory { GetAnimeExtensionRepo() }
        addSingletonFactory { UpdateAnimeExtensionRepo() }
        addSingletonFactory { AnimeExtensionLoader(get<Context>()) }
        addSingletonFactory { AnimeExtensionManager(get<Context>(), get(), get()) }
        addSingletonFactory<AnimeSourceManager> { AndroidAnimeSourceManager(get<Context>(), get()) }

        // AniList ↔ extension source bridge (fuzzy title matching)
        addSingletonFactory { AniyomiSourceBridge(get<AnimeSourceManager>()) }

        // AniList client
        addSingletonFactory { AniListRepository(get()) }

        // 3-step cache
        addSingletonFactory { LocalCache(get<AnimeDatabase>()) }
        addSingletonFactory { SupabaseClient(get()) }
        addSingletonFactory { CacheManager(get(), get()) }
    }
}
