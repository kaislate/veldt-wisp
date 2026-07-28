// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.services

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import com.kaislate.veldt.data.media.MediaSessionBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app's only source of media truth.
 *
 * It holds notification-listener access, watches every active media session on the device,
 * decides which one the pill should follow, and pushes that session's state, metadata, artwork
 * and status-bar glyph into [MediaSessionBus]. Everything downstream — the state machine, the
 * foreground service, the pill, the panel — reads what this file decided.
 *
 * ## The selection here is what suppresses the pill over the playing app
 *
 * [MediaSessionBus.activePackage] is set from the session this class picks, and
 * [PollRules.targetInForeground] compares that package against the package on screen to decide
 * whether to get out of the user's way. Two consequences are worth carrying in mind before
 * changing anything here:
 *
 *  * **Picking the wrong session does not look like a selection bug.** It looks like the pill
 *    reappearing on top of the app that is playing, because the package being compared is not
 *    the one on screen — and it sends the reader to [PollRules] or the foreground poll, neither
 *    of which is at fault.
 *  * **No session must publish a null package, never the last-known one.** A stale package makes
 *    `targetInForeground` false forever, and the pill then shows in places it should not.
 *
 * The class name and package are named directly in `AndroidManifest.xml` and are frozen.
 *
 * ## What this class must not do
 *
 * It must never call into `UsageStatsRepository`. `latestForegroundPackage()` is a cursor
 * consumer, not a getter: a second caller in the same tick splits one event batch across two
 * folds and silently desynchronises the foreground reading. The poll in
 * [IslandForegroundService] is the only legitimate caller and must stay so.
 *
 * Every branching decision is delegated to [SessionRules] so it can be asserted without a
 * [MediaController]; what is left here is the Android surface the rules cannot touch.
 */
class MediaNotificationListener : NotificationListenerService() {

    /** The system service, obtained on connect. */
    private lateinit var sessions: MediaSessionManager

    /** The session the pill currently follows. Null between sessions, and often. */
    private var active: MediaController? = null

    /** The latest full session list, exactly as the system ordered it. */
    private var known: List<MediaController> = emptyList()

    /** One callback per live session, kept so it can be handed back for unregistration. */
    private val watchers = mutableMapOf<MediaSession.Token, MediaController.Callback>()

    /** Async artwork and icon resolution. Main-immediate so publishes land on the main thread. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Ticket counters guarding against out-of-order async results. See [resolveArt]. */
    private var artGeneration = 0
    private var iconGeneration = 0

