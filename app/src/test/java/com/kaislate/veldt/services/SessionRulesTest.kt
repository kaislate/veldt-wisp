// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.services

import android.media.session.PlaybackState
import com.kaislate.veldt.services.SessionRules.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The session-selection rules, asserted without an Android runtime.
 *
 * Two of the rules below are trivially invertible and would survive a casual reading of the
 * listener, so they are pinned deliberately and named for what they protect:
 *
 *  * **Stickiness must lose to playing** (rule 1 above rule 2) and **win over list order**
 *    (rule 2 above rule 3). Both orderings matter and both are asserted directly.
 *  * **[SessionRules.shouldApplyAsync] must AND the generation check with the nullity check.**
 *    Dropping either half produces a real, shippable bug — a stale cover landing on the current
 *    track, or a failed load blanking a good one.
 */
class SessionRulesTest {

    // Tokens are opaque to the rules, so plain objects stand in for MediaSession.Token.
    private val tokenA = Any()
    private val tokenB = Any()
    private val tokenC = Any()

    private fun playing(token: Any) = SessionSummary(token, PlaybackState.STATE_PLAYING)
    private fun paused(token: Any) = SessionSummary(token, PlaybackState.STATE_PAUSED)
    private fun buffering(token: Any) = SessionSummary(token, PlaybackState.STATE_BUFFERING)
    private fun stateless(token: Any) = SessionSummary(token, null)

    // ---- select: rule 4, the empty case ----

    @Test fun `no sessions at all selects nothing`() {
        assertNull(SessionRules.select(emptyList(), null))
    }

    @Test fun `no sessions selects nothing even when a token was previously active`() {
        // The stale-package trap: a vanished session must not keep its package alive.
        assertNull(SessionRules.select(emptyList(), tokenA))
    }

    // ---- select: rule 1, prefer whatever is actually playing ----

    @Test fun `a single playing session is selected`() {
        val only = playing(tokenA)
        assertSame(only, SessionRules.select(listOf(only), null))
    }

    @Test fun `the second session is selected when it is the one playing`() {
        // Kills a mutant that returns first() unconditionally.
        val first = paused(tokenA)
        val second = playing(tokenB)
        assertSame(second, SessionRules.select(listOf(first, second), null))
    }

    @Test fun `two playing sessions resolve to the first in system order`() {
        val first = playing(tokenA)
        val second = playing(tokenB)
        assertSame(first, SessionRules.select(listOf(first, second), null))
    }

    @Test fun `a playing session beats a paused session that is currently active`() {
        // Rule 1 above rule 2. A mutant that swaps stickiness ahead of playing dies here,
        // and this is the case that makes handover between two live players work.
        val stickyButPaused = paused(tokenA)
        val actuallyPlaying = playing(tokenB)
        assertSame(
            actuallyPlaying,
            SessionRules.select(listOf(stickyButPaused, actuallyPlaying), tokenA),
        )
    }

    @Test fun `buffering does not count as playing`() {
        // "Playing-ish" is the natural mis-generalisation and it changes which app the pill
        // follows during a track change. Only STATE_PLAYING satisfies rule 1, so the sticky
        // session keeps the pill even though the other one is buffering.
        val sticky = paused(tokenA)
        val bufferingOther = buffering(tokenB)
        assertSame(sticky, SessionRules.select(listOf(sticky, bufferingOther), tokenA))
    }

    @Test fun `a buffering session alone is selected only by falling through to list order`() {
        // Same rule from the other side: it is still chosen, but as first-in-list, not as
        // "playing". With a sticky session present it would have lost, as the test above shows.
        val only = buffering(tokenA)
        assertSame(only, SessionRules.select(listOf(only), null))
    }

    // ---- select: rule 2, otherwise stay where you are (the M5 rule) ----

