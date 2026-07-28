package com.kaislate.veldt.data.visibility

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.core.content.getSystemService

class UsageStatsRepository(private val context: Context) {

    private val usm: UsageStatsManager? = context.getSystemService()

    // Foreground is tracked incrementally across polls (see ForegroundTracker):
    // each call consumes the usage events since the previous call and folds them
    // into fgState. Persisting the state — rather than re-deriving from a fixed
    // window every call — is what makes long idles and spurious background resumes
    // resolve correctly. This is a Hilt @Singleton, so the state survives the
    // foreground service's frequent restarts (only a process death resets it).
    private var fgState = FgState()
    private var eventCursorMs: Long = 0L

    fun isAppInForeground(packageName: String): Boolean = latestForegroundPackage() == packageName

    /**
     * Current foreground package, resolved from the ACTIVITY_RESUMED/PAUSED event
     * stream. Immune to the spurious background launcher resumes that used to make
     * the pill reappear over the playing app (see [resolveForeground]).
     */
    @Synchronized
    fun latestForegroundPackage(): String? {
        val mgr = usm ?: return fgState.foreground
        val now = System.currentTimeMillis()
        // First call seeds from a lookback window to establish the current foreground;
        // afterwards we consume only events since the last call.
        val from = if (eventCursorMs == 0L) now - SEED_WINDOW_MS else eventCursorMs
        val query = mgr.queryEvents(from, now) ?: return fgState.foreground

        val batch = ArrayList<FgEvent>()
        val e = UsageEvents.Event()
        while (query.hasNextEvent()) {
            query.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> batch.add(FgEvent(e.packageName, true, e.timeStamp))
                UsageEvents.Event.ACTIVITY_PAUSED -> batch.add(FgEvent(e.packageName, false, e.timeStamp))
            }
        }
        fgState = resolveForeground(fgState, batch, now)
        eventCursorMs = now
        return fgState.foreground
    }

    /** The set of installed launcher (home screen) packages, cached after first query. */
    fun launcherPackages(): Set<String> = cachedLaunchers ?: run {
        val i = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_HOME)
        context.packageManager.queryIntentActivities(i, 0)
            .mapNotNull { it.activityInfo?.packageName }.toSet()
            .also { cachedLaunchers = it }
    }
    private var cachedLaunchers: Set<String>? = null

    private companion object {
        // Lookback used only on the first call, to establish an initial foreground.
        const val SEED_WINDOW_MS = 60_000L
    }
}
