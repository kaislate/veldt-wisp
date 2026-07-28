// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.ui.island

import android.media.MediaMetadata
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kaislate.veldt.data.media.MediaSessionBus
import com.kaislate.veldt.overlay.ColorExtractor
import com.kaislate.veldt.overlay.DominantColors
import com.kaislate.veldt.ui.components.HillsWave
import com.kaislate.veldt.ui.components.drawPillEffect
import com.kaislate.veldt.ui.components.isPremiumStyle
import com.kaislate.veldt.util.IslandPosition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Content of the dedicated PANEL window used on devices without a usable
 * touchable-region API (Android 10–12, 15+). The panel is its own entity: it
 * fades/scales in from the pill's anchor and out again — no attempt to
 * impersonate the pill (two windows can't be frame-locked, so entity-morph
 * attempts always leaked blinks).
 */
@Composable
fun PanelRoot(
    visible: Boolean,
    onCollapse: () -> Unit,
    vibrant: Boolean = false,
    position: IslandPosition = IslandPosition.TOP_CENTER,
    thumbShape: String = "circle",
    waveColorMode: String = "auto",
    panelWidthDp: Int = 400,
    crossfadeMs: Int = 450,
    waveStyle: String = "hills",
    consume: Boolean = false
) {
    val morphOrigin = TransformOrigin(position.horizontalBias, if (position.isBottom) 1f else 0f)
    Box(Modifier.fillMaxSize(), contentAlignment = position.alignment) {
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)) + scaleIn(
                spring(dampingRatio = 0.85f, stiffness = 380f),
                initialScale = 0.85f,
                transformOrigin = morphOrigin
            ),
            exit = fadeOut(tween(160)) + scaleOut(
                tween(200), targetScale = 0.85f,
                transformOrigin = morphOrigin
            )
        ) {
            val edgePadding =
                if (position.isBottom) Modifier.padding(bottom = 4.dp)
                else Modifier.padding(top = 4.dp)
            Box(Modifier.width(panelWidthDp.dp).then(edgePadding)) {
                MusicPopUp(
                    onSwipeUpClose = onCollapse,
                    vibrant = vibrant,
                    thumbShape = thumbShape,
                    waveColorMode = waveColorMode,
                    crossfadeMs = crossfadeMs,
                    waveStyle = waveStyle,
                    consume = consume
                )
            }
        }
    }
}

/**
 * Root of the overlay window: morphs between the pill and the expanded media
 * panel IN PLACE (anchored top-center), so the pill visually expands into the
 * panel instead of launching a separate activity (whose new-task animation the
 * window manager animates from the bottom and apps cannot override).
 */
@Composable
fun IslandRoot(
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onBoundsChanged: (androidx.compose.ui.geometry.Rect) -> Unit,
    onStashSwipe: () -> Unit = {},
    vibrant: Boolean = false,
    fixedWindow: Boolean = true,
    position: IslandPosition = IslandPosition.TOP_CENTER,
    thumbShape: String = "circle",
    waveColorMode: String = "auto",
    pillTextWidthDp: Int = 128,
    panelWidthDp: Int = 400,
    crossfadeMs: Int = 450,
    showPillControls: Boolean = false,
    pillControlSet: String = "play-next",
    pillControlPosition: String = "right",
    waveStyle: String = "hills",
    consume: Boolean = false
) {
    // On API 33+ the host window is a FIXED panel-sized rectangle; content anchors
    // top-center and the reported bounds drive the window's touchable region, so
    // transparent areas pass touches through to whatever is beneath. Below API 33
    // the window itself is WRAP_CONTENT (resized on expand/collapse instead), so
    // the root Box must NOT fill the (already content-sized) window — filling it
    // there would just re-create the touch dead-zone the legacy window mode exists
    // to avoid.
    val springSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)
    Box(
        modifier = (if (fixedWindow) Modifier.fillMaxSize() else Modifier)
            // While expanded, a tap on the window's transparent margin collapses
            // the panel (taps fully outside the window arrive as ACTION_OUTSIDE).
            .pointerInput(expanded) {
                if (expanded) {
                    // The no-op long-press is load-bearing: supplying it makes
                    // detectTapGestures arm its long-press timeout, so holding the margin
                    // and releasing does NOT also collapse the panel.
                    detectTapGestures(onLongPress = {}, onTap = { onCollapse() })
                }
            },
        contentAlignment = position.alignment
    ) {
        Box(
            Modifier.onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) }
        ) {
            val morphOrigin = TransformOrigin(position.horizontalBias, if (position.isBottom) 1f else 0f)
            AnimatedContent(
                targetState = expanded,
                contentAlignment = position.alignment,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(
                        springSpec, initialScale = 0.8f,
                        transformOrigin = morphOrigin
                    )).togetherWith(
                        fadeOut(tween(140)) + scaleOut(
                            springSpec, targetScale = 0.8f,
                            transformOrigin = morphOrigin
                        )
                    ).using(SizeTransform(clip = false) { _, _ ->
                        spring(dampingRatio = 0.9f, stiffness = 380f)
                    })
                },
                label = "island-expand"
            ) { isExpanded ->
                if (isExpanded) {
                    val edgePadding =
                        if (position.isBottom) Modifier.padding(bottom = 4.dp)
                        else Modifier.padding(top = 4.dp)
                    Box(Modifier.width(panelWidthDp.dp).then(edgePadding)) {
                        MusicPopUp(
                            onSwipeUpClose = onCollapse,
                            vibrant = vibrant,
                            thumbShape = thumbShape,
                            waveColorMode = waveColorMode,
                            crossfadeMs = crossfadeMs,
                            waveStyle = waveStyle,
                            consume = consume
                        )
                    }
                } else {
                    IslandOverlay(
                        onShortTap = onExpand,
                        onLongPress = { },
                        onStashSwipe = onStashSwipe,
                        stashDirectionUp = !position.isBottom,
                        vibrant = vibrant,
                        waveColorMode = waveColorMode,
                        textWidthDp = pillTextWidthDp,
                        crossfadeMs = crossfadeMs,
                        showPillControls = showPillControls,
                        pillControlSet = pillControlSet,
                        pillControlPosition = pillControlPosition,
                        waveStyle = waveStyle
                    )
                }
            }
        }
    }
}