    @Test fun `two paused sessions keep the one that is already active`() {
        // THE most important assertion in this file. Without rule 2, pausing session A while
        // a paused session B also exists hands the pill to B and the panel visibly swaps to a
        // track the user never touched. [ACCEPT-M] M5.
        val first = paused(tokenA)
        val activeButSecond = paused(tokenB)
        assertSame(
            activeButSecond,
            SessionRules.select(listOf(first, activeButSecond), tokenB),
        )
    }

    @Test fun `stickiness holds across three paused sessions`() {
        val first = paused(tokenA)
        val middle = paused(tokenB)
        val last = paused(tokenC)
        assertSame(last, SessionRules.select(listOf(first, middle, last), tokenC))
    }

    @Test fun `a session with no reported state is still eligible to stay active`() {
        // Rule 1 never prefers a null state, but rule 2 does not care about state at all.
        val first = paused(tokenA)
        val activeStateless = stateless(tokenB)
        assertSame(
            activeStateless,
            SessionRules.select(listOf(first, activeStateless), tokenB),
        )
    }

    // ---- select: rule 3, otherwise the first in the list ----

    @Test fun `a lone paused session is selected when nothing is active`() {
        val only = paused(tokenA)
        assertSame(only, SessionRules.select(listOf(only), null))
    }

    @Test fun `an active token that has left the list falls through to the first session`() {
        val first = paused(tokenA)
        val second = paused(tokenB)
        assertSame(first, SessionRules.select(listOf(first, second), tokenC))
    }

    @Test fun `a session with no reported state is eligible as the list fallback`() {
        val first = stateless(tokenA)
        val second = paused(tokenB)
        assertSame(first, SessionRules.select(listOf(first, second), null))
    }

    @Test fun `a null state is never preferred over a playing session`() {
        val first = stateless(tokenA)
        val second = playing(tokenB)
        assertSame(second, SessionRules.select(listOf(first, second), null))
    }

    // ---- isChange ----

    @Test fun `no session before and none now is not a change`() {
        assertFalse(SessionRules.isChange(null, null))
    }

    @Test fun `the same token is not a change`() {
        assertFalse(SessionRules.isChange(tokenA, tokenA))
    }

    @Test fun `a different token is a change`() {
        assertTrue(SessionRules.isChange(tokenA, tokenB))
    }

    @Test fun `gaining a session is a change`() {
        assertTrue(SessionRules.isChange(tokenA, null))
    }

    @Test fun `losing a session is a change`() {
        // The stale-activePackage guard: this must fire so the bus is cleared to null.
        assertTrue(SessionRules.isChange(null, tokenA))
    }

    // ---- shouldStartService ----

    @Test fun `a real candidate starts the foreground service`() {
        assertTrue(SessionRules.shouldStartService(tokenA))
    }

    @Test fun `no candidate does not start the foreground service`() {
        assertFalse(SessionRules.shouldStartService(null))
    }

    // ---- artwork key order ----

    @Test fun `the metadata bitmap keys are tried display icon then album art then art`() {
        assertEquals(
            listOf(
                "android.media.metadata.DISPLAY_ICON",
                "android.media.metadata.ALBUM_ART",
                "android.media.metadata.ART",
            ),
            SessionRules.bitmapKeyOrder(),
        )
    }

    @Test fun `the metadata uri keys are tried display icon then album art then art`() {
        assertEquals(
            listOf(
                "android.media.metadata.DISPLAY_ICON_URI",
                "android.media.metadata.ALBUM_ART_URI",
                "android.media.metadata.ART_URI",
            ),
            SessionRules.uriKeyOrder(),
        )
    }

    @Test fun `the four artwork tiers are named in fallback order`() {
        assertEquals(
            listOf(
                SessionRules.ArtTier.METADATA_BITMAP,
                SessionRules.ArtTier.METADATA_URI,
                SessionRules.ArtTier.NOTIFICATION_ICON,
                SessionRules.ArtTier.NONE,
            ),
            SessionRules.ArtTier.entries.toList(),
        )
    }

