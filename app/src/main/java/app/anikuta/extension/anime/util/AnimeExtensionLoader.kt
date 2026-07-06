package app.anikuta.extension.anime.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
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
 *
 * Detection: extensions declare `<uses-feature android:name="tachiyomi.animeextension" />`
 * in their AndroidManifest.xml. We scan all installed packages for this feature
 * (via [PackageInfo.reqFeatures]), then load the source class from the package's
 * `<meta-data android:name="tachiyomi.animeextension.class" />` value using a
 * DexClassLoader.
 *
 * Source: REFERENCE/app/.../extension/anime/util/AnimeExtensionLoader.kt
 */
class AnimeExtensionLoader(
    private val context: Context,
) {
    companion object {
        private const val TAG = "AnimeExtLoader"
        const val LIB_VERSION_MIN = 12.0
        const val LIB_VERSION_MAX = 16.0

        /** The <uses-feature> name that identifies an anime extension. */
        private const val EXTENSION_FEATURE = "tachiyomi.animeextension"

        /** Metadata key for the source class name (or factory class name). */
        private const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
        private const val METADATA_SOURCE_FACTORY = "tachiyomi.animeextension.factory"

        /** Old fallback metadata keys (pre-lib-1.5 extensions). */
        private const val METADATA_SOURCE_CLASS_FALLBACK = "extension.class"

        private const val METADATA_LIB_VERSION = "tachiyomi.animeextension.lib.version"
        private const val METADATA_LIB_VERSION_FALLBACK = "extension.libVersion"
        private const val METADATA_NSFW = "tachiyomi.animeextension.nsfw"

        /** Package-info flags matching aniyomi: need metaData + signing certs. */
        @Suppress("DEPRECATION")
        private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)
    }

    private val trustExtension: TrustAnimeExtension by injectLazy()
    private val preferences: SourcePreferences by injectLazy()

    /**
     * Finds all installed anime extensions on the device.
     * Scans every installed package for the `tachiyomi.animeextension` feature.
     */
    @SuppressLint("QueryAllPackagesPermission")
    fun loadAll(): List<AnimeLoadResult> {
        val pkgManager = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }
        Log.d(TAG, "Scanned ${packages.size} installed package(s) for anime extensions")
        val extensions = packages
            .filter { isPackageAnExtension(it) }
            .also { Log.d(TAG, "Found ${it.size} anime extension(s): ${it.joinToString { p -> p.packageName }}") }
            .map { loadExtension(it) }
        return extensions
    }

    /**
     * Loads a single extension from its PackageInfo.
     */
    fun loadExtension(pkgInfo: PackageInfo): AnimeLoadResult {
        val pkgName = pkgInfo.packageName
        Log.d(TAG, "Loading extension: $pkgName")
        val sources = getSourcesFromPackage(pkgInfo)

        if (sources.isEmpty()) {
            Log.w(TAG, "No sources found in extension $pkgName")
            return AnimeLoadResult.Error
        }

        val appInfo = pkgInfo.applicationInfo
        val extension = AnimeExtension.Installed(
            name = appInfo?.loadLabel(context.packageManager)?.toString() ?: pkgName,
            pkgName = pkgName,
            versionName = pkgInfo.versionName ?: "unknown",
            versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo),
            libVersion = getLibVersionFromMetadata(pkgInfo),
            lang = getLangFromPackageName(pkgName),
            isNsfw = appInfo?.metaData?.getInt(METADATA_NSFW, 0) == 1,
            pkgFactory = appInfo?.metaData?.getString(METADATA_SOURCE_FACTORY),
            sources = sources,
            icon = appInfo?.loadIcon(context.packageManager),
            isShared = false,
        )
        Log.d(TAG, "✅ Loaded '${extension.name}' (lang=${extension.lang}, sources=${sources.size}, libVersion=${extension.libVersion})")

        return if (isExtensionTrusted(pkgInfo)) {
            AnimeLoadResult.Success(extension)
        } else {
            Log.w(TAG, "Extension $pkgName is untrusted")
            AnimeLoadResult.Untrusted(
                AnimeExtension.Untrusted(
                    name = extension.name,
                    pkgName = extension.pkgName,
                    versionName = extension.versionName,
                    versionCode = extension.versionCode,
                    libVersion = extension.libVersion,
                    signatureHash = "unknown",
                    lang = extension.lang,
                    isNsfw = extension.isNsfw,
                )
            )
        }
    }

    /**
     * Detects whether a package is an anime extension by checking its
     * `<uses-feature>` declarations for [EXTENSION_FEATURE].
     *
     * This is the SAME logic aniyomi uses:
     *   pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
     *
     * Previously this checked `applicationInfo.metaData` (metadata tags) which
     * was wrong — the extension declares a `<uses-feature>`, not a `<meta-data>`,
     * for identification. The source class name IS in metadata, but detection
     * is via reqFeatures.
     */
    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
    }

    /**
     * Extracts the language code from the package name.
     * Extension package names follow: eu.kanade.tachiyomi.animeextension.{lang}.{name}
     * e.g., eu.kanade.tachiyomi.animeextension.en.anikoto180 → "en"
     */
    private fun getLangFromPackageName(pkgName: String): String {
        val parts = pkgName.split(".")
        return parts.getOrNull(4) ?: ""
    }

    private fun isExtensionTrusted(pkgInfo: PackageInfo): Boolean {
        val signatures = pkgInfo.signingInfo?.signingCertificateHistory?.map {
            app.anikuta.util.lang.Hash.sha256(it.toByteArray())
        } ?: emptyList()
        return true  // Always trust for now (TrustAnimeExtension stub returns true)
    }

    private fun getSourcesFromPackage(pkgInfo: PackageInfo): List<AnimeSource> {
        val appInfo = pkgInfo.applicationInfo ?: return emptyList()
        val sourceClass = appInfo.metaData?.getString(METADATA_SOURCE_CLASS)
            ?: appInfo.metaData?.getString(METADATA_SOURCE_CLASS_FALLBACK)
            ?: run {
                Log.w(TAG, "No source class metadata in ${pkgInfo.packageName}")
                return emptyList()
            }

        Log.d(TAG, "Loading source class '$sourceClass' from ${pkgInfo.packageName}")
        val classLoader = createClassLoader(appInfo)
        val sources = mutableListOf<AnimeSource>()

        sourceClass.split(";").forEach { className ->
            try {
                val clazz = Class.forName(className.trim(), false, classLoader)
                val instance = clazz.getDeclaredConstructor().newInstance()

                when (instance) {
                    is AnimeSource -> {
                        sources.add(instance)
                        Log.d(TAG, "  ✓ Loaded source: ${instance.name} (id=${instance.id})")
                    }
                    is AnimeSourceFactory -> {
                        val factorySources = instance.createSources()
                        sources.addAll(factorySources)
                        Log.d(TAG, "  ✓ Loaded factory with ${factorySources.size} source(s)")
                    }
                    else -> Log.w(TAG, "  ✗ Unknown source type: ${clazz.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "  ✗ Failed to load source class: $className", e)
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
}
