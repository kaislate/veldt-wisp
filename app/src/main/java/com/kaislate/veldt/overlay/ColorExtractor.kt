// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.overlay

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette

/**
 * Every colour the pill draws itself in, derived from one piece of album art.
 *
 * The first three fields are read positionally at two construction sites, so their order is
 * part of the contract. [waveColors] carries the gradient stops in the order the wave
 * assigns them and may legitimately be empty — artwork that yields no swatches at all is a
 * real case, not an error.
 */
@Immutable
data class DominantColors(
    val bg: Color,
    val onBg: Color,
    val accent: Color,
    val waveColors: List<Color> = emptyList(),
)

/**
 * Turns album art into the pill's palette.
 *
 * Two things here look like mistakes and are not.
 *
 * **The luminance is approximate.** [approximateLuminance] applies the Rec. 709 / sRGB luma
 * weights — a published standard, nobody's authorship — to the *raw* sRGB channel values,
 * with no gamma expansion. That makes it approximate luminance, not WCAG relative
 * luminance. It is deliberate: the value drives a single binary light-or-dark text choice
 * between a near-white and a near-black foreground, and over that decision the
 * approximation never disagrees with the exact form anywhere near the boundary that
 * matters. Switching to the WCAG form would move the cut point and repaint the title on
 * every mid-tone cover.
 *
 * **The contrast bar is low.** [CONTRAST_TARGET] is 1.6:1, far under any accessibility
 * threshold. It is not an accessibility control: it is the point at which an accent colour
 * stops disappearing into the panel behind it, applied to decoration rather than to text.
 * The panel's actual text colour is [DominantColors.onBg], which is chosen for maximum
 * separation and never goes through this path.
 */
object ColorExtractor {

    // ---- the darkness cut (§3.3) ----

    /** Rec. 709 / sRGB luma weight for the red channel. */
    internal const val LUMA_RED = 0.2126f

    /** Rec. 709 / sRGB luma weight for the green channel. */
    internal const val LUMA_GREEN = 0.7152f

    /** Rec. 709 / sRGB luma weight for the blue channel. */
    internal const val LUMA_BLUE = 0.0722f

    /**
     * Below this approximate luminance a background counts as dark and takes the light
     * foreground. Strictly below: a colour landing exactly on the cut is treated as light.
     */
    internal const val DARK_CUT = 0.5f

    // ---- the contrast lift (§3.5) ----

    /** The luminance ratio [ensureContrast] lifts a foreground until it reaches. */
    internal const val CONTRAST_TARGET = 1.6f

    /** The constant added to both luminances so the ratio stays finite at black-on-black. */
    internal const val CONTRAST_OFFSET = 0.05f

    /** Each lift step closes this fraction of the remaining distance to 1.0. */
    internal const val LIFT_STEP = 0.25f

    /**
     * The most lift steps [ensureContrast] will take. The cap is what makes the loop
     * total: against a white background no foreground can ever reach [CONTRAST_TARGET],
     * so without it the call would never return.
     */
    internal const val MAX_LIFT_STEPS = 8

    // ---- the palette values (§3.2, §3.4) ----

    /** Background of last resort when the artwork yields no dominant swatch at all. */
    private val FALLBACK_DOMINANT = Color(0xFF222222)

    /** Foreground for a dark background. */
    private val ON_DARK = Color(0xFFF5F5F5)

    /** Foreground for a light background. */
    private val ON_LIGHT = Color(0xFF121212)

    /**
     * What the pill wears before any artwork has been extracted, and what it falls back to
     * when there is none. Public so the UI can use it instead of carrying its own copy of
     * the same three values.
     */
    val NEUTRAL = DominantColors(
        bg = Color(0xFF1E1E1E),
        onBg = Color(0xFFF5F5F5),
        accent = Color(0xFF888888),
    )

