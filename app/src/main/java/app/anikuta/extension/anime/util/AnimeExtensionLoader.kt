package app.anikuta.extension.anime.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import app.anikuta.domain.extension.anime.interactor.TrustAnimeExtension
import app.anikuta.domain.source.service.SourcePreferences
import app.anikuta.extension.anime.model.AnimeExtension
import app.anikuta.extension.anime.model.AnimeLoadResult
import app.anikuta.source.api.AnimeCatalogueSource
import app.anikuta.source.api.AnimeSource
import app.anikuta.source.api.AnimeSourceFactory
import app.anikuta.util.system.ChildFirstPathClassLoader
import dalvik.system.PathClassLoader
import uy.kohesive.injekt.injectLazy
import java.io.File

/**
 * Loads anime extension APKs at runtime using DexClassLoader.
 * Clean rewrite based on aniyomi's AnimeExtensionLoader.
 */
class AnimeExtensionLoader(
    private val context: Context,
) {
    companion object {
        private const val TAG = "AnimeExtLoader"
        const val LIB_VERSION_MIN = 12.0
        const val LIB_VERSION_MAX = 16.0
        private const val EXTENSION_FEATURE = "tachiyomi.animeextension"
        private const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
        private const val METADATA_SOURCE_CLASS_FALLBACK = "extension.class"
        private const val METADATA_LIB_VERSION = "tachiyomi.animeextension.lib.version"
        private const val METADATA_LIB_VERSION_FALLBACK = "extension.libVersion"
    }

    private val trustExtension: TrustAnimeExtension by injectLazy()
    private val preferences: SourcePreferences by injectLazy()

    /**
     * Finds all installed anime extensions on the device.
     */
    @SuppressLint("QueryAllPackagesPermission")
    fun loadAll(): List<AnimeLoadResult> {
        val packages = context.packageManager.getInstalledPackages(PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS)
        return packages
            .filter { isPackageAnExtension(it) }
            .map { loadExtension(it) }
    }

    /**
     * Loads a single extension from its PackageInfo.
     */
    fun loadExtension(pkgInfo: PackageInfo): AnimeLoadResult {
        val pkgName = pkgInfo.packageName
        val sources = getSourcesFromPackage(pkgInfo)

        if (sources.isEmpty()) {
            Log.w(TAG, "No sources found in extension $pkgName")
            return AnimeLoadResult.Error
        }

        val extension = AnimeExtension.Installed(
            name = pkgInfo.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: pkgName,
            pkgName = pkgName,
            versionName = pkgInfo.versionName ?: "unknown",
            versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo),
            libVersion = getLibVersionFromMetadata(pkgInfo),
            lang = "",
            isNsfw = false,
            sources = sources,
            icon = pkgInfo.applicationInfo?.loadIcon(context.packageManager),
        )

        return if (isExtensionTrusted(pkgInfo)) {
            AnimeLoadResult.Success(extension)
        } else {
            AnimeLoadResult.Untrusted(extension)
        }
    }

    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        val reqFeatures = pkgInfo.applicationInfo?.metaData ?: return false
        return reqFeatures.containsKey(METADATA_SOURCE_CLASS) ||
               reqFeatures.containsKey(METADATA_SOURCE_CLASS_FALLBACK)
    }

    private fun isExtensionTrusted(pkgInfo: PackageInfo): Boolean {
        val signatures = pkgInfo.signingInfo?.apkContentsSigners?.map {
            app.anikuta.util.lang.Hash.sha256(it.encoded)
        } ?: emptyList()
        return true  // Always trust for now (TrustAnimeExtension stub returns true)
    }

    private fun getSourcesFromPackage(pkgInfo: PackageInfo): List<AnimeSource> {
        val appInfo = pkgInfo.applicationInfo ?: return emptyList()
        val sourceClass = appInfo.metaData?.getString(METADATA_SOURCE_CLASS)
            ?: appInfo.metaData?.getString(METADATA_SOURCE_CLASS_FALLBACK)
            ?: return emptyList()

        val classLoader = createClassLoader(appInfo)
        val sources = mutableListOf<AnimeSource>()

        sourceClass.split(";").forEach { className ->
            try {
                val clazz = Class.forName(className.trim(), false, classLoader)
                val instance = clazz.getDeclaredConstructor().newInstance()

                when (instance) {
                    is AnimeSource -> sources.add(instance)
                    is AnimeSourceFactory -> sources.addAll(instance.createSources())
                    else -> Log.w(TAG, "Unknown source type: ${clazz.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load source class: $className", e)
            }
        }

        return sources
    }

    private fun createClassLoader(appInfo: android.content.pm.ApplicationInfo): ClassLoader {
        val dexPath = appInfo.sourceDir
        val libraryPath = appInfo.nativeLibraryDir
        return try {
            ChildFirstPathClassLoader(dexPath, libraryPath, context.classLoader)
        } catch (e: Exception) {
            Log.w(TAG, "ChildFirstPathClassLoader failed, falling back to PathClassLoader", e)
            PathClassLoader(dexPath, libraryPath, context.classLoader)
        }
    }

    private fun getLibVersionFromMetadata(pkgInfo: PackageInfo): Double {
        val version = pkgInfo.applicationInfo?.metaData?.getString(METADATA_LIB_VERSION)
            ?: pkgInfo.applicationInfo?.metaData?.getString(METADATA_LIB_VERSION_FALLBACK)
            ?: return 0.0
        return try {
            version.substringBeforeLast('.').toDouble()
        } catch (e: Exception) {
            0.0
        }
    }

    companion object {
        private const val TAG = "AnimeExtLoader"
    }
}
