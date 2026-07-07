package app.anikuta.download

import android.content.Context
import android.util.Log
import app.anikuta.core.preference.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Phase 6 task 6.12 — Persistent download store.
 *
 * Tracks the download queue + completed downloads. PreferenceStore-backed
 * (survives app restart). Each download entry has: id, animeId, episodeUrl,
 * videoUrl, title, status, progress, localPath.
 *
 * The actual download is performed by [DownloadWorker] (WorkManager).
 * This store tracks the metadata + state.
 */
class DownloadStore(
    private val preferenceStore: PreferenceStore,
    private val context: Context,
) {
    companion object {
        private const val TAG = "DownloadStore"
        private const val KEY = "download_queue"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val queuePref = preferenceStore.getObject(
        key = KEY,
        defaultValue = emptyList(),
        serializer = { list ->
            json.encodeToString(ListSerializer(DownloadEntry.serializer()), list)
        },
        deserializer = { str ->
            try { json.decodeFromString(ListSerializer(DownloadEntry.serializer()), str) }
            catch (e: Exception) { emptyList() }
        },
    )

    val queue: Flow<List<DownloadEntry>> = queuePref.changes().map { it }

    fun getAll(): List<DownloadEntry> = queuePref.get()

    fun add(entry: DownloadEntry) {
        val list = queuePref.get().toMutableList()
        // Don't add duplicates (same episodeUrl)
        if (list.none { it.episodeUrl == entry.episodeUrl }) {
            list.add(entry)
            queuePref.set(list)
            Log.d(TAG, "Added download: ${entry.title}")
        }
    }

    fun update(id: String, status: DownloadStatus, progress: Int = 0, localPath: String? = null, error: String? = null) {
        val list = queuePref.get().toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val existing = list[idx]
            list[idx] = existing.copy(
                status = status,
                progress = progress,
                localPath = localPath ?: existing.localPath,
                error = error ?: existing.error,
            )
            queuePref.set(list)
        }
    }

    fun remove(id: String) {
        val list = queuePref.get().toMutableList()
        val entry = list.find { it.id == id }
        // Delete the local file if it exists
        entry?.localPath?.let { path ->
            try { File(path).delete() } catch (_: Exception) {}
        }
        list.removeAll { it.id == id }
        queuePref.set(list)
        Log.d(TAG, "Removed download: $id")
    }

    fun clearCompleted() {
        val list = queuePref.get().filter { it.status != DownloadStatus.COMPLETED }
        queuePref.set(list)
    }

    /** Get the local file path for a downloaded episode (for offline playback). */
    fun getDownloadedFile(episodeUrl: String): String? {
        return getAll().find { it.episodeUrl == episodeUrl && it.status == DownloadStatus.COMPLETED }?.localPath
    }

    /** Directory for downloaded episodes. */
    fun getDownloadDir(): File {
        return File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads").apply { mkdirs() }
    }
}

@Serializable
data class DownloadEntry(
    val id: String,
    val anilistId: Int,
    val episodeUrl: String,
    val videoUrl: String,
    val title: String,
    val episodeNumber: Float,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Int = 0,
    val localPath: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED,
}
