package app.anikuta.download

import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents a single episode download. Mirrors aniyomi's AnimeDownload but
 * adapted for ANI-KUTA's data model (anilistId instead of animeId, episodeUrl
 * as the unique key, sourceId for re-resolution).
 *
 * Status + progress are exposed as StateFlows so the UI can observe them.
 */
data class Download(
    val id: String,
    val anilistId: Int,
    val sourceId: Long,
    val sourceName: String,
    val animeTitle: String,
    val episodeUrl: String,
    val episodeName: String,
    val episodeNumber: Float,
    val order: Int = 0,
    var video: Video? = null,
) {
    private val _statusFlow = MutableStateFlow(State.QUEUE)
    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(value) { _statusFlow.value = value }

    private val _progressFlow = MutableStateFlow(0)
    val progressFlow = _progressFlow.asStateFlow()
    var progress: Int
        get() = _progressFlow.value
        set(value) { _progressFlow.value = value }

    /** Estimated total file size in bytes (from Content-Length header), or -1 if unknown. */
    var totalSize: Long = -1L

    /** Downloaded bytes so far. */
    var downloadedBytes: Long = 0L

    /** Error message if status is ERROR. */
    var error: String? = null

    enum class State(val value: Int) {
        NOT_DOWNLOADED(0),
        QUEUE(1),
        DOWNLOADING(2),
        DOWNLOADED(3),
        ERROR(4),
    }
}
