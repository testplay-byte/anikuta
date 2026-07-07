package app.anikuta.domain.mihon.extensionrepo.anime.interactor

import app.anikuta.domain.mihon.extensionrepo.model.ExtensionRepo

class GetAnimeExtensionRepo {
    suspend fun await(): List<ExtensionRepo> = getAll()
    suspend fun getAll(): List<ExtensionRepo> = listOf(
        ExtensionRepo(
            baseUrl = "https://raw.githubusercontent.com/Confused-Creature-180/aniyomi-extensions/repo",
            name = "AniKoto Extensions",
            signingKeyFingerprint = "",
        )
    )
    suspend fun count(): Long = 1
}
