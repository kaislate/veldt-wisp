// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.overlay

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The specification of the island's decision logic, asserted directly.
 * Every case here comes from the tables in the plan, not from any prior implementation.
 */
class IslandRulesTest {

    private val permitted = IslandEnv()          // all five gates open by default
    private val blocked = IslandEnv(enabled = false)

    // ---- classification ----

    @Test fun `playing and buffering are active`() {
        assertEquals(Playback.ACTIVE, PlaybackClassifier.of(PlaybackState.STATE_PLAYING))
        assertEquals(Playback.ACTIVE, PlaybackClassifier.of(PlaybackState.STATE_BUFFERING))
    }

    @Test fun `paused is its own bucket`() {
        assertEquals(Playback.PAUSED, PlaybackClassifier.of(PlaybackState.STATE_PAUSED))
    }

    @Test fun `stopped none and null are gone`() {
        assertEquals(Playback.GONE, PlaybackClassifier.of(PlaybackState.STATE_STOPPED))
        assertEquals(Playback.GONE, PlaybackClassifier.of(PlaybackState.STATE_NONE))
        assertEquals(Playback.GONE, PlaybackClassifier.of(null))
    }

    @Test fun `transient transport states are neither active nor gone`() {
        assertEquals(Playback.OTHER, PlaybackClassifier.of(PlaybackState.STATE_SKIPPING_TO_NEXT))
        assertEquals(Playback.OTHER, PlaybackClassifier.of(PlaybackState.STATE_SKIPPING_TO_PREVIOUS))
        assertEquals(Playback.OTHER, PlaybackClassifier.of(PlaybackState.STATE_FAST_FORWARDING))
        assertEquals(Playback.OTHER, PlaybackClassifier.of(PlaybackState.STATE_REWINDING))
        assertEquals(Playback.OTHER, PlaybackClassifier.of(PlaybackState.STATE_ERROR))
        assertEquals(Playback.OTHER, PlaybackClassifier.of(PlaybackState.STATE_CONNECTING))
    }

    // ---- the permit conjunction ----

    @Test fun `default environment permits the island`() {
        assertTrue(IslandEnv().permits)
    }

    @Test fun `each gate alone blocks the island`() {
        assertFalse(IslandEnv(enabled = false).permits)
        assertFalse(IslandEnv(unlocked = false).permits)
        assertFalse(IslandEnv(targetInForeground = true).permits)
        assertFalse(IslandEnv(stashed = true).permits)
        assertFalse(IslandEnv(homeOnlyBlocked = true).permits)
    }

    // ---- rule 1: environment changed ----

    @Test fun `blocked environment hides and cancels regardless of playback`() {
        for (p in Playback.values()) {
            val a = IslandRules.onEnvironment(blocked, p)
            assertEquals("playback=$p", IslandState.Hidden, a.target)
            assertEquals("playback=$p", TimerOp.CANCEL, a.timer)
        }
    }

    @Test fun `permitted environment with active playback shows the pill and cancels the timer`() {
        val a = IslandRules.onEnvironment(permitted, Playback.ACTIVE)
        assertEquals(IslandState.Pill, a.target)
        assertEquals(TimerOp.CANCEL, a.timer)
    }

    @Test fun `permitted environment with paused playback shows the pill and restarts the timer`() {
        val a = IslandRules.onEnvironment(permitted, Playback.PAUSED)
        assertEquals(IslandState.Pill, a.target)
        assertEquals(TimerOp.RESTART, a.timer)
    }

    @Test fun `permitted environment with no session changes nothing`() {
        val a = IslandRules.onEnvironment(permitted, Playback.GONE)
        assertNull(a.target)
        assertEquals(TimerOp.LEAVE, a.timer)
    }

    @Test fun `permitted environment mid-skip changes nothing`() {
        val a = IslandRules.onEnvironment(permitted, Playback.OTHER)
        assertNull(a.target)
        assertEquals(TimerOp.LEAVE, a.timer)
    }

    // ---- rule 2: playback changed ----

    private fun onPlayback(
        p: Playback,
        env: IslandEnv = permitted,
        nowMs: Long = 1_000_000L,
        lastPauseAtMs: Long = 0L,
    ) = IslandRules.onPlayback(p, env, nowMs, lastPauseAtMs, IslandRules.TRANSIENT_GONE_GRACE_MS)

    @Test fun `active playback shows the pill when permitted`() {
        val a = onPlayback(Playback.ACTIVE)
        assertEquals(IslandState.Pill, a.target)
        assertEquals(TimerOp.CANCEL, a.timer)
    }

    @Test fun `active playback still hides when the environment blocks`() {
        val a = onPlayback(Playback.ACTIVE, env = blocked)
        assertEquals(IslandState.Hidden, a.target)
        assertEquals(TimerOp.CANCEL, a.timer)
    }

    @Test fun `pause shows the pill and arms the auto-hide`() {
        val a = onPlayback(Playback.PAUSED)
        assertEquals(IslandState.Pill, a.target)
        assertEquals(TimerOp.RESTART, a.timer)
    }

    @Test fun `pause while blocked hides and disarms`() {
        val a = onPlayback(Playback.PAUSED, env = blocked)
        assertEquals(IslandState.Hidden, a.target)
        assertEquals(TimerOp.CANCEL, a.timer)
    }

    @Test fun `session loss right after a pause is ignored`() {
        val a = onPlayback(Playback.GONE, nowMs = 10_500L, lastPauseAtMs = 10_000L)
        assertNull("must not hide during the grace window", a.target)
        assertEquals("must not touch the armed timer", TimerOp.LEAVE, a.timer)
    }

    @Test fun `the grace window is inclusive at exactly one second`() {
        val a = onPlayback(Playback.GONE, nowMs = 11_000L, lastPauseAtMs = 10_000L)
        assertNull(a.target)
    }

    @Test fun `session loss one millisecond past the grace window hides`() {
        val a = onPlayback(Playback.GONE, nowMs = 11_001L, lastPauseAtMs = 10_000L)
        assertEquals(IslandState.Hidden, a.target)
        assertEquals(TimerOp.CANCEL, a.timer)
    }

    @Test fun `the grace window applies even when the environment blocks`() {
        val a = onPlayback(Playback.GONE, env = blocked, nowMs = 10_500L, lastPauseAtMs = 10_000L)
        assertNull(a.target)
        assertEquals(TimerOp.LEAVE, a.timer)
    }

    @Test fun `session loss with no prior pause hides immediately`() {
        val a = onPlayback(Playback.GONE, nowMs = 1_000_000L, lastPauseAtMs = 0L)
        assertEquals(IslandState.Hidden, a.target)
    }

    @Test fun `transient transport states never move the island`() {
        assertNull(onPlayback(Playback.OTHER).target)
        assertEquals(TimerOp.LEAVE, onPlayback(Playback.OTHER).timer)
        assertNull(onPlayback(Playback.OTHER, env = blocked).target)
    }

    @Test fun `the grace window is one second`() {
        assertEquals(1_000L, IslandRules.TRANSIENT_GONE_GRACE_MS)
    }
}