/**
 * The pill's touch surface. It draws nothing of its own — [PillContent] is the pill — and
 * exists only to own the two gestures the collapsed pill answers to.
 *
 * The whole strip is one target. There is no ripple: a translucent pill floating over
 * somebody else's app has no surface for a ripple to belong to, and with the entire pill
 * being the button there is nothing for the ripple to disambiguate. Both handlers fire a
 * haptic first, so the pill confirms the touch even where the visual response is a window
 * animation that takes a frame or two to start.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IslandOverlay(
    onShortTap: () -> Unit,
    onLongPress: () -> Unit,
    onStashSwipe: () -> Unit = {},
    stashDirectionUp: Boolean = true,
    leftMarginDp: Int = 2,
    rightMarginDp: Int = 2,
    vibrant: Boolean = false,
    waveColorMode: String = "auto",
    textWidthDp: Int = 128,
    crossfadeMs: Int = 450,
    showPillControls: Boolean = false,
    pillControlSet: String = "play-next",
    pillControlPosition: String = "right",
    waveStyle: String = "hills",
) {
    val view = LocalView.current
    // Unused on purpose: combinedClickable insists on an interaction source, and this one
    // exists so that nothing observes it and no indication is ever drawn.
    val interactions = remember { MutableInteractionSource() }

    PillContent(
        vibrant = vibrant,
        waveColorMode = waveColorMode,
        textWidthDp = textWidthDp,
        leftMarginDp = leftMarginDp,
        rightMarginDp = rightMarginDp,
        crossfadeMs = crossfadeMs,
        showControls = showPillControls,
        controlSet = pillControlSet,
        controlPosition = pillControlPosition,
        waveStyle = waveStyle,
        modifier = Modifier
            .combinedClickable(
                interactionSource = interactions,
                indication = null,
                role = Role.Button,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onShortTap()
                },
                onLongClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onLongPress()
                },
            )
            // Keyed on the direction so the recogniser re-arms when the user moves the pill
            // between a top and a bottom anchor; the threshold is per-event, so a flick
            // stashes and a slow deliberate drag does not. Neither gesture consumes the
            // event — arbitration between the tap and the drag is the framework's job.
            .pointerInput(stashDirectionUp) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (PillLayout.isStashDrag(dragAmount, stashDirectionUp)) onStashSwipe()
                }
            },
    )
}

/**
 * The pill itself: the artwork, the title and artist, the wave along its bottom edge, and —
 * when the user has asked for them — the transport buttons.
 *
 * Rendered both by [IslandOverlay] in the real overlay window and, scaled down, by the
 * settings screen's device preview, which is why it takes a [modifier] and owns no gestures.
 *
 * Its size is entirely content-driven. The overlay window is gravity-anchored by
 * `OverlayWindowManager`, so a pill that grows wider re-centres itself and one that grows
 * taller extends from whichever edge it is anchored to. The absence of any centring or
 * offset logic here is deliberate, not an omission.
 */
