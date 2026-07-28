// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.ui.island

import android.media.session.PlaybackState

/**
 * How the collapsed pill arranges its contents.
 *
 * The pill is a wrap-content strip, so the arrangement decides its whole silhouette: whether
 * it is a single row of artwork and text, or grows sideways for transport buttons, or grows
 * taller to carry them underneath.
 */
enum class PillArrangement {
    /** Artwork and text only — the pill at its narrowest. */
    TEXT_ONLY,

    /** Artwork and text on one line, transport buttons on a second line beneath. */
    CONTROLS_BELOW,

    /** Transport buttons first, then artwork and text. */
    CONTROLS_LEFT,

    /** Artwork and text first, then transport buttons. */
    CONTROLS_RIGHT,
}

/** One transport button on the pill. */
enum class PillButton {
    PREVIOUS,
    PLAY_PAUSE,
    NEXT,
}

/**
 * The collapsed pill's decision core: shape, buttons, gestures and playback, none of which
 * needs a frame, a clock or a canvas to answer.
 *
 * The composable above owns the drawing and the recomposition; it asks these functions what
 * to draw. Everything here is a plain value question, which is the point — the pill's
 * appearance is the part of the app hardest to test on a device, so as much of it as can be
 * decided in advance is decided here, where it is assertable.
 *
 * The only Android reference is [PlaybackState]'s state constants. They are `static final`
 * ints, inlined at compile time, so this object still runs on a bare JVM with no Android
 * runtime present — the same arrangement [PlaybackClassifier] already uses.
 */
object PillLayout {

    /**
     * How far one vertical drag event must travel, in pixels, before the pill treats it as a
     * flick toward its screen edge and stashes.
     *
     * Deliberately **not** the panel's `Playhead.CLOSE_SWIPE_PX`, which is `-20f`. The two
     * govern different gestures on different surfaces — dismissing the expanded panel versus
     * hiding the collapsed pill — and they have been tuned apart. Unifying them would change
     * the feel of both.
     */
    const val STASH_THRESHOLD_PX = 18f

    /**
     * Which arrangement the pill takes, from the user's two appearance settings.
     *
     * When the controls are switched off the position is meaningless and is ignored outright,
     * rather than being allowed to select a layout that has no controls to place.
     *
     * An unrecognised position resolves to the right-hand layout rather than throwing: the
     * setting is a persisted string, and a stale or hand-edited value should land on the
     * default rather than take the overlay down.
     */
    fun arrangementFor(showControls: Boolean, controlPosition: String): PillArrangement =
        if (!showControls) {
            PillArrangement.TEXT_ONLY
        } else when (controlPosition) {
            "below" -> PillArrangement.CONTROLS_BELOW
            "left" -> PillArrangement.CONTROLS_LEFT
            else -> PillArrangement.CONTROLS_RIGHT
        }

    /**
     * The transport buttons to draw, in render order, for a control-set setting.
     *
     * Play/pause is in every result, including the fallback for a value this build does not
     * recognise. That is the invariant worth stating out loud: whatever else is configured,
     * the pill can always start and stop the music. A control set that could omit it would
     * leave a user who is showing controls with buttons that cannot do the one thing the
     * pill exists for.
     */
    fun buttonsFor(controlSet: String): List<PillButton> = when (controlSet) {
        "prev-play-next" -> listOf(PillButton.PREVIOUS, PillButton.PLAY_PAUSE, PillButton.NEXT)
        "play-next" -> listOf(PillButton.PLAY_PAUSE, PillButton.NEXT)
        else -> listOf(PillButton.PLAY_PAUSE)
    }

    /**
     * Whether one vertical drag event counts as a stash flick: strictly more than
     * [STASH_THRESHOLD_PX] of travel, in whichever direction the pill's own screen edge lies.
     *
     * Per-event rather than accumulated, on purpose, and the same reasoning as the panel's
     * close gesture: a slow deliberate drag never exceeds the threshold within any single
     * event and leaves the pill where it is, while a flick clears it in one and stashes.
     * Without that the pill would vanish whenever it was nudged.
     *
     * The direction is honoured rather than taken as absolute travel. A pill anchored at the
     * top stashes upward and a pill anchored at the bottom stashes downward; a drag *away*
     * from the pill's own edge would be dragging it across the screen, which is not a stash.
     *
     * @param dragDeltaPx travel within a single drag event; negative is upward.
     * @param stashDirectionUp whether the pill's anchored edge is the top of the screen.
     */
    fun isStashDrag(dragDeltaPx: Float, stashDirectionUp: Boolean): Boolean =
        if (stashDirectionUp) dragDeltaPx < -STASH_THRESHOLD_PX
        else dragDeltaPx > STASH_THRESHOLD_PX

    /**
     * Whether the pill should look alive: full-strength wave, pause glyph on the button.
     *
     * Buffering counts. A session that is loading the next few seconds of a stream is
     * playing as far as the listener is concerned, and dimming the wave every time the
     * network hesitates would make the pill flicker on a poor connection.
     *
     * A null state — no session at all — is not playing.
     */
    fun isPlaying(playbackState: Int?): Boolean =
        playbackState == PlaybackState.STATE_PLAYING ||
            playbackState == PlaybackState.STATE_BUFFERING
}
