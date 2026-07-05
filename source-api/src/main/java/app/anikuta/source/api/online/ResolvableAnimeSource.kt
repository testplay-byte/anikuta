package app.anikuta.source.api.online

import app.anikuta.source.api.AnimeSource
import app.anikuta.source.api.model.SAnime
import app.anikuta.source.api.model.SEpisode

/**
 * A source that may handle opening an SAnime or SEpisode for a given URI.
 *
 * @since extensions-lib 1.5
 */
interface ResolvableAnimeSource : AnimeSource {

    /**
     * Returns what the given URI may open.
     * Returns [UriType.Unknown] if the source is not able to resolve the URI.
     *
     * @since extensions-lib 1.5
     */
    fun getUriType(uri: String): UriType

    /**
     * Called if [getUriType] is [UriType.Anime].
     * Returns the corresponding SManga, if possible.
     *
     * @since extensions-lib 1.5
     */
    suspend fun getAnime(uri: String): SAnime?

    /**
     * Called if [getUriType] is [UriType.Episode].
     * Returns the corresponding SChapter, if possible.
     *
     * @since extensions-lib 1.5
     */
    suspend fun getEpisode(uri: String): SEpisode?
}

sealed interface UriType {
    data object Anime : UriType
    data object Episode : UriType
    data object Unknown : UriType
}
