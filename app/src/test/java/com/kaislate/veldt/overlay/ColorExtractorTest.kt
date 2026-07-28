package com.kaislate.veldt.overlay

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pure half of [ColorExtractor]: the light/dark cut, the contrast lift and the
 * neutral fallback.
 *
 * `extract` on a real bitmap is deliberately absent. `Palette` samples pixels through the
 * platform `Bitmap`, which is a stub in a plain JVM test, and mocking `Palette` would only
 * assert that the code calls the methods this test already knows it calls. The bitmap path
 * is verified on device by the acceptance matrix; what is verified here is everything that
 * is arithmetic.
 *
 * **On tolerances.** Compose packs an sRGB [Color] at 8 bits per channel, so every
 * constructed colour is quantised to the nearest 1/255 and can sit up to 0.5/255 (~0.00196)
 * away from the exact arithmetic. `0.002f` is that bound and is the tightest delta any
 * single-step assertion here can use. Assertions that span several lift steps use a wider
 * delta, because the quantisation error compounds once per step; each of those says so and
 * names the value it is discriminating against.
 */
class ColorExtractorTest {

    // ------------------------------------------------------------------
    // The darkness test (§3.3)
    // ------------------------------------------------------------------

    @Test
    fun `pure black is dark`() {
        assertTrue(ColorExtractor.isDark(Color.Black))
    }

    @Test
    fun `pure white is not dark`() {
        assertFalse(ColorExtractor.isDark(Color.White))
    }

    @Test
    fun `the three weights are the Rec 709 luma coefficients`() {
        // Kills every permutation of the three coefficients: the closest pair of them
        // differ by 0.14, seventy times the quantisation tolerance.
        assertEquals(0.2126f, ColorExtractor.approximateLuminance(Color.Red), 0.002f)
        assertEquals(0.7152f, ColorExtractor.approximateLuminance(Color.Green), 0.002f)
        assertEquals(0.0722f, ColorExtractor.approximateLuminance(Color.Blue), 0.002f)
    }

    @Test
    fun `green is light and blue is dark because the weights are not equal`() {
        // The behavioural face of the test above: an even-weights implementation would
        // put both of these on the same side of the cut.
        assertFalse(ColorExtractor.isDark(Color.Green))
        assertTrue(ColorExtractor.isDark(Color.Blue))
    }

    @Test
    fun `mid grey is not dark because the cut is strictly below`() {
        val midGrey = Color(0.5f, 0.5f, 0.5f)
        assertEquals(0.5f, ColorExtractor.approximateLuminance(midGrey), 0.002f)
        assertFalse(ColorExtractor.isDark(midGrey))
    }

    @Test
    fun `no gamma expansion is applied`() {
        // Mid grey's WCAG relative luminance is 0.216, comfortably below the cut; its raw
        // sRGB luma is 0.502, just above it. Only the raw form calls this colour light, so
        // this test fails the moment somebody "corrects" §3.3 to the WCAG form.
        assertFalse(ColorExtractor.isDark(Color(0.5f, 0.5f, 0.5f)))
    }

    @Test
    fun `the two greys either side of the cut fall on opposite sides`() {
        // 127 and 128 are adjacent representable greys straddling 0.5 (0.4980 and 0.5020),
        // so this pair pins the cut to within 0.002 in either direction.
        assertTrue(ColorExtractor.isDark(Color(0xFF7F7F7F)))
        assertFalse(ColorExtractor.isDark(Color(0xFF808080)))
    }

    @Test
    fun `a colour landing exactly on the cut is not dark`() {
        // 0x0DA371 is one of the thirteen 8-bit sRGB colours whose luma evaluates to
        // exactly 0.5f in IEEE single precision, so it is the only kind of input that can
        // tell `<` apart from `<=`. The equality is asserted first: if a future toolchain
        // ever stops producing the exact tie, this fails loudly rather than going vacuous.
        val onTheCut = Color(0xFF0DA371)
        assertEquals(0.5f, ColorExtractor.approximateLuminance(onTheCut), 0f)
        assertFalse(ColorExtractor.isDark(onTheCut))
    }

    @Test
    fun `the cut is one half`() {
        assertEquals(0.5f, ColorExtractor.DARK_CUT, 0f)
    }

    // ------------------------------------------------------------------
    // ensureContrast (§3.5)
    // ------------------------------------------------------------------

    /** The documented lift, re-derived here so the expectations do not lean on the code. */
    private fun oneStep(c: Color): Color = Color(
        red = c.red + (1f - c.red) * 0.25f,
        green = c.green + (1f - c.green) * 0.25f,
        blue = c.blue + (1f - c.blue) * 0.25f,
        alpha = c.alpha,
    )

    private fun stepsOf(c: Color, n: Int): Color = generateSequence(c, ::oneStep).elementAt(n)

    @Test
    fun `a colour comfortably above the bar is returned unchanged`() {
        val fg = Color.White
        assertEquals(fg, ColorExtractor.ensureContrast(fg, Color.Black))
    }

    @Test
    fun `a colour just above the bar is returned unchanged`() {
        // 1.612:1 — above 1.6 but below 1.7, so a raised target would lift it.
        val fg = Color(0xFF5A5A5A)
        assertEquals(fg, ColorExtractor.ensureContrast(fg, Color(0xFF333333)))
    }

