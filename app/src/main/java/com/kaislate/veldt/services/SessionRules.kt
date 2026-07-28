package com.kaislate.veldt.services

import android.media.MediaMetadata
import android.media.session.PlaybackState

/**
 * The decisions [MediaNotificationListener] makes, lifted out of it so they can be asserted
 * without a `MediaController`, a `Bitmap` or an Android runtime.
 *
 * Nothing here touches a session. Sessions arrive as [SessionSummary] — a token the rules only
 * ever compare for identity, and the raw state integer — so the whole of selection is a total
 * function of data and can be exhaustively tested on the JVM. `MediaController` is deliberately
 * not imported; if it ever needs to be, the rule has drifted back into the listener.
 *
 * The `PlaybackState.STATE_*` and `MediaMetadata.METADATA_KEY_*` values are compile-time
 * constants, so naming them costs nothing at runtime and keeps this file readable next to the
 * platform's own vocabulary.
 *
 * ## Why the selection rule is shaped the way it is
 *
 * Selection feeds `MediaSessionBus.activePackage`, which `PollRules.targetInForeground` compares
 * against the package on screen to decide whether to get the pill out of the user's way. Picking
 * the wrong session therefore does not look like a selection bug — it looks like the pill
 * reappearing on top of the app that is playing, and it sends the reader to the wrong file.
 * Both halves of the rule below are load-bearing for that, and both are pinned by tests.
 */
object SessionRules {

    /**
     * One session, reduced to the only two things selection cares about.
     *
     * @param token identity only. The listener passes the real `MediaSession.Token`; tests pass
     *   anything. It is non-null by construction, which is what lets [select] compare it against
     *   a nullable active token without a null ever matching a live session.
     * @param state the raw `PlaybackState.STATE_*` integer, or null when the session has not
     *   said. A null state is never *preferred*, but it is never disqualifying either.
     */
    data class SessionSummary(val token: Any, val state: Int?)

    /**
     * Which session should the pill follow?
     *
     * Four ordered rules, first match wins:
     *
     *  1. The first session that is actually `STATE_PLAYING`, in the order the system supplied.
     *  2. Otherwise the session that is already active, if it is still in the list — *stay put*.
     *  3. Otherwise the first session in the list.
     *  4. Otherwise nothing.
     *
     * **Rule 2 is a deliberate UX fix, not an accident.** Without it, pausing session A while a
     * paused session B also exists hands the pill to B, and the panel visibly swaps to a track
     * the user never touched. "Prefer whatever is actually playing; otherwise stay where you
     * are" is the whole rule.
     *
     * **Rule 1 must stay above rule 2.** They are trivially swappable and the swap is invisible
     * until two players are live at once: with stickiness first, starting playback in app B
     * while app A is loaded leaves the pill stranded on A.
     *
     * **Only `STATE_PLAYING` satisfies rule 1.** `STATE_BUFFERING` deliberately does not.
     * "Playing-ish" is the natural mis-generalisation, and it would let a session that is merely
     * spinning up during a track change steal the pill from the one the user is listening to.
     *
     * Rule 4 is not a special case in the code — an empty list simply exhausts all three
     * `firstOrNull`s. It matters all the same: the caller must publish a **null** active package
     * rather than the last-known one, or `targetInForeground` can never match again and the pill
     * shows in places it should not.
     */
    fun select(sessions: List<SessionSummary>, activeToken: Any?): SessionSummary? =
        sessions.firstOrNull { it.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.token == activeToken }
            ?: sessions.firstOrNull()

    /**
     * Is the freshly selected session actually a different one?
     *
     * The gate in front of every republish. Selection re-runs on every playback tick of every
     * watched session, so without this the bus would be rewritten several times a second and the
     * panel would rebuild constantly.
     *
     * Null on either side is meaningful: gaining a first session and losing the last one are
     * both changes, and only null-against-null is not.
     */
    fun isChange(candidateToken: Any?, activeToken: Any?): Boolean = candidateToken != activeToken

    /**
     * Should the foreground service be started for this candidate?
     *
     * Only for a real one. With no session there is nothing to watch, and starting the service
     * to immediately discover that would post and retract a foreground notification for nothing.
     */
    fun shouldStartService(candidateToken: Any?): Boolean = candidateToken != null

