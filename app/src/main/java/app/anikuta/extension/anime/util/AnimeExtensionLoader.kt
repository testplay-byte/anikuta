package app.anikuta.extension.anime.util

import android.content.Context
import android.content.pm.PackageInfo
import app.anikuta.extension.anime.model.AnimeExtension
import app.anikuta.extension.anime.model.AnimeLoadResult
import app.anikuta.source.api.AnimeCatalogueSource
import app.anikuta.source.api.AnimeSource
import app.anikuta.source.api.AnimeSourceFactory

/**
 * ANI-KUTA AnimeExtensionLoader — minimal stub.
 *
 * TODO (later steps): copy the full implementation from aniyomi once we have:
 * - ChildFirstPathClassLoader util
 * - Hash util
 * - copyAndSetReadOnlyTo storage util
 * - TrustAnimeExtension interactor
 *
 * For now, this stub can't load extensions yet.
 */
class AnimeExtensionLoader(
    private val context: Context,
) {
    fun loadAll(): List<AnimeLoadResult> {
        // TODO: full implementation with DexClassLoader
        return emptyList()
    }

    fun loadExtension(packageInfo: PackageInfo): AnimeLoadResult {
        // TODO: full implementation
        return AnimeLoadResult.Error
    }
}
