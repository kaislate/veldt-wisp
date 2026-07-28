package com.kaislate.veldt.overlay

import android.media.session.PlaybackState
import android.util.Log
import com.kaislate.veldt.domain.overlay.HideIslandUseCase
import com.kaislate.veldt.domain.overlay.ShowIslandUseCase
import com.kaislate.veldt.util.Constants
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the island is showing right now.
 *
 * [Expanded] is part of the contract even though nothing in this class produces it: the
 * expanded panel is a window-level concern owned by the overlay window manager, and the
 * foreground service still switches exhaustively over all three variants.
 */
sealed interface IslandState {
    data object Hidden : IslandState
    data object Pill : IslandState
    data object Expanded : IslandState
}

/**
 * The stateful shell around [IslandRules].
 *
 * The rules decide; this class remembers and acts. It owns the four pieces of mutable
 * state the rules need handed to them — the environment gates, the last playback bucket,
 * the wall clock of the last pause, and the pending auto-hide job — feeds them into a
 * pure rule on every event, and carries out the resulting [IslandAction].
 *
 * Two properties of the plumbing are load-bearing rather than incidental:
 *
 * **Everything runs on the main thread, immediately.** Showing and hiding end in
 * `WindowManager.addView` / `removeViewImmediate`, which are main-thread-only. The
 * dispatcher is [Dispatchers.Main.immediate] rather than plain `Main` so that a caller
 * already on the main thread sees the window change land *before* its call returns; the
 * foreground service depends on that ordering when it decides to stop itself.
 *
 * **Transitions are idempotent.** The service re-reports its environment several times a
 * second. Without the guard in [moveTo], each identical report would re-enter the show
 * path and re-emit on [state], and the service would re-post its notification in a loop.
 */
