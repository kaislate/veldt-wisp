// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.ui.island

import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import com.kaislate.veldt.R
import com.kaislate.veldt.data.audio.AudioOutputProvider
import com.kaislate.veldt.data.media.MediaSessionBus
import com.kaislate.veldt.overlay.ColorExtractor
import com.kaislate.veldt.overlay.DominantColors
import com.kaislate.veldt.ui.components.HillsWave
import com.kaislate.veldt.ui.components.cardFxOffset
import com.kaislate.veldt.ui.components.cardFxScale
import com.kaislate.veldt.ui.components.drawCardEffect
import com.kaislate.veldt.ui.components.drawCardEffectBg
import com.kaislate.veldt.ui.components.drawWave
import com.kaislate.veldt.ui.components.isPremiumStyle
import com.kaislate.veldt.util.loadCustomActionIcon
import kotlinx.coroutines.delay

/** Corner radius shared by the card and every full-bleed layer inside it. */
private val CARD_SHAPE = RoundedCornerShape(28.dp)

/**
 * The expanded panel: blurred artwork under a glass scrim, the track's identity,
 * a seekable wavy scrub bar and the transport.
 *
 * The panel owns only presentation. Where the playhead is, how far along the track
 * that is, how to write it down and which colour the wave should be are all decided
 * by [Playhead] and [PopUpColors], which are pure and unit-tested; this composable
 * supplies the clock, the recomposition and the gestures and asks them the answers.
 *
 * [onSwipeUpClose] collapses the panel. It fires both on an upward flick and after
 * the header block launches the playing app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicPopUp(
    onSwipeUpClose: () -> Unit,
    vibrant: Boolean = false,
    thumbShape: String = "circle",
    waveColorMode: String = "auto",
    crossfadeMs: Int = 450,
    waveStyle: String = "hills",
    consume: Boolean = false,
) {
    val ctx = LocalContext.current
    val view = LocalView.current

    // ---- Observed session state -------------------------------------------------
    val playbackState by MediaSessionBus.playbackState.collectAsState(initial = null)
    val playback by MediaSessionBus.playback.collectAsState(initial = null)
    val metadata by MediaSessionBus.metadata.collectAsState(initial = null)
    val albumArt by MediaSessionBus.albumArt.collectAsState(initial = null)
    val customActions by MediaSessionBus.customActions.collectAsState(initial = emptyList())
    val activePackage by MediaSessionBus.activePackage.collectAsState(initial = null)
    val smallIcon by MediaSessionBus.smallIcon.collectAsState(initial = null)

    // The output device has no flow of its own; the metadata is the cheapest available
    // proxy for "something happened", so re-ask whenever the track changes.
    val output = remember(metadata) { AudioOutputProvider.current(ctx) }

    // Placeholder palette until the art is analysed, so the very first frame still has
    // a legible foreground on an opaque background.
    var palette by remember {
        mutableStateOf(
            DominantColors(
                bg = Color(0xFF1E1E1E),
                onBg = Color(0xFFF5F5F5),
                accent = Color(0xFF888888)
            )
        )
    }
    LaunchedEffect(albumArt) { palette = ColorExtractor.extract(albumArt) }

    val effectiveAccent = PopUpColors.effectiveAccent(waveColorMode, palette)

    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Title"
    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Artist"
    val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

    val isPlaying = playbackState == PlaybackState.STATE_PLAYING ||
        playbackState == PlaybackState.STATE_BUFFERING

    // ---- The playhead ticker ----------------------------------------------------
    // Re-keying on the PlaybackState object is what makes a seek land instantly: a new
    // object means a new reported position, and the loop restarts against it.
    var positionMs by remember { mutableStateOf(0L) }
    var progress by remember(duration) { mutableStateOf(0f) }
    LaunchedEffect(duration, playback, isPlaying) {
        while (true) {
            val pb = playback
            // `advancing` comes from the PlaybackState object, not the observed integer:
            // it is the one consistent with lastPositionUpdateTime (see Playhead).
            val pos = Playhead.positionMs(
                reportedMs = pb?.position ?: 0L,
                reportedAtMs = pb?.lastPositionUpdateTime ?: 0L,
                speed = pb?.playbackSpeed ?: 1f,
                advancing = pb?.state == PlaybackState.STATE_PLAYING ||
                    pb?.state == PlaybackState.STATE_BUFFERING,
                nowMs = SystemClock.elapsedRealtime(),
                durationMs = duration,
            )
            positionMs = pos
            progress = Playhead.progress(pos, duration)
            // Compute first, wait after: a freshly opened panel shows the right position
            // immediately rather than half a second later.
            delay(Playhead.TICK_MS)
        }
    }

    // ---- Entrance bounce --------------------------------------------------------
    // Enter 8% small, overshoot by 2%, settle. ~1.1s end to end.
    val bounce = remember { Animatable(0.92f) }
    LaunchedEffect(Unit) {
        bounce.snapTo(0.92f)
        bounce.animateTo(1.02f, tween(550))
        bounce.animateTo(1.00f, tween(550))
    }

    // ---- Premium effect layer ---------------------------------------------------
    val premium = isPremiumStyle(waveStyle)
    // 20π per loop, shared with the scrub bar's clock, so every layer running at an
    // integer multiple of the base speed wraps with no visible pattern snap.
    val phase: State<Float> = if (premium) {
        val fx = rememberInfiniteTransition(label = "card-fx")
        fx.animateFloat(
            initialValue = 0f,
            targetValue = (20f * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing)),
            label = "card-fx-phase"
        )
    } else remember { mutableStateOf(0f) }
    // The effects breathe down rather than freeze when the user pauses.
    val intensity by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.35f,
        animationSpec = tween(600),
        label = "card-fx-intensity"
    )
    val artImage = remember(albumArt) { albumArt?.asImageBitmap() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = bounce.value * if (premium) cardFxScale(waveStyle, phase.value) else 1f
                scaleX = s
                scaleY = s
                if (premium) {
                    val o = cardFxOffset(waveStyle, phase.value)
                    translationX = o.x
                    translationY = o.y
                }
            }
            .clip(CARD_SHAPE)
            .pointerInput(Unit) {
                // Per-event, not accumulated: a flick closes the panel, a slow drag
                // never crosses the threshold in any one event and leaves it open.
                detectVerticalDragGestures { _, drag ->
                    if (Playhead.isCloseSwipe(drag)) onSwipeUpClose()
                }
            }
            .drawWithContent {
                drawContent()
                // Foreground pass: translucent/additive only, and clipped by the shape
                // above, so the text underneath stays legible.
                if (premium) {
                    drawCardEffect(
                        waveStyle, phase.value, effectiveAccent, palette.waveColors,
                        artImage, vibrant, intensity
                    )
                }
            }
    ) {
        // Layer 0 — opaque base. NOT optional: Modifier.blur is a no-op below API 31,
        // and some players (VLC) supply no album art at all; without this the launcher
        // shows through the panel on Android 10 and 11.
        Box(
            Modifier
                .matchParentSize()
                .clip(CARD_SHAPE)
                .background(palette.bg)
        )

        // Layer 1 — blurred artwork. Unbounded edge treatment so the blur has no hard
        // rectangle edge; the card's own clip rounds it off.
        CrossfadeArt(
            bitmap = albumArt,
            durationMs = crossfadeMs,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .clip(CARD_SHAPE)
                .blur(10.dp, BlurredEdgeTreatment.Unbounded)
                .alpha(0.90f)
        )

        // Layer 2 — glass scrim, strong enough that the foreground colour's contrast
        // assumption holds over any artwork.
        Box(
            Modifier
                .matchParentSize()
                .clip(CARD_SHAPE)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            palette.bg.copy(alpha = 0.58f),
                            palette.bg.copy(alpha = 0.44f)
                        )
                    )
                )
        )

        // Layer 2b — premium scenery that belongs BEHIND the text: aurora curtains,
        // grids, starfields.
        if (premium) {
            Canvas(
                Modifier
                    .matchParentSize()
                    .clip(CARD_SHAPE)
            ) {
                drawCardEffectBg(
                    waveStyle, phase.value, effectiveAccent, palette.waveColors,
                    artImage, vibrant, intensity
                )
            }
        }

        // Layer 3 — the sharp content.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            val fg = palette.onBg
            val fgDim = fg.copy(alpha = 0.85f)

            // (a) Tappable header: opens the playing app, then collapses. No ripple —
            // a ripple on a floating panel reads as a mis-tap.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { openPlayingApp(ctx, activePackage, onSwipeUpClose) }
            ) {
                // Output row.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = output.icon,
                        contentDescription = null,
                        tint = fgDim,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = output.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = fgDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // Source-app indicator. It inherits the header's tap, so tapping it
                    // opens the app.
                    smallIcon?.let { icon ->
                        Spacer(Modifier.width(8.dp))
                        Image(
                            bitmap = icon.asImageBitmap(),
                            contentDescription = "Playing app",
                            colorFilter = ColorFilter.tint(fgDim),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Art and titles row.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CrossfadeArt(
                        bitmap = albumArt,
                        durationMs = crossfadeMs,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = fg,
                            maxLines = 1,
                            // Clipped, not ellipsised: the marquee needs the overflow to
                            // scroll rather than be replaced by an ellipsis.
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        )
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = fgDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    HillsWave(
                        isPlaying = isPlaying,
                        color = effectiveAccent,
                        vibrant = vibrant,
                        waveColors = palette.waveColors,
                        modifier = Modifier.size(width = 54.dp, height = 30.dp)
                    )
                }
            }

            // (b)
            Spacer(Modifier.height(12.dp))

            // (c) Scrub bar.
            SeekableProgressBar(
                progress = progress,
                isPlaying = isPlaying,
                onSeek = { fraction ->
                    // Seeking a live stream by fraction is meaningless.
                    if (duration > 0L) MediaSessionBus.seekTo((fraction * duration).toLong())
                },
                modifier = Modifier.fillMaxWidth(),
                progressColor = fgDim,
                trackColor = fg.copy(alpha = 0.22f),
                accentColor = effectiveAccent,
                vibrant = vibrant,
                waveColors = palette.waveColors,
                thumbShape = thumbShape,
                waveStyle = waveStyle,
                consume = consume
            )

            // (d) Times row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val timeColor = fg.copy(alpha = 0.9f)
                Text(
                    text = Playhead.formatTime(positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = timeColor
                )
                Text(
                    text = Playhead.formatTime(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = timeColor
                )
            }

            // (e)
            Spacer(Modifier.height(12.dp))

            // (f) Controls.
            val pkg = activePackage
            // Custom actions are only meaningful with an owning package to load their
            // icons from. The icons already encode the app's own state (shuffle on vs
            // off), so there is no extra state handling here.
            val shownActions = if (pkg != null) customActions.take(4) else emptyList()
            // Some sessions (VLC with a single item) terminate when sent a skip they do
            // not support, so a greyed-out button prevents a real footgun.
            val sessionActions = playback?.actions
            val canPrev = sessionActions == null ||
                (sessionActions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L
            val canNext = sessionActions == null ||
                (sessionActions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pkg != null) {
                    shownActions.take(2).forEach { action ->
                        CustomActionButton(ctx, pkg, action, view, fg)
                    }
                }

                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        MediaSessionBus.previous()
                    },
                    enabled = canPrev
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        tint = if (canPrev) fg else fg.copy(alpha = 0.3f)
                    )
                }

                IconButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    MediaSessionBus.togglePlayPause()
                }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = fg
                    )
                }

                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        MediaSessionBus.next()
                    },
                    enabled = canNext
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = if (canNext) fg else fg.copy(alpha = 0.3f)
                    )
                }

                if (pkg != null) {
                    shownActions.drop(2).forEach { action ->
                        CustomActionButton(ctx, pkg, action, view, fg)
                    }
                }
            }
        }
    }
}

/**
 * One UI-style wavy scrub bar: the played portion is an animated sine wave that
 * flattens when paused (or while scrubbing); the remainder is a thin flat track.
 * Tap to jump, drag to seek.
 */
