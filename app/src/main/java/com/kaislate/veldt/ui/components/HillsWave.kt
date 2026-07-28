package com.kaislate.veldt.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * One UI 9-style "overlapping hills": several translucent filled hill layers
 * drifting at different speeds, rising from a baseline. Used by the expanded
 * panel's scrub bar (played portion) and, in miniature, inside the pill.
 */
private class HillLayer(
    val lenDp: Float,     // wavelength
    val drift: Float,     // slow leftward spatial drift (multiples of 0.1 for seamless 20π loop)
    val modSpeed: Float,  // in-place height pulsing rate (multiples of 0.1)
    val modOffset: Float, // pulsing phase offset
    val alpha: Float,     // layer translucency
    val weight: Float,    // relative height
    val offset: Float     // spatial phase offset so layers don't align
)

// Spectrograph feel: heights pulse in place (modSpeed) while the pattern only
// drifts left slowly (drift).
private val LAYERS = listOf(
    HillLayer(96f, 0.3f, 1.3f, 0.0f, 0.30f, 1.00f, 0.0f),
    HillLayer(58f, 0.6f, 2.9f, 1.8f, 0.38f, 0.66f, 2.1f),
    HillLayer(36f, 0.8f, 4.1f, 3.9f, 0.55f, 0.45f, 4.2f),
)

/** Smoothstep 0..1 over [0, ramp]; 1 when ramp <= 0. */
private fun ramp(dist: Float, ramp: Float): Float {
    if (ramp <= 0f) return 1f
    val t = (dist / ramp).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

// Fallback gradients when the album art yields too few colorful swatches
private val VIBRANT_FALLBACK = listOf(
    listOf(Color(0xFFFF5FA2), Color(0xFF9C6BFF)), // pink -> purple
    listOf(Color(0xFFFF9A3D), Color(0xFFFFE05A)), // orange -> yellow
    listOf(Color(0xFF41D8F0), Color(0xFFE05AF0)), // cyan -> magenta
)

/** Per-layer gradients from the album art's vibrant swatches (fallback if < 2). */
private fun layerGradients(waveColors: List<Color>): List<List<Color>> =
    if (waveColors.size < 2) VIBRANT_FALLBACK
    else List(LAYERS.size) { i ->
        listOf(waveColors[i % waveColors.size], waveColors[(i + 1) % waveColors.size])
    }

fun DrawScope.drawHills(
    color: Color,
    ampPx: Float,
    phase: Float,
    baseY: Float,
    width: Float,
    vibrant: Boolean = false,
    waveColors: List<Color> = emptyList(),
    taperStartPx: Float = 0f,
    taperEndPx: Float = 0f
) {
    if (ampPx <= 0.5f || width <= 0f) return
    val twoPi = (2f * Math.PI).toFloat()
    val gradients = if (vibrant) layerGradients(waveColors) else emptyList()
    LAYERS.forEachIndexed { i, l ->
        val lenPx = l.lenDp.dp.toPx()
        // Height of this whole layer pulses in place (spectrograph feel)
        val pulse = 0.55f + 0.45f * sin(phase * l.modSpeed + l.modOffset)
        val path = Path()
        path.moveTo(0f, baseY)
        var x = 0f
        while (x <= width) {
            val s = 0.5f + 0.5f * sin((x / lenPx) * twoPi + phase * l.drift + l.offset)
            // Taper to zero at both ends so nothing is ever cropped vertically
            val window = ramp(x, taperStartPx) * ramp(width - x, taperEndPx)
            path.lineTo(x, baseY - ampPx * l.weight * pulse * window * s * s)
            x += 4f
        }
        path.lineTo(width, baseY)
        path.close()
        if (vibrant) {
            drawPath(
                path,
                Brush.horizontalGradient(gradients[i % gradients.size], 0f, width),
                alpha = (l.alpha + 0.30f).coerceAtMost(0.9f)
            )
        } else {
            drawPath(path, color.copy(alpha = color.alpha * l.alpha))
        }
    }
}

/** Standalone animated hills (the pill's "now playing" indicator). */
@Composable
fun HillsWave(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    vibrant: Boolean = false,
    waveColors: List<Color> = emptyList(),
    waveStyle: String = "hills"
) {
    val infinite = rememberInfiniteTransition(label = "hills")
    // 10 full cycles per loop: layer speed multipliers (1.0/1.7/2.3) all land on a
    // whole number of periods at 20π, so the wrap is seamless — no visible restart.
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (20f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing)),
        label = "hills-phase"
    )
    val breath by infinite.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2100), repeatMode = RepeatMode.Reverse),
        label = "hills-breath"
    )
    val amp by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.15f,
        animationSpec = tween(400),
        label = "hills-amp"
    )
    Canvas(modifier) {
        drawWave(
            style = waveStyle,
            color = color,
            ampPx = size.height * 0.85f * amp * breath,
            phase = phase,
            baseY = size.height,
            width = size.width,
            vibrant = vibrant,
            waveColors = waveColors,
            taperStartPx = size.width * 0.08f,
            taperEndPx = size.width * 0.08f
        )
    }
}
