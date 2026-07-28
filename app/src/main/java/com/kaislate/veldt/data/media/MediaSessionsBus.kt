package com.kaislate.veldt.data.media

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single mirror of whatever media session the device is currently playing from, and the
 * one place the pill reaches through to drive it.
 *
 * Everything the overlay, the panel and the foreground service know about the music arrives
 * through the seven flows below. They are published as read-only [StateFlow]s over private
 * mutable backing fields, so a consumer can collect them but cannot write to them — the
 * listener service is the only writer, by way of the update methods.
 *
 * Two views of the same thing are published deliberately. [playbackState] is the bare
 * `PlaybackState.STATE_*` integer that the state machine and the service switch on;
 * [playback] is the whole object, carrying the position, the playback speed and the
 * advertised actions that the panel's playhead extrapolation needs. Keeping the two in step
 * whichever direction an update arrives from is [updatePlaybackState]'s and
 * [updatePlayback]'s job.
 *
 * Every branching decision in here is delegated to [SessionBusRules] so it can be asserted
 * without a [MediaController]; this object holds no logic of its own beyond dispatching on
 * the answers and touching the Android APIs the rules cannot.
 *
 * Nothing here is synchronised. All writes arrive from the notification listener's callbacks
 * on the main thread, and [MutableStateFlow] publishes safely to collectors on any other.
 */
object MediaSessionBus {

    /** The session the transport methods drive. Null between sessions, and often. */
    private var controller: MediaController? = null

    private val _activePackage = MutableStateFlow<String?>(null)

    /** Package name of the app that owns the current session, or null when there is none. */
    val activePackage: StateFlow<String?> = _activePackage.asStateFlow()

    private val _playbackState = MutableStateFlow<Int?>(null)

    /** The bare `PlaybackState.STATE_*` integer, or null when the session has not said. */
    val playbackState: StateFlow<Int?> = _playbackState.asStateFlow()

    private val _playback = MutableStateFlow<PlaybackState?>(null)

    /** The full state object, carrying position, speed and the advertised actions. */
    val playback: StateFlow<PlaybackState?> = _playback.asStateFlow()

    private val _metadata = MutableStateFlow<MediaMetadata?>(null)

    /** Title, artist, duration and the rest, as last reported by a *non-null* update. */
    val metadata: StateFlow<MediaMetadata?> = _metadata.asStateFlow()

    private val _albumArt = MutableStateFlow<Bitmap?>(null)

    /** The cover the pill is showing. Resolved by the listener, de-duplicated by [setAlbumArt]. */
    val albumArt: StateFlow<Bitmap?> = _albumArt.asStateFlow()

    private val _customActions = MutableStateFlow<List<PlaybackState.CustomAction>>(emptyList())

    /** Player-specific extra buttons, empty when the session advertises none. */
    val customActions: StateFlow<List<PlaybackState.CustomAction>> = _customActions.asStateFlow()

    private val _smallIcon = MutableStateFlow<Bitmap?>(null)

    /** The playing app's status-bar glyph, pushed by the listener straight after a session change. */
    val smallIcon: StateFlow<Bitmap?> = _smallIcon.asStateFlow()

    /**
     * Adopt a new session, replacing the whole published state at once.
     *
     * Two apparent omissions are deliberate:
     *
     * **The album art is not touched.** Resolving it needs a `Context` — it may live behind a
     * URI or inside a notification icon — so the listener pushes it a moment later. Leaving
     * the previous cover up for that fraction of a second beats showing a blank frame.
     *
     * **The small icon *is* cleared**, because the listener re-pushes it synchronously right
     * after this call. Leaving it would hang the previous app's glyph on the new session.
     */
    fun attachController(c: MediaController?) {
        controller = c
        // One snapshot, read once: the three values derived from it must not be allowed to
        // disagree, which is exactly the invariant updatePlaybackState exists to defend.
        val state = c?.playbackState
        _activePackage.value = c?.packageName
        _playbackState.value = state?.state
        _playback.value = state
        _metadata.value = c?.metadata
        _customActions.value = state?.customActions ?: emptyList()
        _smallIcon.value = null
    }

    /** Publish the playing app's status-bar glyph. */
    fun setSmallIcon(bmp: Bitmap?) {
        _smallIcon.value = bmp
    }

