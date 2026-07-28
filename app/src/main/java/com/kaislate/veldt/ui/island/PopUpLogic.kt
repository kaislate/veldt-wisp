// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.ui.island

import androidx.compose.ui.graphics.Color
import com.kaislate.veldt.overlay.ColorExtractor
import com.kaislate.veldt.overlay.DominantColors

/**
 * The decision core behind the expanded panel: where the playhead is, how far along the
 * track that is, how to write it down, and which colour the wave should be.
 *
 * Nothing here draws, animates or reads a clock of its own — every input arrives as a
 * parameter, so the whole of it is assertable from a plain JVM unit test. The composable
 * that sits on top owns the recomposition, the ticker and the gesture plumbing; it asks
 * these functions what the answer is.
 */

/**
 * Where the track has got to, derived rather than observed.
 *
 * A media session publishes its position only when something changes — a seek, a pause, a
 * track boundary. Between those announcements it says nothing at all, so a UI that renders
 * only what it was told shows a progress bar frozen for seconds at a stretch. The panel
 * therefore extrapolates from the last announcement, and [TICK_MS] is how often it asks.
 */
object Playhead {

    /**
     * How often the panel recomputes the extrapolated position.
     *
     * Twice a second: fast enough that the seconds readout never visibly skips a value,
     * slow enough that the cost of the timer is irrelevant.
     */
    const val TICK_MS = 500L

    /**
     * How far a single vertical drag event must travel upward before it counts as a
     * dismissal. Negative because the y-axis grows downward.
     */
    const val CLOSE_SWIPE_PX = -20f

    /**
     * The position the track is at *now*, extrapolated from the last position the session
     * reported.
     *
     * Two details are load-bearing and look arbitrary until you know what they prevent:
     *
     * **The reference clock is `elapsedRealtime`, not wall-clock time.** A `PlaybackState`
     * timestamps its position against the monotonic clock. Measuring the gap in wall time
     * makes the playhead lurch whenever the device syncs NTP or the user crosses a time
     * zone; measuring it monotonically cannot.
     *
     * **[advancing] comes from the `PlaybackState` object itself**, not from the playback
     * state we observe separately elsewhere. The two arrive through different flows and can
     * briefly disagree, and the object is the one that is consistent with [reportedAtMs] —
     * the timestamp being extrapolated from.
     *
     * The speed multiplier is honoured rather than assumed to be `1.0`, so a podcast at
     * 1.5× tracks correctly and a rewinding session walks backwards.
     *
     * @param reportedMs the position the session last announced.
     * @param reportedAtMs the `elapsedRealtime` at which it announced it.
     * @param speed the playback speed multiplier; may be negative while rewinding.
     * @param advancing whether the session's own state says the position is moving.
     * @param nowMs the current `elapsedRealtime`, same base as [reportedAtMs].
     * @param durationMs the track length, or `0` or less when it is unknown — a live stream
     *   has no end to clamp against, and clamping an unknown length to zero would peg the
     *   bar at the start, so an unknown duration is returned unclamped.
     */
    fun positionMs(
        reportedMs: Long,
        reportedAtMs: Long,
        speed: Float,
        advancing: Boolean,
        nowMs: Long,
        durationMs: Long,
    ): Long {
        val extrapolated =
            if (advancing) reportedMs + ((nowMs - reportedAtMs) * speed).toLong()
            else reportedMs
        return if (durationMs > 0L) extrapolated.coerceIn(0L, durationMs) else extrapolated
    }

    /**
     * [positionMs] as a fraction of the track, for the progress bar.
     *
     * `0f` when the duration is unknown: with no end there is no fraction to report, and
     * an empty bar is a better lie than a dividing-by-zero one.
     */
    fun progress(positionMs: Long, durationMs: Long): Float =
        if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /**
     * A duration as `m:ss` — `4:07`, `93:20`. Seconds truncate rather than round, so the
     * readout matches the elapsed second the listener has actually heard, and negative
     * inputs read as zero.
     *
     * There is deliberately no hour field. The panel is narrow, and tracks over an hour are
     * rare enough that letting the minutes run past 60 is preferable to widening the layout
     * for everyone.
     */
    fun formatTime(ms: Long): String {
        val totalSeconds = (if (ms > 0L) ms else 0L) / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "$minutes:" + seconds.toString().padStart(2, '0')
    }

    /**
     * Whether one vertical drag event counts as a close gesture: strictly more than
     * [CLOSE_SWIPE_PX] of upward movement *within that single event*.
     *
     * Per-event rather than accumulated, on purpose. A slow, deliberate upward drag never
     * exceeds the threshold in any one event and leaves the panel open; a flick does it in
     * one and closes it.
     */
    fun isCloseSwipe(dragDeltaPx: Float): Boolean = dragDeltaPx < CLOSE_SWIPE_PX
}

/** Resolves the user's colour settings against the colours pulled from the album art. */
object PopUpColors {

    /**
     * The colour the wave is actually drawn in, given the user's "wave colour" setting.
     *
     * - `"white"` — pure white, for anyone who wants the artwork's colours nowhere near the panel.
     * - `"accent-light"` — the extracted accent with every channel moved 40% of the way to
     *   `1.0`. An accent pulled from dark artwork vanishes against the dark panel; lifting
     *   the channels keeps the hue and buys legibility without a contrast calculation.
     * - anything else, `"auto"` included — hand the accent to
     *   [ColorExtractor.ensureContrast], which nudges it toward white only as far as the
     *   background actually requires.
     *
     * Unrecognised modes fall through to auto rather than throwing: the setting is a
     * persisted string, and a stale or hand-edited value should degrade to the default
     * rather than take the panel down.
     */
    fun effectiveAccent(mode: String, dom: DominantColors): Color = when (mode) {
        "white" -> Color.White
        "accent-light" -> Color(
            red = dom.accent.red + (1f - dom.accent.red) * 0.4f,
            green = dom.accent.green + (1f - dom.accent.green) * 0.4f,
            blue = dom.accent.blue + (1f - dom.accent.blue) * 0.4f,
            alpha = 1f,
        )
        else -> ColorExtractor.ensureContrast(dom.accent, dom.bg)
    }
}
