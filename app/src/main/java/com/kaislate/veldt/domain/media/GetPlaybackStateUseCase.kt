package com.kaislate.veldt.domain.media

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.kaislate.veldt.data.media.MediaSessionBus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The read side of the media session: what is playing, and what it looks and sounds
 * like right now.
 *
 * Four separate streams rather than one combined snapshot, because they change at very
 * different rates — the transport state flips on every pause, the artwork survives a
 * whole album — and combining them would rebuild the pill's expensive parts every time
 * a cheap one moved.
 *
 * Read-only by construction: it re-exposes [MediaSessionBus]'s streams and offers no
 * way to write to them. [ControlPlaybackUseCase] is the other half.
 *
 * [playbackState] is narrowed to a plain [Flow] deliberately — it is the one stream a
 * consumer has no business reading a current value out of, since acting on a transport
 * state sampled outside the flow is how the pill ends up showing a stale play icon.
 */
class GetPlaybackStateUseCase @Inject constructor() {
    val playbackState: Flow<Int?> = MediaSessionBus.playbackState
    val metadata: StateFlow<MediaMetadata?> = MediaSessionBus.metadata
    val albumArt: StateFlow<Bitmap?> = MediaSessionBus.albumArt
    val playback: StateFlow<PlaybackState?> = MediaSessionBus.playback
}