    // ---- the allowNull asymmetry ----

    @Test fun `a session change may clear the cover when no art is found`() {
        // The new session may honestly have no cover, and leaving the previous app's art on
        // the pill would show artwork for a track that is not playing.
        assertTrue(SessionRules.clearsArtWhenNoneFound(SessionRules.ArtTrigger.SESSION_CHANGE))
    }

    @Test fun `metadata churn may not clear the cover`() {
        // Players re-post metadata constantly. A resolution that comes up empty must leave a
        // good cover standing, or the artwork blinks on every playback transition.
        assertFalse(SessionRules.clearsArtWhenNoneFound(SessionRules.ArtTrigger.METADATA_CHANGE))
    }

    @Test fun `a notification update may not clear the cover`() {
        assertFalse(
            SessionRules.clearsArtWhenNoneFound(SessionRules.ArtTrigger.NOTIFICATION_UPDATE)
        )
    }

    @Test fun `exactly one trigger is allowed to clear the cover`() {
        // Pins the asymmetry as a whole rather than one branch of it: a mutant that answers
        // true everywhere, false everywhere, or true for the wrong trigger dies here.
        val clearing = SessionRules.ArtTrigger.entries
            .filter(SessionRules::clearsArtWhenNoneFound)
        assertEquals(listOf(SessionRules.ArtTrigger.SESSION_CHANGE), clearing)
    }

    // ---- shouldApplyAsync ----

    @Test fun `a current load with a real bitmap is applied`() {
        assertTrue(
            SessionRules.shouldApplyAsync(
                capturedGeneration = 4,
                currentGeneration = 4,
                loadedIsNull = false,
                allowNullResult = false,
            )
        )
    }

    @Test fun `a superseded load is dropped even when the bitmap is perfectly good`() {
        // [ACCEPT-M] M8: a slow load for the previous track must not overwrite the current
        // cover. A mutant that ignores the generation entirely dies here.
        assertFalse(
            SessionRules.shouldApplyAsync(
                capturedGeneration = 4,
                currentGeneration = 5,
                loadedIsNull = false,
                allowNullResult = false,
            )
        )
    }

    @Test fun `a current load that produced nothing is dropped when art is required`() {
        // The artwork path: a failed load must leave the cover on screen standing.
        // A mutant that ignores loadedIsNull dies here.
        assertFalse(
            SessionRules.shouldApplyAsync(
                capturedGeneration = 4,
                currentGeneration = 4,
                loadedIsNull = true,
                allowNullResult = false,
            )
        )
    }

    @Test fun `a current load that produced nothing is applied when null is allowed`() {
        // The status-bar icon path: a failed decode and no icon must look the same.
        assertTrue(
            SessionRules.shouldApplyAsync(
                capturedGeneration = 4,
                currentGeneration = 4,
                loadedIsNull = true,
                allowNullResult = true,
            )
        )
    }

    @Test fun `a superseded null load is dropped when art is required`() {
        assertFalse(
            SessionRules.shouldApplyAsync(
                capturedGeneration = 4,
                currentGeneration = 5,
                loadedIsNull = true,
                allowNullResult = false,
            )
        )
    }

    @Test fun `a superseded null load is dropped even on the icon path`() {
        // The generation check is ANDed, not ORed: allowing nulls must not also let a stale
        // result through. A mutant that ORs the two halves dies here and nowhere else.
        assertFalse(
            SessionRules.shouldApplyAsync(
                capturedGeneration = 4,
                currentGeneration = 5,
                loadedIsNull = true,
                allowNullResult = true,
            )
        )
    }

    @Test fun `a superseded load is dropped on the icon path with a real bitmap too`() {
        assertFalse(
            SessionRules.shouldApplyAsync(
                capturedGeneration = 4,
                currentGeneration = 5,
                loadedIsNull = false,
                allowNullResult = true,
            )
        )
    }
}