    @Test
    fun `a colour landing exactly on the target ratio is returned unchanged`() {
        // "At least 1.6" means 1.6 itself passes. #615A3B against #333333 is a pair whose
        // ratio evaluates to exactly 1.6f in IEEE single precision, so it is the only kind
        // of input that can tell `>=` apart from `>`. The exactness is asserted first so
        // this cannot quietly stop testing anything.
        val fg = Color(0xFF615A3B)
        val bg = Color(0xFF333333)
        val ratio = (ColorExtractor.approximateLuminance(fg) + 0.05f) /
            (ColorExtractor.approximateLuminance(bg) + 0.05f)
        assertEquals(1.6f, ratio, 0f)
        assertEquals(fg, ColorExtractor.ensureContrast(fg, bg))
    }

    @Test
    fun `a colour just below the bar is lifted`() {
        // 1.580:1 — below 1.6 but above 1.5, so a lowered target would leave it alone.
        val fg = Color(0xFF585858)
        val out = ColorExtractor.ensureContrast(fg, Color(0xFF333333))
        assertNotEquals(fg, out)
        assertEquals(stepsOf(fg, 1), out)
    }

    @Test
    fun `a colour far below the bar is genuinely lifted`() {
        // The fixture that an implementation returning `fg` untouched cannot survive:
        // this accent sits at 1.19:1 against the panel it is drawn on.
        val fg = Color(0xFF14161A)
        val bg = Color(0xFF101014)
        val out = ColorExtractor.ensureContrast(fg, bg)
        assertNotEquals(fg, out)
        assertTrue(out.red > fg.red)
        assertTrue(out.green > fg.green)
        assertTrue(out.blue > fg.blue)
    }

    @Test
    fun `the first step moves each channel a quarter of the way to white`() {
        val fg = Color(0xFF14161A)
        val out = ColorExtractor.ensureContrast(fg, Color(0xFF101014))
        assertEquals(fg.red + (1f - fg.red) * 0.25f, out.red, 0.002f)
        assertEquals(fg.green + (1f - fg.green) * 0.25f, out.green, 0.002f)
        assertEquals(fg.blue + (1f - fg.blue) * 0.25f, out.blue, 0.002f)
    }

    @Test
    fun `a colour needing one step gets exactly one`() {
        // One step clears the bar by a wide margin (3.20:1), so a second would be visible.
        val fg = Color(0xFF14161A)
        val out = ColorExtractor.ensureContrast(fg, Color(0xFF101014))
        assertEquals(stepsOf(fg, 1), out)
        assertNotEquals(stepsOf(fg, 2), out)
    }

    @Test
    fun `alpha survives the lift`() {
        val fg = Color(0.08f, 0.09f, 0.10f, alpha = 0.5f)
        val out = ColorExtractor.ensureContrast(fg, Color(0xFF101014))
        assertNotEquals(fg, out)
        assertEquals(fg.alpha, out.alpha, 0.002f)
    }

    @Test
    fun `white on white terminates and stays white`() {
        // 1.6:1 is unreachable against a white background at any lift, so this is the
        // case that hangs if the step cap is dropped.
        assertEquals(Color.White, ColorExtractor.ensureContrast(Color.White, Color.White))
    }

    @Test
    fun `the lift stops after eight steps rather than converging`() {
        // Nothing reaches 1.6:1 against white, so this runs the loop to its cap.
        val fg = Color.Black
        val out = ColorExtractor.ensureContrast(fg, Color.White)
        assertEquals(stepsOf(fg, 8), out)
        assertNotEquals(stepsOf(fg, 7), out)
        assertNotEquals(stepsOf(fg, 9), out)
        // 0.006f, not 0.002f: eight quantisation roundings compound. It still separates
        // seven steps (0.867) and nine (0.925) from eight (0.902) by a wide margin.
        assertEquals(0.902f, out.red, 0.006f)
    }

    @Test
    fun `the target ratio is one point six`() {
        assertEquals(1.6f, ColorExtractor.CONTRAST_TARGET, 0f)
    }

    @Test
    fun `the step is a quarter of the remaining distance`() {
        assertEquals(0.25f, ColorExtractor.LIFT_STEP, 0f)
    }

    @Test
    fun `the cap is eight steps`() {
        assertEquals(8, ColorExtractor.MAX_LIFT_STEPS)
    }

    // ------------------------------------------------------------------
    // The neutral fallback (§3.4)
    // ------------------------------------------------------------------

    @Test
    fun `extract of null is the neutral palette`() {
        val out = ColorExtractor.extract(null)
        assertEquals(Color(0xFF1E1E1E), out.bg)
        assertEquals(Color(0xFFF5F5F5), out.onBg)
        assertEquals(Color(0xFF888888), out.accent)
        assertTrue(out.waveColors.isEmpty())
    }

    @Test
    fun `NEUTRAL is that same palette`() {
        assertEquals(ColorExtractor.NEUTRAL, ColorExtractor.extract(null))
    }

    @Test
    fun `waveColors defaults to empty`() {
        // The two UI files construct their placeholder with three positional arguments.
        assertTrue(DominantColors(Color.Black, Color.White, Color.Gray).waveColors.isEmpty())
    }
}