    /**
     * Publish a cover, unless it is one we are already showing.
     *
     * Some players — VLC reproducibly — parcel a brand-new [Bitmap] instance of the *same*
     * artwork on every play/pause transition. Publishing each instance made the image reload
     * and visibly blink, so identical pictures are dropped rather than re-emitted.
     *
     * @param allowNull whether a null cover means "this session genuinely has none" (publish
     *   it) or merely "I could not resolve one this time" (keep what is on screen). It governs
     *   nulls only; it has no bearing on a real bitmap.
     */
    fun setAlbumArt(newArt: Bitmap?, allowNull: Boolean = false) {
        val current = _albumArt.value
        val publish = SessionBusRules.shouldPublishArt(
            incomingIsNull = newArt == null,
            currentIsNull = current == null,
            samePicture = newArt != null && current != null && samePicture(newArt, current),
            allowNull = allowNull,
        )
        if (publish) _albumArt.value = newArt
    }

    /**
     * Do these two bitmaps hold the same picture?
     *
     * Dimensions are compared first: that settles most pairs without reading a single pixel
     * of a multi-megabyte cover. Only then does [Bitmap.sameAs] walk them.
     *
     * **Any failure answers "different".** A recycled or hardware-config bitmap cannot be read
     * back, and walking a full-size cover can plausibly run out of memory, so [Error] is caught
     * alongside [Exception]. The asymmetry is the safe one: a redundant emit costs a single
     * flicker, whereas a wrongly dropped emit strands the previous track's cover on screen for
     * the rest of the session.
     */
    private fun samePicture(a: Bitmap, b: Bitmap): Boolean = try {
        a.width == b.width && a.height == b.height && a.sameAs(b)
    } catch (t: Throwable) {
        false
    }

    /**
     * Publish a new state integer and bring the full object back into step with it.
     *
     * The integer is published unconditionally. The full object is then rebuilt only if it
     * exists and disagrees, and the rebuild **carries the existing position and playback speed
     * across unchanged** — the panel extrapolates the playhead from
     * `position + (now − lastUpdate) × speed`, so a replacement that reset them would snap the
     * scrub bar to the start of the track every time someone pressed pause.
     *
     * A null state is published as null, but the full object cannot hold one; see
     * [SessionBusRules.rebuiltState] for why the substitute is `STATE_PAUSED`.
     */
    fun updatePlaybackState(state: Int?) {
        _playbackState.value = state
        val current = _playback.value
        val rebuild = SessionBusRules.shouldRebuildPlayback(
            hasCurrent = current != null,
            currentState = current?.state,
            incomingState = state,
        )
        if (rebuild && current != null) {
            _playback.value = PlaybackState.Builder(current)
                .setState(
                    SessionBusRules.rebuiltState(state),
                    current.position,
                    current.playbackSpeed,
                )
                .build()
        }
    }

    /**
     * Publish a whole new state object, deriving the bare integer and the custom actions from
     * it. This is the inverse direction from [updatePlaybackState], and the one the listener's
     * playback callback uses.
     */
    fun updatePlayback(playback: PlaybackState?) {
        _playback.value = playback
        _playbackState.value = playback?.state
        _customActions.value = playback?.customActions ?: emptyList()
    }

    /**
     * Publish new metadata.
     *
     * **A null argument is a defined no-op, not a defensive guard.** Several players
     * momentarily re-post null metadata around pause and seek; letting that through swapped
     * the whole panel to placeholders and back, which reads as a blink. The last good metadata
     * therefore stands until [attachController] replaces it on a genuine session change.
     * Do not "fix" this into a straight assignment.
     */
    fun updateMetadata(meta: MediaMetadata?) {
        if (SessionBusRules.shouldPublishMetadata(incomingIsNull = meta == null)) {
            _metadata.value = meta
        }
    }

    /**
     * Resume playback. Like every transport method here it is a silent no-op when there is no
     * controller — a session can go away between a tap and its dispatch, and that must never
     * take the overlay down with it.
     */
    fun play() {
        controller?.transportControls?.play()
    }

    /** Pause playback; a no-op without a controller. */
    fun pause() {
        controller?.transportControls?.pause()
    }

    /**
     * Pause or resume, decided from the **currently published state integer** rather than by
     * asking the controller, so the pill acts on exactly what the user can see.
     *
     * Buffering counts as playing, because the user has already pressed play and is waiting;
     * see [SessionBusRules.toggleTarget].
     */
    fun togglePlayPause() {
        if (SessionBusRules.toggleTarget(_playbackState.value)) pause() else play()
    }

    /** Skip to the next track; a no-op without a controller. */
    fun next() {
        controller?.transportControls?.skipToNext()
    }

    /** Skip to the previous track; a no-op without a controller. */
    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    /** Seek to [posMs] milliseconds into the track; a no-op without a controller. */
    fun seekTo(posMs: Long) {
        controller?.transportControls?.seekTo(posMs)
    }

    /** Fire one of the session's advertised extra buttons; a no-op without a controller. */
    fun sendCustomAction(a: PlaybackState.CustomAction) {
        controller?.transportControls?.sendCustomAction(a, null)
    }
}