    /**
     * Derives the four-part palette from [bitmap], or returns [NEUTRAL] if there is none.
     *
     * The background walks a fallback chain — dominant, then vibrant, then dark vibrant —
     * with each step supplying the next one's default, so a cover that produces only a
     * dominant swatch still lands somewhere sensible. The accent is taken one step earlier,
     * from the vibrant colour, before the dark-vibrant narrowing: it wants to be the
     * liveliest colour on the cover, not the deepest. It is handed over unlifted; whether
     * to lift it is the caller's decision, via [ensureContrast].
     */
    fun extract(bitmap: Bitmap?): DominantColors {
        val source = bitmap ?: return NEUTRAL
        val readable = readableCopyOf(source) ?: return NEUTRAL

        // Palette's default filter rejects near-black and near-white swatches. On moody or
        // monochrome artwork that leaves no swatches at all and the whole derivation falls
        // through to the defaults below, so it is cleared.
        val palette = Palette.from(readable).clearFilters().generate()

        val dominant = palette.getDominantColor(FALLBACK_DOMINANT.toArgb())
        val vibrant = palette.getVibrantColor(dominant)
        val darkVibrant = palette.getDarkVibrantColor(vibrant)

        val bg = Color(darkVibrant)
        return DominantColors(
            bg = bg,
            onBg = if (isDark(bg)) ON_DARK else ON_LIGHT,
            accent = Color(vibrant),
            waveColors = waveColorsOf(palette),
        )
    }

    /**
     * Nudges [fg] toward white until it reads against [bg], and no further.
     *
     * The ratio is checked *before* each step, so a colour that already clears the bar is
     * returned exactly as it came in and a colour needing three steps gets exactly three.
     * Alpha is carried through untouched — the caller may be lifting a translucent accent
     * and has no reason to lose its transparency to a contrast decision.
     */
    fun ensureContrast(fg: Color, bg: Color): Color {
        val backgroundLuminance = approximateLuminance(bg)
        var lifted = fg
        repeat(MAX_LIFT_STEPS) {
            val ratio = (approximateLuminance(lifted) + CONTRAST_OFFSET) /
                (backgroundLuminance + CONTRAST_OFFSET)
            if (ratio >= CONTRAST_TARGET) return lifted
            lifted = Color(
                red = lifted.red + (1f - lifted.red) * LIFT_STEP,
                green = lifted.green + (1f - lifted.green) * LIFT_STEP,
                blue = lifted.blue + (1f - lifted.blue) * LIFT_STEP,
                alpha = lifted.alpha,
            )
        }
        // The bar was unreachable within the cap. Returning the best reached beats looping.
        return lifted
    }

    /**
     * The Rec. 709 luma weights over raw sRGB channels. See the note on the object: this is
     * approximate luminance on purpose and must not be "corrected" to the WCAG form.
     */
    internal fun approximateLuminance(color: Color): Float =
        LUMA_RED * color.red + LUMA_GREEN * color.green + LUMA_BLUE * color.blue

    /** Whether [color] is dark enough to want the light foreground. */
    internal fun isDark(color: Color): Boolean = approximateLuminance(color) < DARK_CUT

    /**
     * The gradient stops, in the order the wave assigns them positionally.
     *
     * The order is the contract, not a preference: the wave and the premium effects map
     * these onto fixed stops by index, so reordering them repaints the wave on every track.
     * Swatches the artwork did not produce drop out, and duplicates are removed keeping the
     * first occurrence, because a cover whose vibrant and light-vibrant swatches coincide
     * should give the wave one colour rather than the same one twice.
     */
    private fun waveColorsOf(palette: Palette): List<Color> = listOfNotNull(
        palette.vibrantSwatch,
        palette.lightVibrantSwatch,
        palette.darkVibrantSwatch,
        palette.mutedSwatch,
        palette.lightMutedSwatch,
    ).map { Color(it.rgb) }.distinct()

    /**
     * A bitmap `Palette` can actually read.
     *
     * `Palette` samples pixels on the CPU; a [Bitmap.Config.HARDWARE] bitmap lives in
     * graphics memory with no backing array and throws on the attempt. Only that case is
     * copied — album art runs to several megabytes and the ordinary path must not allocate
     * a second one. The copy returns null rather than throwing when the allocation fails,
     * and null here means the caller falls back to [NEUTRAL] instead of crashing.
     */
    private fun readableCopyOf(bitmap: Bitmap): Bitmap? =
        if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
}
