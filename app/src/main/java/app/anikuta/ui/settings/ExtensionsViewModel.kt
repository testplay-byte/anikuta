package app.anikuta.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.extension.anime.AnimeExtensionManager
import app.anikuta.extension.anime.api.AnimeExtensionApi
import app.anikuta.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException

/**
 * Phase 6 tasks 6.1-6.6 — ViewModel for the Extensions settings screen.
 *
 * Surfaces installed extensions (collected from [AnimeExtensionManager.installedExtensions])
 * and available extensions (fetched from the repo via [AnimeExtensionApi.findExtensions]),
 * plus install/uninstall entry points that hand off to the system package
 * installer via Intent + FileProvider.
 *
 * Crash-resistant: if DI fails the screen shows an empty state + a Toast
 * instead of crashing (same pattern as [app.anikuta.ui.home.HomeViewModel]
 * and [app.anikuta.ui.library.LibraryViewModel]).
 */
class ExtensionsViewModel : ViewModel() {

    companion object {
        private const val TAG = "ExtensionsViewModel"
    }

    private val extensionManager: AnimeExtensionManager? = try {
        Injekt.get<AnimeExtensionManager>().also { Log.d(TAG, "✅ AnimeExtensionManager obtained") }
    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to get AnimeExtensionManager from DI", e); null
    }

    private val context: Context? = try {
        Injekt.get<Context>()
    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to get Context from DI", e); null
    }

    private val networkHelper: NetworkHelper? = try {
        Injekt.get<NetworkHelper>()
    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to get NetworkHelper from DI", e); null
    }

    // AnimeExtensionApi uses injectLazy internally for NetworkHelper + repo,
    // so constructing it is cheap and safe to do lazily.
    private val api: AnimeExtensionApi by lazy { AnimeExtensionApi() }

    private val _installed = MutableStateFlow<List<AnimeExtension.Installed>>(emptyList())
    val installed: StateFlow<List<AnimeExtension.Installed>> = _installed.asStateFlow()

    private val _available = MutableStateFlow<List<AnimeExtension.Available>>(emptyList())
    val available: StateFlow<List<AnimeExtension.Available>> = _available.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Package names of extensions whose APK download is in flight. */
    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    init {
        // Subscribe to the manager's installed-extensions flow so the UI
        // reflects installs/uninstalls in real time without a manual refresh.
        viewModelScope.launch {
            extensionManager?.installedExtensions?.collect { installed ->
                _installed.value = installed
            }
        }
        // Kick off the first available-extensions fetch.
        refresh()
    }

    /**
     * Reload installed (re-scan via the manager) + available (re-fetch from
     * the repo) extensions. Safe to call multiple times. Used by pull-to-refresh.
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            // Show the full-screen loading skeleton only on the first load —
            // pull-to-refresh uses the spinner in the PullToRefreshBox instead.
            if (_available.value.isEmpty()) _isLoading.value = true
            try {
                extensionManager?.reload()
                val available = withContext(Dispatchers.IO) {
                    api.findExtensions()
                }
                _available.value = available
                Log.i(TAG, "Loaded ${available.size} available extensions")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh extensions", e)
                Toast.makeText(context, "Failed to load extensions", Toast.LENGTH_SHORT).show()
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Download the APK for [ext] from its repo and launch the system package
     * installer. The APK is written to the app cache dir and shared with the
     * installer via FileProvider so we don't need world-readable file perms.
     *
     * URL pattern: `${ext.repoUrl}/apk/${ext.apkName}` — matches the
     * `AnimeExtensionApi.getApkUrl` convention.
     */
    fun installExtension(ext: AnimeExtension.Available) {
        if (ext.pkgName in _downloading.value) return

        val ctx = context ?: run {
            Log.e(TAG, "Cannot install — app context unavailable")
            return
        }
        val client = networkHelper?.client ?: run {
            Toast.makeText(ctx, "Network not available", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _downloading.value = _downloading.value + ext.pkgName
            try {
                val apkUrl = "${ext.repoUrl}/apk/${ext.apkName}"
                val apkFile = File(ctx.cacheDir, "ext_${ext.pkgName}_${ext.versionCode}.apk")

                withContext(Dispatchers.IO) {
                    val response = client.newCall(GET(apkUrl)).execute()
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    response.body?.byteStream()?.use { input ->
                        apkFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IOException("Empty response body")
                }

                val uri = FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.fileprovider",
                    apkFile,
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
                Toast.makeText(ctx, "Installing ${ext.name}…", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Install failed for ${ext.pkgName}", e)
                Toast.makeText(ctx, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _downloading.value = _downloading.value - ext.pkgName
            }
        }
    }

    /**
     * Launch the system uninstall dialog for [ext]. Android handles the
     * actual uninstall + user confirmation; the manager's
     * [AnimeExtensionManager.installedExtensions] flow updates once the
     * package is gone and the user returns to the screen.
     */
    fun uninstallExtension(ext: AnimeExtension.Installed) {
        val ctx = context ?: run {
            Log.e(TAG, "Cannot uninstall — app context unavailable")
            return
        }
        try {
            // Use ACTION_DELETE with the package URI — opens the system
            // uninstall confirmation dialog.
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${ext.pkgName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            Log.d(TAG, "Uninstall dialog launched for ${ext.pkgName}")
        } catch (e: Exception) {
            Log.e(TAG, "Uninstall failed for ${ext.pkgName}", e)
            Toast.makeText(ctx, "Uninstall failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
