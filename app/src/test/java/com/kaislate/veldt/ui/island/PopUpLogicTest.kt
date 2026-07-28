// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.ui.island

import androidx.compose.ui.graphics.Color
import com.kaislate.veldt.overlay.DominantColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PopUpLogicTest {

    // ---- extrapolation ----

    private fun pos(
        reportedMs: Long = 30_000L,
        reportedAtMs: Long = 1_000_000L,
        speed: Float = 1f,
        advancing: Boolean = true,
        nowMs: Long = 1_000_000L,
        durationMs: Long = 300_000L,
    ) = Playhead.positionMs(reportedMs, reportedAtMs, speed, advancing, nowMs, durationMs)

    @Test fun `a paused session reports exactly what it last said`() {
        assertEquals(30_000L, pos(advancing = false, nowMs = 1_099_000L))
    }

    @Test fun `a playing session advances with the monotonic clock`() {
        assertEquals(32_500L, pos(nowMs = 1_002_500L))
    }

    @Test fun `playback speed is honoured`() {
        assertEquals(33_000L, pos(speed = 1.5f, nowMs = 1_002_000L))
        assertEquals(31_000L, pos(speed = 0.5f, nowMs = 1_002_000L))
    }

    @Test fun `a rewinding session walks backwards`() {
        assertEquals(28_000L, pos(speed = -1f, nowMs = 1_002_000L))
    }

    @Test fun `a fractional millisecond is truncated toward zero, not rounded or floored`() {
        // 1001ms at 1.5x is 1501.5ms of progress: forwards it truncates down to 1501...
        assertEquals(31_501L, pos(speed = 1.5f, nowMs = 1_001_001L))
        // ...and backwards it truncates up to -1501, which floor() would not do.
        assertEquals(28_499L, pos(speed = -1.5f, nowMs = 1_001_001L))
    }

    @Test fun `the position is clamped to the track length`() {
        assertEquals(300_000L, pos(reportedMs = 299_000L, nowMs = 1_060_000L))
    }

    @Test fun `the position never goes below zero`() {
        assertEquals(0L, pos(reportedMs = 1_000L, speed = -1f, nowMs = 1_010_000L))
    }

    @Test fun `an unknown duration leaves the position unclamped`() {
        assertEquals(90_000L, pos(reportedMs = 30_000L, nowMs = 1_060_000L, durationMs = 0L))
    }

    @Test fun `zero elapsed time changes nothing`() {
        assertEquals(30_000L, pos(nowMs = 1_000_000L))
    }

    // ---- progress ----

    @Test fun `progress is the position over the duration`() {
        assertEquals(0.5f, Playhead.progress(50L, 100L), 0.0001f)
        assertEquals(0f, Playhead.progress(0L, 100L), 0.0001f)
        assertEquals(1f, Playhead.progress(100L, 100L), 0.0001f)
    }

    @Test fun `progress is clamped both ways`() {
        assertEquals(1f, Playhead.progress(500L, 100L), 0.0001f)
        assertEquals(0f, Playhead.progress(-500L, 100L), 0.0001f)
    }

    @Test fun `an unknown duration reports no progress rather than dividing by zero`() {
        assertEquals(0f, Playhead.progress(50L, 0L), 0.0001f)
        assertEquals(0f, Playhead.progress(50L, -1L), 0.0001f)
        // A negative position over a negative duration divides out to a plausible-looking
        // fraction; the duration check has to reject it before the arithmetic ever runs.
        assertEquals(0f, Playhead.progress(-50L, -100L), 0.0001f)
    }

    // ---- time formatting ----

    @Test fun `time is minutes and zero-padded seconds`() {
        assertEquals("0:00", Playhead.formatTime(0L))
        assertEquals("0:07", Playhead.formatTime(7_400L))
        assertEquals("1:00", Playhead.formatTime(60_000L))
        assertEquals("4:07", Playhead.formatTime(247_000L))
    }

    @Test fun `long tracks keep counting in minutes`() {
        assertEquals("93:20", Playhead.formatTime(5_600_000L))
    }

    @Test fun `negative time reads as zero`() {
        assertEquals("0:00", Playhead.formatTime(-5_000L))
    }

    @Test fun `seconds truncate rather than round`() {
        assertEquals("0:01", Playhead.formatTime(1_999L))
    }

    // ---- the close-swipe threshold ----

    @Test fun `a firm upward flick closes the panel`() {
        assertTrue(Playhead.isCloseSwipe(-21f))
        assertTrue(Playhead.isCloseSwipe(-100f))
    }

    @Test fun `a gentle drag does not`() {
        assertFalse(Playhead.isCloseSwipe(-20f))
        assertFalse(Playhead.isCloseSwipe(-5f))
        assertFalse(Playhead.isCloseSwipe(0f))
    }

    @Test fun `a downward drag never closes the panel`() {
        assertFalse(Playhead.isCloseSwipe(50f))
    }

    @Test fun `the threshold is twenty pixels upward`() {
        assertEquals(-20f, Playhead.CLOSE_SWIPE_PX, 0.0001f)
    }

    @Test fun `the tick period is 500ms`() {
        assertEquals(500L, Playhead.TICK_MS)
    }

    // ---- accent resolution ----

    private val dom = DominantColors(
        bg = Color(0xFF101014),
        onBg = Color(0xFFF5F5F5),
        accent = Color(0xFF204060),
    )

    @Test fun `white mode is white`() {
        assertEquals(Color.White, PopUpColors.effectiveAccent("white", dom))
    }

    @Test fun `accent-light lifts every channel forty percent toward white`() {
        val out = PopUpColors.effectiveAccent("accent-light", dom)
        // Compose stores an sRGB Color as 8 bits per channel, so any constructed colour is
        // quantised to the nearest 1/255 and can sit up to 0.5/255 (~0.00196) off the exact
        // arithmetic. The tolerance is that bound, not a shrug: a wrong lift factor misses
        // by ~0.07 per channel, two orders of magnitude wider.
        val quantisation = 0.002f
        assertEquals(dom.accent.red + (1f - dom.accent.red) * 0.4f, out.red, quantisation)
        assertEquals(dom.accent.green + (1f - dom.accent.green) * 0.4f, out.green, quantisation)
        assertEquals(dom.accent.blue + (1f - dom.accent.blue) * 0.4f, out.blue, quantisation)
        // ...and the result is opaque whatever the source alpha was.
        val fromTranslucent = PopUpColors.effectiveAccent(
            "accent-light",
            dom.copy(accent = dom.accent.copy(alpha = 0.3f)),
        )
        assertEquals(1f, fromTranslucent.alpha, quantisation)
    }

    @Test fun `accent-light stays brighter than the source but is not white`() {
        val out = PopUpColors.effectiveAccent("accent-light", dom)
        assertTrue(out.red > dom.accent.red)
        assertTrue(out.blue > dom.accent.blue)
        assertTrue(out.red < 1f)
    }

    @Test fun `auto mode defers to the contrast helper`() {
        assertEquals(
            com.kaislate.veldt.overlay.ColorExtractor.ensureContrast(dom.accent, dom.bg),
            PopUpColors.effectiveAccent("auto", dom),
        )
        // The fixture above already clears the helper's contrast bar, so it would pass even
        // against an implementation that returned the raw accent. This one does not: the
        // accent is nearly the background, and the helper has to lift it.
        val murky = DominantColors(
            bg = Color(0xFF101014),
            onBg = Color(0xFFF5F5F5),
            accent = Color(0xFF14161A),
        )
        val lifted = com.kaislate.veldt.overlay.ColorExtractor.ensureContrast(murky.accent, murky.bg)
        assertNotEquals(murky.accent, lifted)
        assertEquals(lifted, PopUpColors.effectiveAccent("auto", murky))
    }

    @Test fun `an unrecognised mode behaves like auto`() {
        assertEquals(
            PopUpColors.effectiveAccent("auto", dom),
            PopUpColors.effectiveAccent("something-else", dom),
        )
    }
}
