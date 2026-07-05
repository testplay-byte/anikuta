package app.anikuta.source

import android.content.Context
import app.anikuta.domain.source.anime.model.StubAnimeSource
import app.anikuta.domain.source.anime.service.AnimeSourceManager
import app.anikuta.extension.anime.AnimeExtensionManager
import app.anikuta.source.api.AnimeCatalogueSource
import app.anikuta.source.api.AnimeSource
import app.anikuta.source.api.online.AnimeHttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * ANI-KUTA AndroidAnimeSourceManager — minimal stub.
 * TODO (later steps): full implementation with extension loading, stub sources, etc.
 */
class AndroidAnimeSourceManager(
    private val context: Context,
    private val extensionManager: AnimeExtensionManager,
) : AnimeSourceManager {

    private val sourceMap = ConcurrentHashMap<Long, AnimeSource>()

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized = _isInitialized.asStateFlow()

    private val _catalogueSources = MutableStateFlow(emptyList<AnimeCatalogueSource>())
    override val catalogueSources: Flow<List<AnimeCatalogueSource>> = _catalogueSources

    override fun get(sourceKey: Long): AnimeSource? = sourceMap[sourceKey]

    override fun getOrStub(sourceKey: Long): AnimeSource {
        return get(sourceKey) ?: StubAnimeSource(sourceKey, "", "")
    }

    override fun getOnlineSources(): List<AnimeHttpSource> =
        sourceMap.values.filterIsInstance<AnimeHttpSource>()

    override fun getCatalogueSources(): List<AnimeCatalogueSource> =
        sourceMap.values.filterIsInstance<AnimeCatalogueSource>()

    override fun getStubSources(): List<StubAnimeSource> = emptyList()
}
