// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.data.media

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The specification of the media bus's decision logic, asserted directly.
 * Every case here comes from the behavioural tables in the plan, not from any
 * prior implementation.
 */
class SessionBusRulesTest {

    // ---- toggleTarget: true means pause, false means play ----

    @Test fun `playing toggles to pause`() {
        assertTrue(SessionBusRules.toggleTarget(PlaybackState.STATE_PLAYING))
    }

    @Test fun `buffering toggles to pause, because the user already pressed play`() {
        assertTrue(SessionBusRules.toggleTarget(PlaybackState.STATE_BUFFERING))
    }

    @Test fun `paused toggles to play`() {
        assertFalse(SessionBusRules.toggleTarget(PlaybackState.STATE_PAUSED))
    }

    @Test fun `stopped and none toggle to play`() {
        assertFalse(SessionBusRules.toggleTarget(PlaybackState.STATE_STOPPED))
        assertFalse(SessionBusRules.toggleTarget(PlaybackState.STATE_NONE))
    }

    @Test fun `error and connecting toggle to play`() {
        assertFalse(SessionBusRules.toggleTarget(PlaybackState.STATE_ERROR))
        assertFalse(SessionBusRules.toggleTarget(PlaybackState.STATE_CONNECTING))
    }

    @Test fun `every skipping state toggles to play`() {
        assertFalse(SessionBusRules.toggleTarget(PlaybackState.STATE_SKIPPING_TO_NEXT))
        assertFalse(SessionBusRules.toggleTarget(PlaybackState.STATE_SKIPPING_TO_PREVIOUS))
        assertFalse(SessionBusRules.toggleTarget(PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM))
    }

    @Test fun `the scrubbing states toggle to play`() {
        assertFalse(SessionBusRules.toggleTarget(PlaybackState.STATE_FAST_FORWARDING))
        assertFalse(SessionBusRules.toggleTarget(PlaybackState.STATE_REWINDING))
    }

    @Test fun `an unknown state toggles to play rather than doing nothing`() {
        assertFalse(SessionBusRules.toggleTarget(null))
    }

    // ---- shouldRebuildPlayback ----

    @Test fun `with no current object there is nothing to rebuild`() {
        assertFalse(
            SessionBusRules.shouldRebuildPlayback(
                hasCurrent = false,
                currentState = null,
                incomingState = PlaybackState.STATE_PLAYING,
            ),
        )
        assertFalse(
            SessionBusRules.shouldRebuildPlayback(
                hasCurrent = false,
                currentState = PlaybackState.STATE_PLAYING,
                incomingState = PlaybackState.STATE_PAUSED,
            ),
        )
        assertFalse(
            SessionBusRules.shouldRebuildPlayback(
                hasCurrent = false,
                currentState = null,
                incomingState = null,
            ),
        )
    }

    @Test fun `a current object already carrying the incoming state is left alone`() {
        assertFalse(
            SessionBusRules.shouldRebuildPlayback(
                hasCurrent = true,
                currentState = PlaybackState.STATE_PLAYING,
                incomingState = PlaybackState.STATE_PLAYING,
            ),
        )
        assertFalse(
            SessionBusRules.shouldRebuildPlayback(
                hasCurrent = true,
                currentState = PlaybackState.STATE_PAUSED,
                incomingState = PlaybackState.STATE_PAUSED,
            ),
        )
    }

    @Test fun `a current object carrying a different state is rebuilt`() {
        assertTrue(
            SessionBusRules.shouldRebuildPlayback(
                hasCurrent = true,
                currentState = PlaybackState.STATE_PLAYING,
                incomingState = PlaybackState.STATE_PAUSED,
            ),
        )
        assertTrue(
            SessionBusRules.shouldRebuildPlayback(
                hasCurrent = true,
                currentState = PlaybackState.STATE_PAUSED,
                incomingState = PlaybackState.STATE_BUFFERING,
            ),
        )
    }

    @Test fun `a null incoming state against a real one counts as a difference`() {
        assertTrue(
            SessionBusRules.shouldRebuildPlayback(
                hasCurrent = true,
                currentState = PlaybackState.STATE_PLAYING,
                incomingState = null,
            ),
        )
    }

