package app.anikuta.domain.mihon.upcoming.manga.interactor

import app.anikuta.source.api.model.SManga
import kotlinx.coroutines.flow.Flow
import app.anikuta.domain.entries.manga.model.Manga
import app.anikuta.domain.entries.manga.repository.MangaRepository

class GetUpcomingManga(
    private val mangaRepository: MangaRepository,
) {

    private val includedStatuses = setOf(
        SManga.ONGOING.toLong(),
        SManga.PUBLISHING_FINISHED.toLong(),
    )

    suspend fun subscribe(): Flow<List<Manga>> {
        return mangaRepository.getUpcomingManga(includedStatuses)
    }
}
