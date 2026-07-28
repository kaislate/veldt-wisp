// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.ui.island

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pill's decision core: which of the four content arrangements to lay out, which
 * transport buttons to draw, whether one drag event was a flick, and whether the session
 * counts as playing.
 *
 * Every case here is a plain value question, so none of it needs a device. The boundaries
 * are asserted from both sides on purpose — an inclusive-versus-exclusive comparison and a
 * swapped left/right are both invisible to a test that only checks the happy value.
 */
class PillLayoutTest {

    // ---------------- arrangementFor ----------------

    @Test
    fun `controls off ignores the position entirely`() {
        assertEquals(PillArrangement.TEXT_ONLY, PillLayout.arrangementFor(false, "below"))
        assertEquals(PillArrangement.TEXT_ONLY, PillLayout.arrangementFor(false, "left"))
        assertEquals(PillArrangement.TEXT_ONLY, PillLayout.arrangementFor(false, "right"))
    }

    @Test
    fun `controls on below stacks them under the pill`() {
        assertEquals(PillArrangement.CONTROLS_BELOW, PillLayout.arrangementFor(true, "below"))
    }

    @Test
    fun `controls on left puts them before the artwork`() {
        assertEquals(PillArrangement.CONTROLS_LEFT, PillLayout.arrangementFor(true, "left"))
    }

    @Test
    fun `controls on right puts them after the text`() {
        assertEquals(PillArrangement.CONTROLS_RIGHT, PillLayout.arrangementFor(true, "right"))
    }

    @Test
    fun `an unrecognised position falls back to the right`() {
        assertEquals(PillArrangement.CONTROLS_RIGHT, PillLayout.arrangementFor(true, "sideways"))
    }

    @Test
    fun `an empty position falls back to the right`() {
        assertEquals(PillArrangement.CONTROLS_RIGHT, PillLayout.arrangementFor(true, ""))
    }

    // ---------------- buttonsFor ----------------

    @Test
    fun `prev-play-next draws all three in transport order`() {
        assertEquals(
            listOf(PillButton.PREVIOUS, PillButton.PLAY_PAUSE, PillButton.NEXT),
            PillLayout.buttonsFor("prev-play-next"),
        )
    }

    @Test
    fun `play-next draws play then next`() {
        assertEquals(
            listOf(PillButton.PLAY_PAUSE, PillButton.NEXT),
            PillLayout.buttonsFor("play-next"),
        )
    }

    @Test
    fun `play draws play alone`() {
        assertEquals(listOf(PillButton.PLAY_PAUSE), PillLayout.buttonsFor("play"))
    }

    @Test
    fun `an unknown control set draws play alone`() {
        assertEquals(listOf(PillButton.PLAY_PAUSE), PillLayout.buttonsFor("everything"))
    }

    @Test
    fun `every control set can start and stop the music`() {
        for (set in listOf("prev-play-next", "play-next", "play", "nonsense")) {
            assertTrue(
                "control set '$set' must offer play/pause",
                PillLayout.buttonsFor(set).contains(PillButton.PLAY_PAUSE),
            )
        }
    }

    // ---------------- isStashDrag ----------------

    @Test
    fun `an upward flick past the threshold stashes`() {
        assertTrue(PillLayout.isStashDrag(-19f, stashDirectionUp = true))
    }

    @Test
    fun `an upward drag exactly on the threshold does not stash`() {
        assertFalse(PillLayout.isStashDrag(-18f, stashDirectionUp = true))
    }

    @Test
    fun `an upward drag short of the threshold does not stash`() {
        assertFalse(PillLayout.isStashDrag(-17f, stashDirectionUp = true))
        assertFalse(PillLayout.isStashDrag(0f, stashDirectionUp = true))
    }

    @Test
    fun `a downward flick never stashes an upward pill`() {
        assertFalse(PillLayout.isStashDrag(30f, stashDirectionUp = true))
    }

    @Test
    fun `a downward flick past the threshold stashes a bottom pill`() {
        assertTrue(PillLayout.isStashDrag(19f, stashDirectionUp = false))
    }

    @Test
    fun `a downward drag exactly on the threshold does not stash`() {
        assertFalse(PillLayout.isStashDrag(18f, stashDirectionUp = false))
    }

    @Test
    fun `an upward flick never stashes a bottom pill`() {
        assertFalse(PillLayout.isStashDrag(-30f, stashDirectionUp = false))
    }

    @Test
    fun `the stash threshold is eighteen and is not the panel's twenty`() {
        assertEquals(18f, PillLayout.STASH_THRESHOLD_PX, 0f)
        assertNotEquals(20f, PillLayout.STASH_THRESHOLD_PX, 0f)
    }

    // ---------------- isPlaying ----------------

    @Test
    fun `playing counts as playing`() {
        assertTrue(PillLayout.isPlaying(PlaybackState.STATE_PLAYING))
    }

    @Test
    fun `buffering counts as playing`() {
        assertTrue(PillLayout.isPlaying(PlaybackState.STATE_BUFFERING))
    }

    @Test
    fun `every other state does not count as playing`() {
        for (state in listOf(
            PlaybackState.STATE_PAUSED,
            PlaybackState.STATE_STOPPED,
            PlaybackState.STATE_NONE,
            PlaybackState.STATE_ERROR,
            PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
        )) {
            assertFalse("state $state must not count as playing", PillLayout.isPlaying(state))
        }
    }

    @Test
    fun `no session at all does not count as playing`() {
        assertFalse(PillLayout.isPlaying(null))
    }
}