    // ---- rebuiltState ----

    @Test fun `a null incoming state becomes paused, not stopped or none`() {
        assertEquals(PlaybackState.STATE_PAUSED, SessionBusRules.rebuiltState(null))
    }

    @Test fun `any real incoming state is carried through unchanged`() {
        assertEquals(
            PlaybackState.STATE_PLAYING,
            SessionBusRules.rebuiltState(PlaybackState.STATE_PLAYING),
        )
        assertEquals(
            PlaybackState.STATE_BUFFERING,
            SessionBusRules.rebuiltState(PlaybackState.STATE_BUFFERING),
        )
        assertEquals(
            PlaybackState.STATE_STOPPED,
            SessionBusRules.rebuiltState(PlaybackState.STATE_STOPPED),
        )
        assertEquals(
            PlaybackState.STATE_NONE,
            SessionBusRules.rebuiltState(PlaybackState.STATE_NONE),
        )
        assertEquals(
            PlaybackState.STATE_ERROR,
            SessionBusRules.rebuiltState(PlaybackState.STATE_ERROR),
        )
    }

    // ---- shouldPublishArt ----

    @Test fun `a null cover is published only when nulls are allowed`() {
        assertTrue(
            SessionBusRules.shouldPublishArt(
                incomingIsNull = true,
                currentIsNull = false,
                samePicture = false,
                allowNull = true,
            ),
        )
    }

    @Test fun `a null cover without permission leaves the cover on screen`() {
        assertFalse(
            SessionBusRules.shouldPublishArt(
                incomingIsNull = true,
                currentIsNull = false,
                samePicture = false,
                allowNull = false,
            ),
        )
    }

    @Test fun `an allowed null publishes even when there is already no cover`() {
        assertTrue(
            SessionBusRules.shouldPublishArt(
                incomingIsNull = true,
                currentIsNull = true,
                samePicture = false,
                allowNull = true,
            ),
        )
        assertFalse(
            SessionBusRules.shouldPublishArt(
                incomingIsNull = true,
                currentIsNull = true,
                samePicture = false,
                allowNull = false,
            ),
        )
    }

    @Test fun `the first cover of a session is always published`() {
        assertTrue(
            SessionBusRules.shouldPublishArt(
                incomingIsNull = false,
                currentIsNull = true,
                samePicture = false,
                allowNull = false,
            ),
        )
        // With nothing on screen there is nothing to have matched, so the comparison's
        // answer is irrelevant and must not be allowed to suppress the first cover.
        assertTrue(
            SessionBusRules.shouldPublishArt(
                incomingIsNull = false,
                currentIsNull = true,
                samePicture = true,
                allowNull = false,
            ),
        )
    }

    @Test fun `a re-parcelled copy of the same picture is not republished`() {
        assertFalse(
            SessionBusRules.shouldPublishArt(
                incomingIsNull = false,
                currentIsNull = false,
                samePicture = true,
                allowNull = false,
            ),
        )
    }

    @Test fun `a genuinely different picture is published`() {
        assertTrue(
            SessionBusRules.shouldPublishArt(
                incomingIsNull = false,
                currentIsNull = false,
                samePicture = false,
                allowNull = false,
            ),
        )
    }

    @Test fun `allowNull governs nulls only and never leaks into the non-null path`() {
        assertFalse(
            SessionBusRules.shouldPublishArt(
                incomingIsNull = false,
                currentIsNull = false,
                samePicture = true,
                allowNull = true,
            ),
        )
        assertTrue(
            SessionBusRules.shouldPublishArt(
                incomingIsNull = false,
                currentIsNull = false,
                samePicture = false,
                allowNull = true,
            ),
        )
    }

    // ---- shouldPublishMetadata ----

    @Test fun `null metadata is a defined no-op`() {
        assertFalse(SessionBusRules.shouldPublishMetadata(incomingIsNull = true))
    }

    @Test fun `real metadata is published`() {
        assertTrue(SessionBusRules.shouldPublishMetadata(incomingIsNull = false))
    }
}