@ServiceScoped
class IslandStateMachine internal constructor(
    private val showIsland: ShowIslandUseCase,
    private val hideIsland: HideIslandUseCase,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
) {

    /**
     * The constructor Hilt sees. Supplies the real main-thread scope and the system clock;
     * the primary constructor exists so tests can drive both.
     */
    @Inject
    constructor(
        showIsland: ShowIslandUseCase,
        hideIsland: HideIslandUseCase,
    ) : this(
        showIsland,
        hideIsland,
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        System::currentTimeMillis,
    )

    private val _state = MutableStateFlow<IslandState>(IslandState.Hidden)

    /** What the island is showing. Cold callers may read [StateFlow.value] freely. */
    val state: StateFlow<IslandState> = _state.asStateFlow()

    /** The five gates, as last reported. Defaults describe an unobstructed device. */
    private var env = IslandEnv()

    /** The last bucket seen from the session; [Playback.GONE] means "never told anything". */
    private var lastPlayback: Playback = Playback.GONE

    /** The last raw `PlaybackState.STATE_*`, kept solely for the auto-hide re-check. */
    private var lastRawPlayback: Int? = null

    /** When the last [Playback.PAUSED] arrived, in [nowMs]'s time base. */
    private var lastPauseAtMs: Long = 0L

    /** The pending auto-hide, if one is armed. At most one exists at a time. */
    private var autoHideJob: Job? = null

    /** How long a paused pill survives. Written from the settings screen's thread. */
    @Volatile
    private var autoHideMs: Long = Constants.INACTIVITY_TIMEOUT_MS

    /**
     * Sets how long a paused island waits before retiring itself.
     *
     * Takes effect on the *next* timer only — an auto-hide already counting down keeps the
     * duration it was armed with, so dragging the settings slider cannot yank a pill off
     * the screen mid-countdown.
     */
    fun setPausedTimeout(ms: Long) {
        autoHideMs = ms
    }

    /**
     * Reports a change to one or more environment gates. `null` means "leave that gate as
     * it was", so callers can report just the thing they observed.
     *
     * A report that changes nothing is dropped entirely — no log, no rules, no effects.
     * The foreground service polls, and the overwhelming majority of its reports are
     * repeats of the last one.
     */
    fun updateEnvironment(
        enabled: Boolean? = null,
        unlocked: Boolean? = null,
        targetInForeground: Boolean? = null,
        stashed: Boolean? = null,
        homeOnlyBlocked: Boolean? = null,
    ) {
        val merged = env.copy(
            enabled = enabled ?: env.enabled,
            unlocked = unlocked ?: env.unlocked,
            targetInForeground = targetInForeground ?: env.targetInForeground,
            stashed = stashed ?: env.stashed,
            homeOnlyBlocked = homeOnlyBlocked ?: env.homeOnlyBlocked,
        )
        if (merged == env) return

        env = merged
        Log.d(
            TAG,
            "gates changed: enabled=${merged.enabled} unlocked=${merged.unlocked} " +
                "playerOnScreen=${merged.targetInForeground} stashed=${merged.stashed} " +
                "homeOnlyBlocked=${merged.homeOnlyBlocked} -> permitted=${merged.permits}",
        )
        apply(IslandRules.onEnvironment(merged, lastPlayback))
    }

    /**
     * Reports what the active media session is doing now, as a `PlaybackState.STATE_*`
     * value, or `null` when there is no session at all.
     *
     * The raw value and its bucket are cached before the rules are consulted and
     * regardless of what the rules decide — including when a vanishing session is
     * disbelieved. The auto-hide's re-check reads that cached raw value, and the caching
     * is what makes a graced session drop leave an armed timer permanently inert.
     */
    fun onPlaybackChanged(state: Int?) {
        if (state != lastRawPlayback) {
            Log.d(TAG, "playback state $lastRawPlayback -> $state")
        }
        lastRawPlayback = state
        val playback = PlaybackClassifier.of(state)
        lastPlayback = playback

        // Stamped before the rules run, so a later GONE measures its grace window from the
        // instant of the pause rather than from whenever we got round to reacting.
        if (playback == Playback.PAUSED) lastPauseAtMs = nowMs()

        apply(IslandRules.onPlayback(playback, env, nowMs(), lastPauseAtMs))
    }

    /**
     * Retires this instance: cancels any armed auto-hide and cancels the scope, after
     * which nothing it launches can ever run.
     *
     * This is not tidiness. The class is `@ServiceScoped` and the foreground service stops
     * itself whenever the island is hidden with no playback, so every restart builds a
     * *fresh* machine while the overlay repository behind [hideIsland] stays a singleton.
     * A dead instance that kept an armed auto-hide would fire it a few seconds into the
     * new instance's life and hide the **new** pill — which presents as "the pill randomly
     * vanishes about half a minute after resuming" and is near-impossible to trace back.
     */
    fun shutdown() {
        cancelAutoHide()
        scope.cancel()
    }

    /** Carries out a rule's verdict: the timer first, then the visible state. */
    private fun apply(action: IslandAction) {
        when (action.timer) {
            TimerOp.CANCEL -> cancelAutoHide()
            TimerOp.RESTART -> restartAutoHide()
            TimerOp.LEAVE -> Unit
        }
        action.target?.let(::moveTo)
    }

    /**
     * Moves the island to [target], or does nothing if it is already there.
     *
     * The effect runs before [state] is updated, so anything reacting to `Pill` finds a
     * window that already exists.
     */
    private fun moveTo(target: IslandState) {
        val current = _state.value
        when (target) {
            IslandState.Pill -> {
                if (current == IslandState.Pill) return
                Log.d(TAG, "$current -> Pill")
                showIsland()
            }

            IslandState.Hidden -> {
                if (current == IslandState.Hidden) return
                Log.d(TAG, "$current -> Hidden")
                hideIsland()
            }

            // No rule names Expanded; the panel is driven by the window manager, not here.
            IslandState.Expanded -> return
        }
        _state.value = target
    }

    /** Drops any armed auto-hide. Safe on a timer that has already fired. */
    private fun cancelAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = null
    }

    /**
     * Arms a fresh auto-hide, replacing any pending one.
     *
     * The duration is read once, here, so the countdown is immune to later changes. When
     * it elapses the job re-checks the *raw* playback value rather than the bucket or the
     * environment: only a session still sitting at `STATE_PAUSED` gets retired. A session
     * that has since reported anything else — including the `STATE_NONE` teardown that the
     * grace window deliberately disbelieved — leaves the pill exactly where it is.
     */
    private fun restartAutoHide() {
        cancelAutoHide()
        val waitMs = autoHideMs
        autoHideJob = scope.launch {
            delay(waitMs)
            if (lastRawPlayback == PlaybackState.STATE_PAUSED) {
                Log.d(TAG, "auto-hide fired after ${waitMs}ms of pause")
                moveTo(IslandState.Hidden)
            }
        }
    }

    private companion object {
        const val TAG = "IslandStateMachine"
    }
}
