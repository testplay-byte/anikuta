package app.anikuta.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.anikuta.download.DownloadEntry
import app.anikuta.download.DownloadManager
import app.anikuta.download.DownloadPreferences
import app.anikuta.download.DownloadQuality
import app.anikuta.download.DownloadStatus
import app.anikuta.download.DownloadStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase 6 task 6.13 — ViewModel for the Downloads screen.
 * Observes the download queue + exposes settings.
 */
class DownloadsViewModel : ViewModel() {

    companion object { private const val TAG = "DownloadsVM" }

    private val store: DownloadStore? = try { Injekt.get() } catch (e: Exception) { null }
    private val manager: DownloadManager? = try { Injekt.get() } catch (e: Exception) { null }
    private val prefs: DownloadPreferences? = try { Injekt.get() } catch (e: Exception) { null }

    private val _queue = MutableStateFlow<List<DownloadEntry>>(emptyList())
    val queue: StateFlow<List<DownloadEntry>> = _queue.asStateFlow()

    init {
        viewModelScope.launch {
            store?.queue?.collect { entries ->
                _queue.value = entries
            }
        }
    }

    fun cancelDownload(id: String) { manager?.cancelDownload(id) }
    fun removeDownload(id: String) { manager?.removeDownload(id) }
    fun clearCompleted() { manager?.clearCompleted() }

    // Settings
    fun preferredQuality(): String = prefs?.preferredQuality()?.get() ?: "720p"
    fun setPreferredQuality(v: String) { prefs?.preferredQuality()?.set(v) }
    fun preferredAudioVersion(): String = prefs?.preferredAudioVersion()?.get() ?: "sub"
    fun setPreferredAudioVersion(v: String) { prefs?.preferredAudioVersion()?.set(v) }
    fun preferredServer(): String = prefs?.preferredServer()?.get() ?: ""
    fun setPreferredServer(v: String) { prefs?.preferredServer()?.set(v) }
    fun downloadOverWifiOnly(): Boolean = prefs?.downloadOverWifiOnly()?.get() ?: true
    fun setDownloadOverWifiOnly(v: Boolean) { prefs?.downloadOverWifiOnly()?.set(v) }
    fun deleteAfterWatching(): Boolean = prefs?.deleteAfterWatching()?.get() ?: false
    fun setDeleteAfterWatching(v: Boolean) { prefs?.deleteAfterWatching()?.set(v) }
}