@Composable
private fun SeekableProgressBar(
    progress: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    progressColor: Color,
    trackColor: Color,
    accentColor: Color = progressColor,
    vibrant: Boolean = false,
    waveColors: List<Color> = emptyList(),
    thumbShape: String = "circle",
    waveStyle: String = "hills",
    consume: Boolean = false
) {
    // While dragging, show the drag position instead of playback progress.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shown = (dragFraction ?: progress).coerceIn(0f, 1f)

    val infinite = rememberInfiniteTransition(label = "wave")
    // 20π per loop so every layer-speed multiple wraps seamlessly (no pattern snap).
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (20f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing)),
        label = "wave-phase"
    )
    // Slow breathing so the hills feel like they react to the music.
    val breath by infinite.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2100), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "wave-breath"
    )
    val waveAmp by animateFloatAsState(
        targetValue = if (isPlaying && dragFraction == null) 1f else 0f,
        animationSpec = tween(400),
        label = "wave-amp"
    )

    Canvas(
        // 34dp-tall touch target. Taller than the line needs, on purpose: it opens
        // room BELOW the baseline so band styles (silk) can dip under the scrub line
        // without clipping. baseY stays 21dp from the top so every other style's
        // position and amplitude are unchanged — only the space below grows.
        modifier = modifier
            .height(34.dp)
            .pointerInput(Unit) {
                detectTapGestures { ofs -> onSeek((ofs.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { ofs ->
                        dragFraction = (ofs.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragFraction?.let(onSeek)
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null }
                ) { change, _ ->
                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
    ) {
        // One UI 9 style: played portion = thick baseline bar with translucent
        // overlapping hills rolling above it; remainder = thin flat track.
        val baseY = size.height - 13.dp.toPx()   // 21dp from top (unchanged); ~13dp of room below for silk to dip into
        val endX = shown * size.width
        val ampPx = (baseY - 3.dp.toPx()) * waveAmp * breath

        // Rolling hills over the played portion; amplitude tapers to zero before
        // the thumb (and after the start) so no hill is ever cropped vertically.
        drawWave(
            waveStyle,
            progressColor, ampPx, wavePhase, baseY, endX,
            vibrant = vibrant,
            waveColors = waveColors,
            taperStartPx = 10.dp.toPx(),
            taperEndPx = 44.dp.toPx(),
            consume = consume
        )
        // Played baseline (thick) + remaining track (thin). Skipped in consume mode
        // so the already-played (left) side stays invisible.
        if (endX > 0f && !consume) {
            drawLine(
                progressColor,
                Offset(0f, baseY), Offset(endX, baseY),
                strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round
            )
        }
        drawLine(
            trackColor,
            Offset(endX + 2.dp.toPx(), baseY), Offset(size.width, baseY),
            strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
        )
        // Thumb on the baseline; shape per the "Thumb shape" setting. Album-accent
        // colored with a soft glow halo in vibrant mode (One UI style) when circular.
        // The playhead is drawn LAST, so it always sits on top of the animation.
        val thumbColor = if (vibrant) accentColor else progressColor
        val c = Offset(endX, baseY)
        fun sz(w: Float, h: Float) = androidx.compose.ui.geometry.Size(w, h)
        when (thumbShape) {
            "none" -> Unit
            "bar" -> {
                val halfH = 9.dp.toPx(); val halfW = 2.dp.toPx()
                drawRoundRect(thumbColor, Offset(endX - halfW, baseY - halfH), sz(halfW * 2, halfH * 2), androidx.compose.ui.geometry.CornerRadius(halfW))
            }
            "ring" -> drawCircle(thumbColor, 6.5.dp.toPx(), c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
            "square" -> {
                val h = 5.5.dp.toPx()
                drawRoundRect(thumbColor, Offset(endX - h, baseY - h), sz(h * 2, h * 2), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            }
            "diamond" -> {
                val r = 7.dp.toPx()
                val p = androidx.compose.ui.graphics.Path().apply { moveTo(endX, baseY - r); lineTo(endX + r, baseY); lineTo(endX, baseY + r); lineTo(endX - r, baseY); close() }
                drawPath(p, thumbColor)
            }
            "triangle" -> {
                val r = 7.dp.toPx()
                val p = androidx.compose.ui.graphics.Path().apply { moveTo(endX, baseY - r); lineTo(endX - r * 0.9f, baseY + r * 0.7f); lineTo(endX + r * 0.9f, baseY + r * 0.7f); close() }
                drawPath(p, thumbColor)
            }
            "glow" -> {
                drawCircle(thumbColor.copy(alpha = 0.28f), 12.dp.toPx(), c)
                drawCircle(thumbColor.copy(alpha = 0.35f), 8.dp.toPx(), c)
                drawCircle(androidx.compose.ui.graphics.lerp(thumbColor, Color.White, 0.35f), 5.dp.toPx(), c)
            }
            else -> { // circle
                if (vibrant) drawCircle(thumbColor.copy(alpha = 0.35f), 11.dp.toPx(), c)
                drawCircle(thumbColor, 6.5.dp.toPx(), c)
            }
        }
    }
}

/**
 * One app-exposed custom action (shuffle, repeat, thumbs-up, etc.) rendered as an
 * icon button. Loads the icon lazily from the owning app's resources; if it fails
 * to load, nothing is rendered (per-composition no-op, not a crash).
 */
@Composable
private fun CustomActionButton(
    ctx: android.content.Context,
    pkg: String,
    action: PlaybackState.CustomAction,
    view: android.view.View,
    tint: Color
) {
    val bmp = remember(pkg, action.icon) {
        loadCustomActionIcon(ctx, pkg, action)?.toBitmap()?.asImageBitmap()
    }
    if (bmp != null) {
        IconButton(onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            MediaSessionBus.sendCustomAction(action)
        }) {
            Image(
                bitmap = bmp,
                contentDescription = action.name?.toString(),
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Album art that crossfades to the new bitmap over [durationMs] when the art
 * changes (album-art dedup means this fires only on a real change). A Compose
 * Crossfade is used rather than Coil's own transition, which skips animating
 * direct in-memory bitmaps. durationMs == 0 cuts instantly (crossfade off).
 */
@androidx.compose.runtime.Composable
fun CrossfadeArt(
    bitmap: android.graphics.Bitmap?,
    durationMs: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    androidx.compose.animation.Crossfade(
        targetState = bitmap,
        animationSpec = androidx.compose.animation.core.tween(durationMs.coerceAtLeast(0)),
        modifier = modifier,
        label = "album-art"
    ) { bmp ->
        AsyncImage(
            model = bmp ?: R.drawable.ic_album_placeholder,
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun openPlayingApp(
    ctx: android.content.Context,
    pkg: String?,
    onOpened: () -> Unit
) {
    val intent = pkg?.let { ctx.packageManager.getLaunchIntentForPackage(it) } ?: return
    runCatching {
        ctx.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        onOpened()
    }
}
