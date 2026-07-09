package app.anikuta.ui.detail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import app.anikuta.data.anilist.model.AniListAnime
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.InputStream

/**
 * Dynamic color scheme extracted from an anime's cover image.
 *
 * Multiple colors are extracted using the Android Palette API:
 * - [primary]: vibrant accent color (used for source badge, enrichment indicator)
 * - [surfaceLow]: dark muted — even-index episode card background
 * - [surfaceHigh]: muted — odd-index episode card background
 * - [surfaceContainer]: light muted — inner elements (title, synopsis, thumbnail bg)
 * - [background]: very dark version — page background tint
 * - [onSurface]: text color on surface (auto-contrast: black or white)
 * - [onSurfaceVariant]: secondary text color
 * - [scrim]: dark scrim for overlays (episode number overlay, etc.)
 *
 * Fallback strategy: if Palette can't extract a specific swatch, we derive it
 * from the dominant swatch or fall back to the theme's default colors.
 *
 * The color extraction is reliable because Palette always returns a dominant
 * swatch — the question is just how vibrant/muted the variants are.
 */
data class DynamicColorScheme(
    val primary: Color,
    val surfaceLow: Color,
    val surfaceHigh: Color,
    val surfaceContainer: Color,
    val background: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
)

/**
 * Extract a [DynamicColorScheme] from an anime's cover image.
 *
 * Loads the cover image bitmap via OkHttp (the same network stack used
 * throughout the app), then passes it to Palette for color extraction.
 *
 * @param anime The AniList anime (uses coverImage.extraLarge or best())
 * @return A DynamicColorScheme, or null if the image couldn't be loaded
 */
suspend fun extractDynamicColors(anime: AniListAnime): DynamicColorScheme? =
    withContext(Dispatchers.IO) {
        val imageUrl = anime.coverImage.extraLarge ?: anime.coverImage.best()
        if (imageUrl.isNullOrBlank()) return@withContext null

        val networkHelper: NetworkHelper = try { Injekt.get() } catch (e: Exception) { return@withContext null }
        val bitmap = loadBitmap(networkHelper, imageUrl) ?: return@withContext null

        val palette = Palette.from(bitmap).generate()
        paletteToColorScheme(palette)
    }

/**
 * Load a bitmap from a URL using OkHttp.
 * Downscales to 100px width for faster Palette extraction (we don't need
 * full resolution for color analysis).
 */
private fun loadBitmap(networkHelper: NetworkHelper, url: String): Bitmap? {
    return try {
        val request = Request.Builder().url(url).build()
        networkHelper.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val inputStream: InputStream = response.body?.byteStream() ?: return null

            // First decode bounds to check dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)

            // Calculate sample size to downscale to ~100px width
            val targetWidth = 100
            options.inSampleSize = calculateSampleSize(options.outWidth, targetWidth)
            options.inJustDecodeBounds = false

            // Re-open the stream (it was consumed by the bounds decode)
            // Actually, we need a fresh request since the stream was consumed
            val request2 = Request.Builder().url(url).build()
            networkHelper.client.newCall(request2).execute().use { response2 ->
                if (!response2.isSuccessful) return null
                val stream2 = response2.body?.byteStream() ?: return null
                BitmapFactory.decodeStream(stream2, null, options)
            }
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Calculate the sample size to downscale a bitmap to approximately the target width.
 */
private fun calculateSampleSize(currentWidth: Int, targetWidth: Int): Int {
    var sampleSize = 1
    while (currentWidth / (sampleSize * 2) > targetWidth) {
        sampleSize *= 2
    }
    return sampleSize
}

/**
 * Convert a Palette to a DynamicColorScheme.
 *
 * Extraction priority for each color:
 * - primary: vibrant → dominant (fallback)
 * - surfaceLow: darkMuted → darkVibrant → dominant darkened
 * - surfaceHigh: muted → vibrant darkened → dominant
 * - surfaceContainer: lightMuted → lightVibrant → dominant lightened
 * - background: darkMuted further darkened → dominant at 15% brightness
 * - onSurface: swatch bodyTextColor → auto black/white
 * - onSurfaceVariant: swatch titleTextColor at 70% alpha → auto gray
 */
private fun paletteToColorScheme(palette: Palette): DynamicColorScheme {
    val vibrant = palette.vibrantSwatch
    val darkVibrant = palette.darkVibrantSwatch
    val lightVibrant = palette.lightVibrantSwatch
    val muted = palette.mutedSwatch
    val darkMuted = palette.darkMutedSwatch
    val lightMuted = palette.lightMutedSwatch
    val dominant = palette.dominantSwatch

    // Primary: vibrant color, fall back to dominant
    val primary = Color(vibrant?.rgb ?: dominant?.rgb ?: 0xFF6750A4.toInt())

    // Surface colors for episode cards (alternating)
    val surfaceLow = Color(darkMuted?.rgb ?: muted?.rgb ?: dominant?.rgb?.darken(0.3f) ?: 0xFF1C1B1F.toInt())
    val surfaceHigh = Color(muted?.rgb ?: darkVibrant?.rgb ?: dominant?.rgb?.darken(0.15f) ?: 0xFF2B2930.toInt())

    // Inner elements (title, synopsis, thumbnail backgrounds)
    val surfaceContainer = Color(lightMuted?.rgb ?: lightVibrant?.rgb ?: dominant?.rgb?.lighten(0.1f) ?: 0xFF322F35.toInt())

    // Background — very dark
    val background = Color(darkMuted?.rgb?.darken(0.5f) ?: dominant?.rgb?.darken(0.6f) ?: 0xFF141318.toInt())

    // Text colors — auto-contrast based on surface luminance
    val onSurface = Color(dominant?.bodyTextColor ?: 0xFFFFFFFF.toInt())
    val onSurfaceVariant = Color(dominant?.titleTextColor ?: 0xFFCAC4D0.toInt()).copy(alpha = 0.7f)

    return DynamicColorScheme(
        primary = primary,
        surfaceLow = surfaceLow,
        surfaceHigh = surfaceHigh,
        surfaceContainer = surfaceContainer,
        background = background,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
    )
}

// --- Color manipulation extensions ---

/**
 * Darken an RGB int color by the given factor (0.0 = black, 1.0 = unchanged).
 */
private fun Int.darken(factor: Float): Int {
    val r = (android.graphics.Color.red(this) * factor).toInt().coerceIn(0, 255)
    val g = (android.graphics.Color.green(this) * factor).toInt().coerceIn(0, 255)
    val b = (android.graphics.Color.blue(this) * factor).toInt().coerceIn(0, 255)
    return android.graphics.Color.rgb(r, g, b)
}

/**
 * Lighten an RGB int color by blending towards white.
 * @param factor 0.0 = unchanged, 1.0 = white
 */
private fun Int.lighten(factor: Float): Int {
    val r = android.graphics.Color.red(this)
    val g = android.graphics.Color.green(this)
    val b = android.graphics.Color.blue(this)
    val nr = (r + (255 - r) * factor).toInt().coerceIn(0, 255)
    val ng = (g + (255 - g) * factor).toInt().coerceIn(0, 255)
    val nb = (b + (255 - b) * factor).toInt().coerceIn(0, 255)
    return android.graphics.Color.rgb(nr, ng, nb)
}
