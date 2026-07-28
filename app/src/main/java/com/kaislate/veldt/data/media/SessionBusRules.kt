package com.kaislate.veldt.data.media

import android.media.session.PlaybackState

/**
 * The five decisions the media bus makes, lifted out of it so they can be asserted without a
 * `MediaController`, a `Bitmap` or an Android runtime.
 *
 * Every function here is total in its arguments. Everything that needs a live session —
 * reading the controller, comparing two covers pixel by pixel, actually publishing to a flow —
 * stays in the caller; what arrives here is already just data. The `PlaybackState.STATE_*`
 * values are compile-time constants, so referring to them by name costs nothing at runtime and
 * keeps this file readable next to the platform's own vocabulary.
 */
object SessionBusRules {

    /**
     * Which way should the play/pause control go, given the state the user can currently see?
     *
     * **Buffering counts as playing.** The user has already pressed play and is waiting for the
     * track to start; offering them "play" again there would do nothing they could perceive.
     * Everything else falls to play — including a null state, because pressing a transport
     * button on a stalled pill should start the music rather than sit there.
     *
     * @param publishedState the last `PlaybackState.STATE_*` published to the pill, or null.
     * @return true to pause, false to play.
     */
    fun toggleTarget(publishedState: Int?): Boolean =
        publishedState == PlaybackState.STATE_PLAYING ||
            publishedState == PlaybackState.STATE_BUFFERING

    /**
     * Does a bare state integer arriving on its own oblige us to rebuild the full
     * `PlaybackState` object so the two views agree?
     *
     * Only when there is an object to rebuild and it disagrees. A null incoming state against a
     * real current one *is* a disagreement — that is the case that drives the [rebuiltState]
     * substitution.
     *
     * @param hasCurrent whether a full state object is currently published.
     * @param currentState the state that object carries, if any.
     * @param incomingState the state integer just reported.
     */
    fun shouldRebuildPlayback(
        hasCurrent: Boolean,
        currentState: Int?,
        incomingState: Int?,
    ): Boolean = hasCurrent && currentState != incomingState

    /**
     * The state a rebuilt `PlaybackState` should carry, given the incoming integer.
     *
     * A real state is carried straight through. A null one cannot be: the object needs an
     * integer, and the substitute is `STATE_PAUSED` rather than `STATE_NONE` or `STATE_STOPPED`
     * because paused is the conservative reading of "the session stopped telling us". It keeps
     * the session alive in the service's active-playback calculation instead of tearing the
     * pill down over a momentary null.
     */
    fun rebuiltState(incomingState: Int?): Int = incomingState ?: PlaybackState.STATE_PAUSED

    /**
     * Should an incoming cover be published, or is the one on screen already right?
     *
     * A null cover is published only when the caller vouches that the session genuinely has
     * none. A real cover is published unless it is pixel-for-pixel what we are already showing:
     * some players parcel a fresh `Bitmap` of the same artwork on every play/pause transition,
     * and re-emitting each one made the image visibly blink.
     *
     * [allowNull] governs the null case **only**. It must never let a duplicate real cover
     * through, and it must never suppress a genuinely new one.
     *
     * @param incomingIsNull the new cover is absent.
     * @param currentIsNull nothing is on screen yet.
     * @param samePicture the two bitmaps hold the same picture; the caller does the comparing,
     *   and answers false whenever it cannot tell.
     * @param allowNull an absent cover means "there is none", not "I could not load one".
     */
    fun shouldPublishArt(
        incomingIsNull: Boolean,
        currentIsNull: Boolean,
        samePicture: Boolean,
        allowNull: Boolean,
    ): Boolean =
        if (incomingIsNull) allowNull else currentIsNull || !samePicture

    /**
     * Should incoming metadata be published?
     *
     * Null metadata is dropped, and this is a contract rather than a guard: players re-post
     * null around pause and seek, and letting it through swapped the panel to placeholders and
     * straight back, which reads as a blink. The last good metadata stands until a genuine
     * session change replaces it.
     */
    fun shouldPublishMetadata(incomingIsNull: Boolean): Boolean = !incomingIsNull
}