    /**
     * The four places artwork is looked for, in fallback order.
     *
     * The ordinal order *is* the contract, which is why it is asserted directly rather than
     * merely observed by running the listener.
     */
    enum class ArtTier {
        /** Bitmaps parcelled in the session metadata. Synchronous; see [bitmapKeyOrder]. */
        METADATA_BITMAP,

        /** URIs parcelled in the session metadata, loaded via Coil. See [uriKeyOrder]. */
        METADATA_URI,

        /** The large icon on the app's own posted media notification. */
        NOTIFICATION_ICON,

        /** Nothing found anywhere. */
        NONE,
    }

    /** Why artwork is being resolved. The answer decides what "found nothing" means. */
    enum class ArtTrigger {
        /** The pill has moved to a different session. */
        SESSION_CHANGE,

        /** The session we are already following reported new metadata. */
        METADATA_CHANGE,

        /** The app we are already following re-posted its notification. */
        NOTIFICATION_UPDATE,
    }

    /**
     * When no artwork can be found anywhere, should the cover on screen be cleared?
     *
     * **Only on a genuine session change**, and this asymmetry is load-bearing and trivially
     * invertible in both directions:
     *
     *  * On a session change, clearing is right. The new session may honestly have no cover, and
     *    leaving the *previous app's* art on the pill would be showing the user artwork for a
     *    track that is not playing.
     *  * On metadata churn or a notification update, clearing is wrong. Players re-post metadata
     *    constantly, and a resolution that happens to come up empty must leave a perfectly good
     *    cover standing rather than blank it.
     *
     * Invert it and you get one of two bugs, both of which look like a rendering fault rather
     * than a rule: a cover that blinks on every playback transition, or the previous track's art
     * stuck on screen for the rest of the session.
     *
     * This exists as a rule rather than a boolean literal at each call site precisely because a
     * literal cannot be asserted — a swapped `true`/`false` in the listener would pass every
     * test in the suite.
     */
    fun clearsArtWhenNoneFound(trigger: ArtTrigger): Boolean = trigger == ArtTrigger.SESSION_CHANGE

    /**
     * The metadata keys carrying a bitmap, in the order they are tried; first non-null wins.
     *
     * Display icon leads because it is the key a player sets when it wants a *specific* image
     * shown in compact UI, and it is the closest thing to an explicit instruction. Album art
     * and the generic art key follow as progressively less specific fallbacks.
     */
    fun bitmapKeyOrder(): List<String> = listOf(
        MediaMetadata.METADATA_KEY_DISPLAY_ICON,
        MediaMetadata.METADATA_KEY_ALBUM_ART,
        MediaMetadata.METADATA_KEY_ART,
    )

    /**
     * The metadata keys carrying a URI, in the order they are tried; first non-null-and-non-blank
     * wins. Same precedence argument as [bitmapKeyOrder], one tier further down the fallback.
     */
    fun uriKeyOrder(): List<String> = listOf(
        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
        MediaMetadata.METADATA_KEY_ART_URI,
    )

    /**
     * May an asynchronous load's result be published?
     *
     * Two independent conditions, and both must hold.
     *
     * **The generation must still match.** Every resolution takes a ticket before it starts; a
     * result whose ticket has been superseded belongs to a track the user has already skipped
     * past, and applying it drops a stale cover onto the current one. Skipping five tracks
     * quickly is exactly how that surfaces.
     *
     * **The result must be usable, unless the caller says a null result is itself an answer.**
     * The artwork path passes `allowNullResult = false`: a load that failed must leave the cover
     * on screen standing rather than blank it. The status-bar icon path passes `true`, because a
     * failed decode and no icon at all should look identical — anything else hangs the previous
     * app's glyph on the new app's pill.
     *
     * The two are ANDed, never ORed. Allowing nulls must not also wave a stale result through.
     *
     * @param capturedGeneration the counter value this resolution took before it started.
     * @param currentGeneration the counter value now.
     * @param loadedIsNull the load produced nothing.
     * @param allowNullResult a null result is a publishable answer rather than a failure.
     */
    fun shouldApplyAsync(
        capturedGeneration: Int,
        currentGeneration: Int,
        loadedIsNull: Boolean,
        allowNullResult: Boolean,
    ): Boolean = capturedGeneration == currentGeneration && (allowNullResult || !loadedIsNull)
}
