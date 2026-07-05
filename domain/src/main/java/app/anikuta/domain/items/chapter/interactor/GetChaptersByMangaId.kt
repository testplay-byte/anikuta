package app.anikuta.domain.items.chapter.interactor

import logcat.LogPriority
import app.anikuta.core.util.system.logcat
import app.anikuta.domain.items.chapter.model.Chapter
import app.anikuta.domain.items.chapter.repository.ChapterRepository

class GetChaptersByMangaId(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(mangaId: Long, applyScanlatorFilter: Boolean = false): List<Chapter> {
        return try {
            chapterRepository.getChapterByMangaId(mangaId, applyScanlatorFilter)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }
}
