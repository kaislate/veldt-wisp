// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.overlay

import android.media.session.PlaybackState

/**
 * The island's decision core, isolated from everything that makes it hard to test.
 *
 * Nothing here touches a window, a coroutine, a clock or a log. Each rule is a total
 * function from (what just happened, what we already knew) to an [IslandAction], so the
 * whole behaviour of the pill can be asserted in a plain JVM unit test. The caller owns
 * all the mutable state — the cached playback bucket, the timestamp of the last pause,
 * the current wall clock — and hands it in.
 *
 * Three decisions encoded here are subtle enough to be worth stating outright, because
 * each of them looks like a bug until you know the failure it prevents.
 *
 * **Why [Playback.OTHER] is a bucket of its own.** A media session reports far more than
 * "playing" and "stopped". Between two tracks it may announce that it is skipping; while
 * reconnecting to a cast target it may announce that it is connecting; a scrub reports
 * fast-forwarding. None of those says anything about whether there is music worth showing
 * — they describe a transition, not a resting state. Folding them into [Playback.GONE]
 * would tear the pill off the screen every time the user pressed *next*. So they get
 * their own bucket, and every rule answers "change nothing" for it.
 *
 * **Why a session that vanishes just after a pause is disbelieved.** Several players emit
 * `STATE_NONE` a beat after `STATE_PAUSED`: the session object is being torn down and
 * rebuilt, not abandoned. Taken at face value that reads as "playback ended", the pill
 * hides, and — because the rebuild emits nothing further — it never comes back until the
 * user pokes the transport. [TRANSIENT_GONE_GRACE_MS] after a pause, a disappearing
 * session is therefore treated as noise. The window is measured from the last *pause*
 * specifically, not from the last event of any kind, because that is the only sequence
 * that produces the spurious teardown.
 *
 * **Why a null target is not the same as the current state.** [IslandAction.target] is
 * nullable, and `null` means *do not touch the visible state* — leave whatever is on
 * screen exactly where it is, mid-animation if need be. That is weaker than naming the
 * state the island already happens to be in: naming a state is an instruction the caller
 * must carry out, and re-issuing the current one can still restart an animation, re-add a
 * window, or cancel work that was in flight. Rules that genuinely have no opinion say so
 * with `null` rather than guessing at the current state and asserting it back.
 */

/** Which of the four situations the raw session state boils down to. */
enum class Playback { ACTIVE, PAUSED, GONE, OTHER }

/** What a rule wants done with the pending auto-hide job. */
enum class TimerOp {
    /** Kill any pending auto-hide; do not start a new one. */
    CANCEL,

    /** Kill any pending auto-hide and arm a fresh one from now. */
    RESTART,

    /** Do not touch the auto-hide job, armed or not. */
    LEAVE,
}

/** Collapses the many `PlaybackState.STATE_*` values into the four [Playback] buckets. */
object PlaybackClassifier {

    /**
     * @param raw a `PlaybackState.STATE_*` value, or `null` when there is no session at all.
     */
    fun of(raw: Int?): Playback = when (raw) {
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING -> Playback.ACTIVE
        PlaybackState.STATE_PAUSED -> Playback.PAUSED
        PlaybackState.STATE_STOPPED, PlaybackState.STATE_NONE, null -> Playback.GONE
        else -> Playback.OTHER
    }
}

/**
 * The five independent gates that decide whether the island is allowed on screen at all,
 * irrespective of what the music is doing. Defaults describe an unobstructed device: the
 * feature on, the phone unlocked, nothing in the way.
 *
 * @param enabled the user has the island switched on.
 * @param unlocked the screen is on and the device is not locked.
 * @param targetInForeground the app that owns the media session is the app on screen, so
 *   the pill would be covering the very UI it is summarising.
 * @param stashed the user swiped the pill away and has not asked for it back.
 * @param homeOnlyBlocked "home screen only" mode is on and the foreground app is neither
 *   a launcher nor us.
 */
