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
import app.anikuta.extension.anime.TrustResult
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
 * Phase 7 — ViewModel for the Extensions settings screen.
 *
 * Exposes three lists:
 *  - [sources] — trusted installed extensions (max 2). These are the ONLY
 *    extensions used for app functionality (search/resolve/play).
 *  - [installed] — installed-but-untrusted extensions. User can Trust (→
 *    moves to sources) or Delete (uninstall).
 *  - [available] — extensions from the repo(s) that are not yet installed.
 *    User can Install (→ moves to installed/untrusted after install).
 *
 * Also exposes [trustResult] for the max-2-trusted popup.
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

    private val api: AnimeExtensionApi by lazy { AnimeExtensionApi() }

    // ---- Sources (trusted installed) ----
    private val _sources = MutableStateFlow<List<AnimeExtension.Installed>>(emptyList())
    val sources: StateFlow<List<AnimeExtension.Installed>> = _sources.asStateFlow()

    // ---- Installed (untrusted) ----
    private val _installed = MutableStateFlow<List<AnimeExtension.Untrusted>>(emptyList())
    val installed: StateFlow<List<AnimeExtension.Untrusted>> = _installed.asStateFlow()

    // ---- Available (from repos, not installed) ----
    private val _available = MutableStateFlow<List<AnimeExtension.Available>>(emptyList())
    val available: StateFlow<List<AnimeExtension.Available>> = _available.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    /** Result of the last trust() call — drives the max-2 popup. */
    private val _trustResult = MutableStateFlow<TrustResult?>(null)
    val trustResult: StateFlow<TrustResult?> = _trustResult.asStateFlow()

    init {
        // Subscribe to trusted (sources) and untrusted (installed) flows.
        viewModelScope.launch {
            extensionManager?.installedExtensions?.collect { installed ->
                _sources.value = installed
            }
        }
        viewModelScope.launch {
            extensionManager?.untrustedExtensions?.collect { untrusted ->
                _installed.value = untrusted
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
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
     * Trust an installed (untrusted) extension — moves it to Sources.
     * If the max-2 limit is reached, sets [trustResult] to LimitExceeded
     * so the UI shows the popup.
     */
    fun trustExtension(ext: AnimeExtension.Untrusted) {
        val result = extensionManager?.trust(ext.pkgName) ?: return
        _trustResult.value = result
        if (result is TrustResult.Success) {
            Log.i(TAG, "Trusted ${ext.name}")
        }
    }

    /**
     * Revoke trust from a source — moves it back to Installed (untrusted).
     */
    fun revokeTrust(ext: AnimeExtension.Installed) {
        extensionManager?.revokeTrust(ext.pkgName)
        Log.i(TAG, "Revoked trust from ${ext.name}")
    }

    /** Dismiss the trust-limit popup. */
    fun dismissTrustResult() {
        _trustResult.value = null
    }

    /**
     * Download the APK for [ext] from its repo and launch the system package
     * installer. The extension installs as UNTRUSTED (appears in Installed,
     * not Sources) — the user must explicitly trust it after install.
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
     * Uninstall an extension (works for both trusted sources and untrusted
     * installed). After uninstall, the manager's flow auto-updates.
     */
    fun uninstallExtension(pkgName: String) {
        val ctx = context ?: return
        try {
            val uri = Uri.parse("package:$pkgName")
            val intent = Intent().apply {
                action = "android.intent.action.UNINSTALL_PACKAGE"
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
            if (intent.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(intent)
            } else {
                val fallbackIntent = Intent(Intent.ACTION_DELETE).apply {
                    data = uri
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (fallbackIntent.resolveActivity(ctx.packageManager) != null) {
                    ctx.startActivity(fallbackIntent)
                } else {
                    val settingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = uri
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(settingsIntent)
                    Toast.makeText(ctx, "Open the app info to uninstall", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Uninstall failed for $pkgName", e)
            Toast.makeText(ctx, "Uninstall failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
