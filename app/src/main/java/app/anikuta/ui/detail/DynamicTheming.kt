package app.anikuta.ui.detail

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * Dynamic color scheme derived from a single AniList cover color.
 *
 * AniList provides `coverImage.color` as a hex string (e.g., "#FF5722").
 * We take that single color and generate a full set of variants for the
 * detail page theming:
 *
 * - [accent]: the original cover color — used for buttons, source badge,
 *   enrichment indicator, and other accent elements.
 * - [surfaceLow]: a dark, desaturated variant — even-index episode card background.
 * - [surfaceHigh]: a slightly lighter variant — odd-index episode card background.
 * - [surfaceContainer]: a medium variant — inner elements (title, synopsis, thumbnail bg).
 * - [background]: a very dark variant — page background tint.
 * - [onSurface]: text color on surfaces (auto black/white based on luminance).
 * - [onSurfaceVariant]: secondary text color (slightly muted).
 * - [scrim]: dark scrim for overlays.
 *
 * The variant generation uses HSL manipulation:
 *  - Convert RGB → HSL
 *  - Adjust lightness and saturation to create the variants
 *  - Keep the same hue so all colors feel cohesive
 *
 * This approach is reliable, fast (no network/image loading), and uses the
 * exact color AniList curates for each anime.
 */
data class DynamicColorScheme(
    val accent: Color,
    val surfaceLow: Color,
    val surfaceHigh: Color,
    val surfaceContainer: Color,
    val background: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
)

/**
 * Generate a [DynamicColorScheme] from an AniList cover color.
 *
 * @param coverColor The AniList cover color (already parsed as a Compose Color)
 * @return A full color scheme with 7 variants derived from the cover color
 */
fun generateDynamicScheme(coverColor: Color): DynamicColorScheme {
    val (h, s, l) = rgbToHsl(coverColor)

    // Accent: the original color, maybe slightly boosted saturation for vibrancy
    val accent = hslToColor(h, min(s, 1f), l)

    // Surface variants — dark, desaturated versions of the same hue
    // These create the alternating episode card colors
    val surfaceLow = hslToColor(h, s * 0.25f, 0.12f)   // very dark, low saturation
    val surfaceHigh = hslToColor(h, s * 0.30f, 0.18f)  // slightly lighter
    val surfaceContainer = hslToColor(h, s * 0.35f, 0.24f) // medium for inner elements

    // Background — very dark with a hint of the cover hue
    val background = hslToColor(h, s * 0.20f, 0.08f)

    // Text colors — auto-contrast
    // On dark surfaces, use white-ish; on light surfaces, use dark
    val onSurface = Color.White.copy(alpha = 0.92f)
    val onSurfaceVariant = Color.White.copy(alpha = 0.65f)

    return DynamicColorScheme(
        accent = accent,
        surfaceLow = surfaceLow,
        surfaceHigh = surfaceHigh,
        surfaceContainer = surfaceContainer,
        background = background,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
    )
}

/**
 * Convert a Compose Color to HSL (Hue: 0-360, Saturation: 0-1, Lightness: 0-1).
 */
private fun rgbToHsl(color: Color): Triple<Float, Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue

    val maxVal = max(r, max(g, b))
    val minVal = min(r, min(g, b))
    val delta = maxVal - minVal

    // Lightness
    val l = (maxVal + minVal) / 2f

    // Saturation
    val s = if (delta == 0f) {
        0f
    } else {
        delta / (1f - kotlin.math.abs(2f * l - 1f))
    }

    // Hue
    val h = when {
        delta == 0f -> 0f
        maxVal == r -> ((g - b) / delta) % 6f
        maxVal == g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    } * 60f

    val hue = if (h < 0) h + 360f else h

    return Triple(hue, s, l)
}

/**
 * Convert HSL to a Compose Color.
 * @param h Hue 0-360
 * @param s Saturation 0-1
 * @param l Lightness 0-1
 */
private fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f

    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Color(r + m, g + m, b + m)
}
