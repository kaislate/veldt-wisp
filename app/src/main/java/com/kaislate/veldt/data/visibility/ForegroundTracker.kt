package com.kaislate.veldt.data.visibility

/**
 * Pure, Android-free logic for deciding which app is in the foreground from a
 * stream of RESUMED/PAUSED usage events.
 *
 * Why not simply "the most recent RESUMED wins": on some OEMs (notably Samsung
 * One UI) a launcher fires ACTIVITY_RESUMED *in the background* while you are still
 * inside another app. A naive last-resume-wins reading then concludes you left the
 * app, so Veldt's pill wrongly reappears over the player and stays until the player
 * happens to resume again. (Confirmed on-device: the player stayed RESUMED with no
 * PAUSED for 36s while launchers fired background resumes.)
 *
 * Rule: a different app's RESUMED becomes the foreground only if the current
 * foreground app has paused — either already, or within [switchToleranceMs] of that
 * resume (event ordering around a real switch isn't strict; the incoming app can
 * resume a beat before the outgoing one pauses). A resume that arrives while the
 * current app is still resumed, and the current app never pauses near it, is a
 * spurious background resume and is ignored. Two apps cannot truly be foreground at
 * once.
 */

/** A usage event. [resumed] = true for ACTIVITY_RESUMED, false for ACTIVITY_PAUSED. */
data class FgEvent(val pkg: String, val resumed: Boolean, val timeMs: Long)

data class FgState(
    val foreground: String? = null,
    /** When [foreground] last paused; null means it is currently resumed. */
    val pausedAtMs: Long? = null,
    /** A candidate foreground awaiting confirmation (see rule above); null if none. */
    val pendingPkg: String? = null,
    val pendingAtMs: Long = 0L,
)

const val DEFAULT_SWITCH_TOLERANCE_MS = 2_500L

/**
 * Fold [events] (any order — sorted internally by time) into [start], then drop a
 * pending candidate that has gone stale as of [nowMs]. The returned
 * [FgState.foreground] is the resolved current foreground package.
 */
fun resolveForeground(
    start: FgState,
    events: List<FgEvent>,
    nowMs: Long,
    switchToleranceMs: Long = DEFAULT_SWITCH_TOLERANCE_MS,
): FgState {
    var s = start
    for (ev in events.sortedBy { it.timeMs }) {
        s = if (ev.resumed) onResume(s, ev.pkg, ev.timeMs) else onPause(s, ev.pkg, ev.timeMs, switchToleranceMs)
    }
    // A pending candidate the current app never paused near is a spurious background
    // resume — discard it.
    if (s.pendingPkg != null && nowMs - s.pendingAtMs > switchToleranceMs) {
        s = s.copy(pendingPkg = null)
    }
    return s
}

private fun onResume(s: FgState, pkg: String, t: Long): FgState = when {
    pkg == s.foreground -> s.copy(pausedAtMs = null, pendingPkg = null)          // re-resumed; still here
    s.foreground == null -> s.copy(foreground = pkg, pausedAtMs = null, pendingPkg = null)
    s.pausedAtMs != null -> s.copy(foreground = pkg, pausedAtMs = null, pendingPkg = null) // current already backgrounded
    else -> s.copy(pendingPkg = pkg, pendingAtMs = t)                            // current still resumed → maybe spurious
}

private fun onPause(s: FgState, pkg: String, t: Long, tol: Long): FgState = when {
    pkg == s.foreground ->
        if (s.pendingPkg != null && kotlin.math.abs(t - s.pendingAtMs) <= tol)
            s.copy(foreground = s.pendingPkg, pausedAtMs = null, pendingPkg = null) // pending switch was real
        else
            s.copy(pausedAtMs = t)                                               // now backgrounded; keep as fg until a resume
    pkg == s.pendingPkg -> s.copy(pendingPkg = null)                             // the candidate itself paused; forget it
    else -> s
}