data class IslandEnv(
    val enabled: Boolean = true,
    val unlocked: Boolean = true,
    val targetInForeground: Boolean = false,
    val stashed: Boolean = false,
    val homeOnlyBlocked: Boolean = false,
) {
    /** True only when every gate is open. Any single closed gate keeps the island off screen. */
    val permits: Boolean
        get() = enabled && unlocked && !targetInForeground && !stashed && !homeOnlyBlocked
}

/**
 * A rule's verdict: two instructions that the caller carries out independently.
 *
 * @param target the visible state to move to, or `null` to leave the visible state alone
 *   (see the note on the file header — this is not the same as naming the current state).
 * @param timer what to do with the pending auto-hide job.
 */
data class IslandAction(val target: IslandState?, val timer: TimerOp)

/** The rules themselves. Pure, stateless, and the single source of truth for when the pill moves. */
object IslandRules {

    /**
     * How long after a pause a vanishing session is dismissed as a session rebuild rather
     * than a genuine stop.
     */
    const val TRANSIENT_GONE_GRACE_MS = 1_000L

    /** "I have no opinion": leave the pill where it is and leave the auto-hide alone. */
    private val Unchanged = IslandAction(target = null, timer = TimerOp.LEAVE)

    /**
     * The environment changed — the user unlocked the phone, swiped the pill away, switched
     * apps, toggled the setting.
     *
     * A closed gate always wins: hide, and disarm the auto-hide so it cannot fire against a
     * pill that is already gone.
     *
     * When the gates open, the island comes back **immediately, on the strength of the
     * playback bucket we last saw** rather than waiting for the session to say something.
     * That is deliberate and load-bearing. A session paused ten minutes ago emits nothing
     * when the screen comes on; without this the pill would stay dead until the user next
     * touched the transport. If the last thing we saw was no session at all, or a transient
     * transport state, there is nothing to restore and the rule declines to act.
     *
     * @param env the environment as it now stands.
     * @param lastPlayback the most recent bucket observed from the session.
     */
    fun onEnvironment(env: IslandEnv, lastPlayback: Playback): IslandAction {
        if (!env.permits) return IslandAction(IslandState.Hidden, TimerOp.CANCEL)
        return when (lastPlayback) {
            Playback.ACTIVE -> IslandAction(IslandState.Pill, TimerOp.CANCEL)
            Playback.PAUSED -> IslandAction(IslandState.Pill, TimerOp.RESTART)
            Playback.GONE, Playback.OTHER -> Unchanged
        }
    }

    /**
     * The session reported a new state.
     *
     * Playing shows the pill and disarms the auto-hide, since there is no reason to retire a
     * pill whose music is still running. Pausing shows the pill but arms the auto-hide, so a
     * pill left behind by a forgotten session eventually retires itself. Either way, a closed
     * environment gate overrules the music and hides.
     *
     * A vanishing session is the one case where the environment does *not* get the last word:
     * inside the grace window after a pause it is disbelieved outright, gates open or shut,
     * and the auto-hide armed by that pause is deliberately left running — so if the session
     * really has gone for good, that timer is what eventually clears the pill.
     *
     * @param playback the bucket just observed.
     * @param env the environment as it now stands.
     * @param nowMs current wall clock, same time base as [lastPauseAtMs].
     * @param lastPauseAtMs when the last [Playback.PAUSED] was observed.
     * @param graceMs the grace window; injectable so tests need not manipulate a real clock.
     */
    fun onPlayback(
        playback: Playback,
        env: IslandEnv,
        nowMs: Long,
        lastPauseAtMs: Long,
        graceMs: Long = TRANSIENT_GONE_GRACE_MS,
    ): IslandAction = when (playback) {
        Playback.ACTIVE ->
            if (env.permits) IslandAction(IslandState.Pill, TimerOp.CANCEL)
            else IslandAction(IslandState.Hidden, TimerOp.CANCEL)

        Playback.PAUSED ->
            if (env.permits) IslandAction(IslandState.Pill, TimerOp.RESTART)
            else IslandAction(IslandState.Hidden, TimerOp.CANCEL)

        Playback.GONE ->
            if (nowMs - lastPauseAtMs <= graceMs) Unchanged
            else IslandAction(IslandState.Hidden, TimerOp.CANCEL)

        Playback.OTHER -> Unchanged
    }
}
