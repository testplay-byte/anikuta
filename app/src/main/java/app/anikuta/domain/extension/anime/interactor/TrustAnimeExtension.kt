package app.anikuta.domain.extension.anime.interactor

import android.content.pm.PackageInfo

/**
 * Stub — always trusts extensions for now.
 * TODO: implement proper trust verification with extension repo fingerprints.
 */
class TrustAnimeExtension() {
    suspend fun isTrusted(pkgInfo: PackageInfo, fingerprints: List<String>): Boolean = true
    fun trust(pkgName: String, versionCode: Long, signatureHash: String) {}
    fun revokeAll() {}
    fun revoke(pkgName: String, versionCode: Long) {}
}
