package app.anikuta.domain.mihon.extensionrepo.model

data class ExtensionRepo(
    val baseUrl: String,
    val name: String,
    val signingKeyFingerprint: String,
)