@Composable
fun PillContent(
    vibrant: Boolean = false,
    waveColorMode: String = "auto",
    textWidthDp: Int = 128,
    leftMarginDp: Int = 2,
    rightMarginDp: Int = 2,
    crossfadeMs: Int = 450,
    showControls: Boolean = false,
    controlSet: String = "play-next",
    controlPosition: String = "right",
    waveStyle: String = "hills",
    modifier: Modifier = Modifier,
) {
    val albumArt by MediaSessionBus.albumArt.collectAsState(initial = null)
    val playbackState by MediaSessionBus.playbackState.collectAsState(initial = null)
    val metadata by MediaSessionBus.metadata.collectAsState(initial = null)

    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
    val isPlaying = PillLayout.isPlaying(playbackState)

    // The neutral palette until the artwork has been analysed, so the very first frame is
    // already opaque and legible rather than a flash of transparency.
    var dom by remember { mutableStateOf(ColorExtractor.NEUTRAL) }
    LaunchedEffect(albumArt) { dom = ColorExtractor.extract(albumArt) }

    val accent = PopUpColors.effectiveAccent(waveColorMode, dom)

    val surface by animateColorAsState(
        targetValue = dom.bg,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "pill-surface",
    )
    val waveAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.3f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pill-wave-fade",
    )

    // An infinite transition recomposes for as long as it exists, so the standard styles get
    // no transition at all rather than one whose output is merely ignored.
    val premium = isPremiumStyle(waveStyle)
    val phase: State<Float> = if (premium) {
        val clock = rememberInfiniteTransition(label = "pill-effect")
        clock.animateFloat(
            initialValue = 0f,
            targetValue = (20f * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing)),
            label = "pill-effect-phase",
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    val effectArt = remember(albumArt) { albumArt?.asImageBitmap() }

    val artAndText: @Composable () -> Unit = {
        CrossfadeArt(
            bitmap = albumArt,
            durationMs = crossfadeMs,
            modifier = Modifier.size(30.dp).clip(CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.widthIn(max = textWidthDp.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The marquee is unconditional. Compose only animates one that actually
            // overflows, so a short title sits still without being asked to.
            Text(
                text = title,
                color = dom.onBg,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
            )
            Text(
                text = artist,
                color = dom.onBg.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
            )
        }
    }

    val transport: @Composable () -> Unit = {
        PillControls(controlSet = controlSet, isPlaying = isPlaying, tint = dom.onBg)
    }

    val rowPad = Modifier.padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 12.dp)

    Box(
        modifier
            .width(IntrinsicSize.Max)
            .shadow(8.dp, RoundedCornerShape(24.dp), clip = false)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(surface.copy(alpha = 0.96f), surface.copy(alpha = 0.90f))
                )
            )
            .drawWithContent {
                drawContent()
                // Topmost layer, over background, wave, artwork and text alike — the premium
                // styles are an effect applied to the whole pill, not a decoration behind it.
                if (premium) {
                    drawPillEffect(
                        style = waveStyle,
                        phase = phase.value,
                        accent = accent,
                        waveColors = dom.waveColors,
                        art = effectArt,
                        vibrant = vibrant,
                    )
                }
            }
    ) {
        HillsWave(
            isPlaying = isPlaying,
            color = accent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(14.dp)
                .alpha(waveAlpha),
            vibrant = vibrant,
            waveColors = dom.waveColors,
            waveStyle = waveStyle,
        )

        when (PillLayout.arrangementFor(showControls, controlPosition)) {
            PillArrangement.TEXT_ONLY ->
                Row(rowPad, verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(leftMarginDp.dp))
                    artAndText()
                    Spacer(Modifier.width(rightMarginDp.dp))
                }

            PillArrangement.CONTROLS_BELOW ->
                Column(
                    Modifier.padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(leftMarginDp.dp))
                        artAndText()
                        Spacer(Modifier.width(rightMarginDp.dp))
                    }
                    transport()
                }

            PillArrangement.CONTROLS_LEFT ->
                Row(rowPad, verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(leftMarginDp.dp))
                    transport()
                    Spacer(Modifier.width(4.dp))
                    artAndText()
                    Spacer(Modifier.width(rightMarginDp.dp))
                }

            PillArrangement.CONTROLS_RIGHT ->
                Row(rowPad, verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(leftMarginDp.dp))
                    artAndText()
                    Spacer(Modifier.width(4.dp))
                    transport()
                    Spacer(Modifier.width(rightMarginDp.dp))
                }
        }
    }
}

/**
 * Always-on transport controls drawn on the pill. Each button owns its click, so
 * tapping a control fires playback (not the pill's tap-to-expand or swipe-to-stash).
 */
@Composable
private fun PillControls(controlSet: String, isPlaying: Boolean, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Which buttons, and in what order, is a value question — it lives in PillLayout
        // where it is assertable without a device.
        PillLayout.buttonsFor(controlSet).forEach { button ->
            when (button) {
                PillButton.PREVIOUS ->
                    PillControlButton(Icons.Filled.SkipPrevious, "Previous", tint) { MediaSessionBus.previous() }
                PillButton.PLAY_PAUSE ->
                    PillControlButton(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (isPlaying) "Pause" else "Play",
                        tint
                    ) { MediaSessionBus.togglePlayPause() }
                PillButton.NEXT ->
                    PillControlButton(Icons.Filled.SkipNext, "Next", tint) { MediaSessionBus.next() }
            }
        }
    }
}

@Composable
private fun PillControlButton(icon: ImageVector, desc: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(20.dp))
    }
}
