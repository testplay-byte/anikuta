package app.anikuta.ui.detail.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Applies a grayscale (desaturation) effect to ALL content within a composable,
 * including text, icons, images, and backgrounds.
 *
 * ## Why this approach
 *
 * Previous attempts used `drawWithContent` + `ColorFilter.colorMatrix` on a `Paint`
 * object. This only affected rasterised draw operations (images) and did NOT
 * affect Compose's text rendering pipeline — themed text colors remained
 * unchanged, giving a "half-grayscale" appearance.
 *
 * The fix uses [RenderEffect.createColorFilterEffect] via [Modifier.graphicsLayer],
 * which applies the color filter at the **GPU render-effect level**. This
 * intercepts the entire rendered output of the layer (text, icons, shapes,
 * images) and desaturates it uniformly.
 *
 * ## Platform support
 *
 * - **Android 12+ (API 31+)**: Full grayscale via [RenderEffect].
 * - **Below API 31**: Only alpha dimming is applied (`RenderEffect` is not
 *   available). The content appears dimmed but not fully desaturated.
 *
 * @param enabled When `true`, applies grayscale + alpha dimming.
 * @param alpha   The alpha multiplier applied to the entire content (default 0.55).
 *                Lower values make the content more visually "muted".
 */
fun Modifier.grayscaleIfSeen(
    enabled: Boolean,
    alpha: Float = 0.55f,
): Modifier = if (!enabled) {
    this
} else {
    this.graphicsLayer {
        this.alpha = alpha
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val matrix = ColorMatrix().apply { setSaturation(0f) }
            this.renderEffect = RenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(matrix)
            ).asComposeRenderEffect()
        }
    }
}
