package com.kaislate.veldt.domain.media

import com.kaislate.veldt.data.media.MediaSessionBus
import javax.inject.Inject

/**
 * The three transport commands the pill can issue.
 *
 * [MediaSessionBus] is an object, so this class holds no state and exists for the
 * boundary rather than the plumbing: callers depend on an injected type they can swap
 * in a test instead of reaching for a global, and they get exactly the three verbs the
 * pill's controls offer rather than the whole session surface.
 *
 * Every command is fire-and-forget. Whether anything happens is up to the session that
 * is currently attached, and with none attached these are silently no-ops — which is
 * the wanted behaviour, since the buttons are only on screen when something is playing.
 */
class ControlPlaybackUseCase @Inject constructor() {
    fun previous() = MediaSessionBus.previous()
    fun next() = MediaSessionBus.next()
    fun toggle() = MediaSessionBus.togglePlayPause()
}
