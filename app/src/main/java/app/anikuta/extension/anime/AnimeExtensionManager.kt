package app.anikuta.extension.anime

import android.content.Context
import app.anikuta.extension.anime.model.AnimeExtension
import app.anikuta.extension.anime.model.AnimeLoadResult
import app.anikuta.extension.anime.util.AnimeExtensionLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ANI-KUTA AnimeExtensionManager — minimal stub.
 *
 * TODO (later steps): copy the full implementation from aniyomi once we have:
 * - TrustAnimeExtension interactor (domain)
 * - SourcePreferences (domain)
 * - ExtensionUpdateNotifier (notifications system)
 * - AnimeExtensionInstallReceiver/Installer (installer system)
 * - toast() util (core)
 *
 * For now, this stub loads extensions via AnimeExtensionLoader but doesn't
 * support installing/updating.
 */
class AnimeExtensionManager(
    private val context: Context,
    private val extensionLoader: AnimeExtensionLoader,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _extensions = MutableStateFlow(emptyList<AnimeExtension.Installed>())
    val extensions: StateFlow<List<AnimeExtension.Installed>> = _extensions.asStateFlow()

    init {
        // Load installed extensions on init
        // TODO: full implementation with TrustAnimeExtension, notifications, etc.
    }

    /** Reloads the installed extensions. */
    fun findAvailableExtensions() {
        // TODO: full implementation
    }
}
