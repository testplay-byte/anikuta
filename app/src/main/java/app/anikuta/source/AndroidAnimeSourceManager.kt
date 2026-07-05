package app.anikuta.source

import android.content.Context
import app.anikuta.domain.source.anime.service.AnimeSourceManager
import app.anikuta.extension.anime.AnimeExtensionManager
import app.anikuta.source.api.AnimeCatalogueSource
import app.anikuta.source.api.AnimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * ANI-KUTA AndroidAnimeSourceManager — minimal stub.
 *
 * TODO (later steps): copy the full implementation from aniyomi once we have:
 * - AnimeDownloadManager (Phase 7)
 * - LocalAnimeSource (D44 — later)
 * - AnimeStubSourceRepository (data layer)
 *
 * For now, this stub manages sources in memory.
 */
class AndroidAnimeSourceManager(
    private val context: Context,
    private val extensionManager: AnimeExtensionManager,
) : AnimeSourceManager {

    private val sourceMap = ConcurrentHashMap<Long, AnimeSource>()
    private val stubSourceMap = ConcurrentHashMap<Long, AnimeSource>()

    private val _catalogueSources = MutableStateFlow(emptyMap<Long, AnimeCatalogueSource>())
    override val catalogueSources: StateFlow<Map<Long, AnimeCatalogueSource>> =
        _catalogueSources.asStateFlow()

    override fun get(sourceKey: Long): AnimeSource? {
        return sourceMap[sourceKey] ?: stubSourceMap[sourceKey]
    }

    override fun getOrStub(sourceKey: Long): AnimeSource {
        return get(sourceKey) ?: stubSourceMap.getOrPut(sourceKey) {
            // TODO: create StubAnimeSource
            throw NotImplementedError("StubAnimeSource not implemented yet")
        }
    }

    override fun getOnlineSources(): List<AnimeSource> {
        return sourceMap.values.filterIsInstance<AnimeSource>()
    }

    override fun getCatalogueSources(): List<AnimeCatalogueSource> {
        return sourceMap.values.filterIsInstance<AnimeCatalogueSource>()
    }
}
