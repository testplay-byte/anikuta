package app.anikuta.extension.anime

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import app.anikuta.domain.extension.anime.interactor.TrustAnimeExtension
import app.anikuta.domain.source.service.SourcePreferences
import app.anikuta.extension.InstallStep
import app.anikuta.extension.anime.api.AnimeExtensionApi
import app.anikuta.extension.anime.model.AnimeExtension
import app.anikuta.extension.anime.model.AnimeLoadResult
import app.anikuta.extension.anime.util.AnimeExtensionLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Manages installed anime extensions.
 * Clean rewrite based on aniyomi's AnimeExtensionManager.
 */
class AnimeExtensionManager(
    private val context: Context,
    private val preferences: SourcePreferences = Injekt.get(),
    private val trustExtension: TrustAnimeExtension = Injekt.get(),
) {
    companion object {
        private const val TAG = "AnimeExtManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api: AnimeExtensionApi by lazy { AnimeExtensionApi() }
    private val loader: AnimeExtensionLoader by lazy { AnimeExtensionLoader(context) }

    private val _installedExtensions = MutableStateFlow(emptyList<AnimeExtension.Installed>())
    val installedExtensions: StateFlow<List<AnimeExtension.Installed>> = _installedExtensions.asStateFlow()

    private val _availableExtensions = MutableStateFlow(emptyList<AnimeExtension.Available>())
    val availableExtensions: StateFlow<List<AnimeExtension.Available>> = _availableExtensions.asStateFlow()

    private val _untrustedExtensions = MutableStateFlow(emptyList<AnimeExtension.Untrusted>())
    val untrustedExtensions: StateFlow<List<AnimeExtension.Untrusted>> = _untrustedExtensions.asStateFlow()

    init {
        initExtensions()
    }

    private fun initExtensions() {
        scope.launch {
            loadExtensions()
        }
    }

    private fun loadExtensions() {
        val results = loader.loadAll()
        val installed = mutableListOf<AnimeExtension.Installed>()
        val untrusted = mutableListOf<AnimeExtension.Untrusted>()

        for (result in results) {
            when (result) {
                is AnimeLoadResult.Success -> installed.add(result.extension)
                is AnimeLoadResult.Untrusted -> untrusted.add(result.extension)
                is AnimeLoadResult.Error -> Log.w(TAG, "Failed to load extension")
            }
        }

        _installedExtensions.value = installed
        _untrustedExtensions.value = untrusted
        Log.i(TAG, "Loaded ${installed.size} extensions (${untrusted.size} untrusted)")
    }

    fun findAvailableExtensions() {
        scope.launch {
            try {
                val available = api.findExtensions()
                _availableExtensions.value = available
                Log.i(TAG, "Found ${available.size} available extensions")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to find available extensions", e)
            }
        }
    }

    fun getExtensionPackage(sourceId: Long): String? {
        return _installedExtensions.value.find { ext ->
            ext.sources.any { it.id == sourceId }
        }?.pkgName
    }

    fun getAppIconForSource(sourceId: Long): Drawable? {
        return _installedExtensions.value.find { ext ->
            ext.sources.any { it.id == sourceId }
        }?.icon
    }

    fun hasUpdates(): Boolean = _installedExtensions.value.any { it.hasUpdate }

    // TODO: install/uninstall methods (need installer system — add later)
}