    private val sessionsChanged =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            onSessionsChanged(controllers ?: emptyList())
        }

    // ---- lifecycle ----

    override fun onListenerConnected() {
        super.onListenerConnected()
        sessions = getSystemService(MediaSessionManager::class.java)
        val self = ComponentName(this, MediaNotificationListener::class.java)
        // The component name is required: a notification listener may not pass null here.
        sessions.addOnActiveSessionsChangedListener(sessionsChanged, self)
        // Seeding, and it is not redundant. The listener above only fires on the *next* change,
        // so connecting while music is already playing would otherwise show no pill at all —
        // which is exactly what a fresh notification-access grant looks like.
        onSessionsChanged(currentSessions(self))
    }

    override fun onDestroy() {
        unwatchAll()
        if (::sessions.isInitialized) {
            runCatching { sessions.removeOnActiveSessionsChangedListener(sessionsChanged) }
        }
        scope.cancel()
        super.onDestroy()
    }

    /**
     * The current session list, or nothing at all if the system refuses.
     *
     * **[SecurityException] specifically, not [Throwable].** At connect time the
     * notification-access grant may not have propagated yet, so a security failure here is
     * expected and survivable; anything else is a real bug that should surface rather than be
     * swallowed into an empty list. Contrast [notificationsFrom], which is deliberately broader.
     */
    private fun currentSessions(self: ComponentName): List<MediaController> = try {
        sessions.getActiveSessions(self)
    } catch (e: SecurityException) {
        emptyList()
    }

    // ---- watching every session, not just the chosen one ----

    /**
     * Rebuild the watcher set against a new session list, then re-run selection.
     *
     * A callback is registered on **every** controller, not only the active one. The
     * sessions-changed event does not fire when playback merely moves between two sessions that
     * both already exist — two YouTube-family apps, or a browser and a player — so without a
     * watcher on each, a handover is invisible and the pill keeps following a session that
     * stopped playing.
     */
    private fun onSessionsChanged(controllers: List<MediaController>) {
        // Unregister against the OLD list first. The watcher map is keyed by token, but the
        // controllers the callbacks were registered on live in `known`; replace `known` before
        // this and there is no way left to find them.
        unwatchAll()

        known = controllers
        controllers.forEach { controller ->
            val callback = watcherFor(controller)
            controller.registerCallback(callback)
            watchers[controller.sessionToken] = callback
        }
        reselect()
    }

    private fun unwatchAll() {
        known.forEach { controller ->
            watchers[controller.sessionToken]?.let(controller::unregisterCallback)
        }
        watchers.clear()
    }

    private fun watcherFor(controller: MediaController) = object : MediaController.Callback() {

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (isActive(controller)) {
                MediaSessionBus.updatePlaybackState(state?.state)
                MediaSessionBus.updatePlayback(state)
                IslandForegroundService.start(this@MediaNotificationListener)
            }
            // Unconditional, and this is the line that makes handover work: a non-active session
            // announcing that it has started playing is the *only* signal we get, because no
            // sessions-changed event accompanies it.
            reselect()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (!isActive(controller)) return
            MediaSessionBus.updateMetadata(metadata)
            // Metadata churn must not be able to blank the cover. And no reselect — a track
            // change is not a reason to reconsider which session to follow.
            resolveArt(metadata, SessionRules.ArtTrigger.METADATA_CHANGE)
        }
    }

    private fun isActive(controller: MediaController): Boolean =
        controller.sessionToken == active?.sessionToken

    // ---- selection ----

    /**
     * Ask [SessionRules] which session the pill should follow, and republish only if the answer
     * actually changed.
     *
     * The change gate is not an optimisation. This runs on every playback tick of every watched
     * session, so without it the bus would be rewritten several times a second and the panel
     * would rebuild constantly.
     */
    private fun reselect() {
        val summaries = known.map {
            SessionRules.SessionSummary(it.sessionToken, it.playbackState?.state)
        }
        val chosen = SessionRules.select(summaries, active?.sessionToken)
        val candidate = chosen?.let { c -> known.firstOrNull { it.sessionToken == c.token } }

        if (!SessionRules.isChange(candidate?.sessionToken, active?.sessionToken)) return

        active = candidate
        MediaSessionBus.attachController(candidate)
        // Only for a real candidate: with no session there is nothing left to watch, and
        // starting the service just to have it retire would post a foreground notification
        // for nothing.
        if (SessionRules.shouldStartService(candidate?.sessionToken)) {
            IslandForegroundService.start(this)
        }
        MediaSessionBus.updatePlaybackState(candidate?.playbackState?.state)
        MediaSessionBus.updateMetadata(candidate?.metadata)
        // SESSION_CHANGE is the ONLY trigger that lets "no art found" clear the cover; see
        // SessionRules.clearsArtWhenNoneFound for why, and for why that is a rule rather than
        // a boolean literal sitting here where nothing could ever assert it.
        resolveArt(candidate?.metadata, SessionRules.ArtTrigger.SESSION_CHANGE)
        resolveIcon(candidate?.packageName)
    }

    // ---- artwork ----

    /**
     * Find a cover for [metadata] and publish it, through four tiers of fallback.
     *
     * Every resolution takes a ticket first — [artGeneration] is incremented and the new value
     * captured locally — and an asynchronous result is applied only while that ticket is still
     * current. That is what stops a slow URI load belonging to a track the user has already
     * skipped past from landing on top of the cover now on screen.
     *
     * @param trigger why we are resolving. It reaches only tier 4, where it decides whether
     *   "found nothing" clears the cover or leaves what is on screen standing.
     */
    private fun resolveArt(metadata: MediaMetadata?, trigger: SessionRules.ArtTrigger) {
        val generation = ++artGeneration

        // Tier 1 — a bitmap parcelled straight into the metadata. Nothing asynchronous happens,
        // so there is no ticket to re-check.
        val embedded = metadata?.let { m ->
            SessionRules.bitmapKeyOrder().firstNotNullOfOrNull(m::getBitmap)
        }
        if (embedded != null) {
            MediaSessionBus.setAlbumArt(embedded, allowNull = false)
            return
        }

        // Tier 2 — a URI in the metadata, fetched through Coil.
        val uri = metadata?.let { m ->
            SessionRules.uriKeyOrder()
                .firstNotNullOfOrNull { key -> m.getString(key)?.takeIf(String::isNotBlank) }
        }
        if (uri != null) {
            scope.launch { publishArt(loadUri(uri), generation) }
            return
        }

        // Tier 3 — the app's own media notification. Some players put artwork in session
        // metadata not at all and only here. It can still come back empty: the system strips
        // some notification images before delivering them to listeners.
        val large = notificationsFrom(active?.packageName)
            .firstNotNullOfOrNull { it.notification.getLargeIcon() }
        if (large != null) {
            scope.launch { publishArt(decodeIcon(large), generation) }
            return
        }

        // Tier 4 — nothing anywhere, and the trigger decides what that means.
        MediaSessionBus.setAlbumArt(null, allowNull = SessionRules.clearsArtWhenNoneFound(trigger))
    }

    /**
     * Apply an asynchronously loaded cover, if it is still wanted.
     *
     * `allowNullResult = false` throughout: a load that produced nothing is a failure, not an
     * answer, and must leave the cover on screen standing. Tier 4 above is the only path that
     * publishes a null cover, and only when its caller allowed it.
     */
    private fun publishArt(loaded: Bitmap?, generation: Int) {
        val apply = SessionRules.shouldApplyAsync(
            capturedGeneration = generation,
            currentGeneration = artGeneration,
            loadedIsNull = loaded == null,
            allowNullResult = false,
        )
        if (apply) MediaSessionBus.setAlbumArt(loaded, allowNull = false)
    }

    /**
     * Fetch a cover from a URI.
     *
     * **`allowHardware(false)` is required, not defensive.** `ColorExtractor` samples these
     * pixels through `Palette`, and a hardware bitmap cannot be read back. The defensive guard
     * downstream costs a full-size copy; this flag avoids ever paying for it. No explicit size
     * is set — the cover is displayed at more than one.
     */
    private suspend fun loadUri(uri: String): Bitmap? = try {
        val request = ImageRequest.Builder(this)
            .data(uri)
            .allowHardware(false)
            .build()
        Coil.imageLoader(this).execute(request).drawable?.toBitmapOrNull()
    } catch (t: Throwable) {
        null
    }

    // ---- status-bar icon ----

    /**
     * Find the playing app's status-bar glyph and publish it.
     *
     * Mirrors [resolveArt] but is simpler, and differs from it in one deliberate way: this path
     * publishes **even a null result**, because a failed decode and no icon at all should look
     * identical. Anything else hangs the previous app's glyph on the new app's pill.
     */
    private fun resolveIcon(packageName: String?) {
        val generation = ++iconGeneration
        val icon = notificationsFrom(packageName)
            .firstNotNullOfOrNull { it.notification.smallIcon }

        if (icon == null) {
            // Immediately, without waiting on anything: the point is to clear the last app's
            // glyph, and deferring that is what leaves it lingering.
            MediaSessionBus.setSmallIcon(null)
            return
        }

        scope.launch {
            val decoded = decodeIcon(icon)
            val apply = SessionRules.shouldApplyAsync(
                capturedGeneration = generation,
                currentGeneration = iconGeneration,
                loadedIsNull = decoded == null,
                allowNullResult = true,
            )
            if (apply) MediaSessionBus.setSmallIcon(decoded)
        }
    }

    /** Turn an [Icon] into a bitmap. Loading one can hit the package manager or disk, so it
     *  does not belong on the main thread. Any failure answers "nothing". */
    private suspend fun decodeIcon(icon: Icon): Bitmap? = withContext(Dispatchers.IO) {
        try {
            icon.loadDrawable(this@MediaNotificationListener)?.toBitmapOrNull()
        } catch (t: Throwable) {
            null
        }
    }

    // ---- notifications ----

    /**
     * The notifications currently posted by [packageName].
     *
     * **Swallows any [Throwable], deliberately.** Reading other apps' posted notifications fails
     * in a wide variety of OEM-specific ways, and none of them should take the listener down.
     * This is broader than [currentSessions]'s narrow [SecurityException] catch on purpose;
     * both behaviours are intentional and should not be unified.
     */
    private fun notificationsFrom(packageName: String?): List<StatusBarNotification> {
        if (packageName == null) return emptyList()
        return try {
            activeNotifications?.filter { it.packageName == packageName } ?: emptyList()
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /**
     * Re-resolve artwork and icon when the app we are following updates its notification.
     *
     * Some players only ever put artwork in the notification, and the notification can be
     * updated well after the session metadata was published — without this hook their pill
     * stays blank. This is an update, not a session change, so an empty result must not clear
     * the cover.
     *
     * Notification *removal* and listener disconnection are deliberately not overridden. The
     * session watchers and the sessions-changed listener are the reactive path; a notification
     * going away does not mean the session did.
     */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val followed = active?.packageName ?: return
        if (sbn?.packageName != followed) return
        resolveArt(active?.metadata, SessionRules.ArtTrigger.NOTIFICATION_UPDATE)
        resolveIcon(followed)
    }

    /** A drawable's own bitmap when it has one, otherwise a drawn copy; null if neither works. */
    private fun Drawable.toBitmapOrNull(): Bitmap? =
        (this as? BitmapDrawable)?.bitmap ?: runCatching { toBitmap() }.getOrNull()
}
