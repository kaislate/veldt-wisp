// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * PREMIUM whole-card / whole-pill effect layers.
 *
 * Each premium wave style is one coordinated effect across three surfaces:
 *  - the scrub bar (renderers in [WaveStyles.kt], routed by [drawWave]),
 *  - the expanded card (a BACKGROUND pass behind the text/controls plus a
 *    FOREGROUND pass over everything), and
 *  - the collapsed pill (a single scaled-down overlay pass).
 *
 * Rules honored here:
 *  - Seamless 20π loop: every temporal speed multiplier is a multiple of 0.1,
 *    particles use integer-cycles-per-20π (`frac(phase * (k / TWENTY_PI) + seed)`
 *    with integer k). Quantized `floor(phase * n)` clocks are used ONLY by the
 *    hard-glitch styles (interference / caldera), where the wrap-pop reads as
 *    an intentional glitch.
 *  - Legibility: foreground passes are translucent/additive and never place a
 *    large opaque shape over text or the scrub thumb; the heavy scenery lives
 *    in the background pass, under the content.
 */

private val TWENTY_PI = (20.0 * Math.PI).toFloat()

private val PREMIUM_STYLES = setOf(
    "interference", "cyberpunk", "caldera",
    "aurora", "prism", "warp", "embers", "eclipse", "monsoon", "pulse"
)

fun isPremiumStyle(style: String): Boolean = style in PREMIUM_STYLES

// ============================= dispatchers =============================

/** Foreground card pass — drawn OVER the whole card content. */
fun DrawScope.drawCardEffect(
    style: String, phase: Float, accent: Color, waveColors: List<Color>,
    art: ImageBitmap?, vibrant: Boolean, intensity: Float = 1f
) {
    val cols = palette(accent, vibrant, waveColors)
    val e = intensity.coerceIn(0f, 1f)
    when (style) {
        "interference" -> fgInterference(phase, art, e)
        "cyberpunk" -> fgCyberpunk(phase, e)
        "caldera" -> fgCaldera(phase, e)
        "aurora" -> fgAurora(phase, e)
        "prism" -> fgPrism(phase, cols, e)
        "warp" -> fgWarp(phase, cols, e)
        "embers" -> fgEmbers(phase, accent, e)
        "eclipse" -> fgEclipse(phase, cols, e)
        "monsoon" -> fgMonsoon(phase, e)
        "pulse" -> fgPulse(phase, cols, e)
    }
}

/** Background card pass — drawn between the glass gradient and the content. */
fun DrawScope.drawCardEffectBg(
    style: String, phase: Float, accent: Color, waveColors: List<Color>,
    art: ImageBitmap?, vibrant: Boolean, intensity: Float = 1f
) {
    val cols = palette(accent, vibrant, waveColors)
    val e = intensity.coerceIn(0f, 1f)
    when (style) {
        "cyberpunk" -> bgCyberpunk(phase, e)
        "caldera" -> bgCaldera(phase, e)
        "aurora" -> bgAurora(phase, cols, e)
        "prism" -> bgPrism(phase, e)
        "warp" -> bgWarp(phase, cols, e)
        "embers" -> bgEmbers(phase, accent, e)
        "eclipse" -> bgEclipse(phase, cols, e)
        "monsoon" -> bgMonsoon(phase, e)
        "pulse" -> bgPulse(phase, cols, e)
        // interference: no bg pass — its whole personality is the fg tear layer.
    }
}

/** Pill pass — a hint of the card's effect, scaled to a tiny wrap-content pill. */
fun DrawScope.drawPillEffect(
    style: String, phase: Float, accent: Color, waveColors: List<Color>,
    art: ImageBitmap?, vibrant: Boolean, intensity: Float = 1f
) {
    val cols = palette(accent, vibrant, waveColors)
    val e = intensity.coerceIn(0f, 1f)
    when (style) {
        "interference" -> pillInterference(phase, e)
        "cyberpunk" -> pillCyberpunk(phase, e)
        "caldera" -> pillCaldera(phase, e)
        "aurora" -> pillAurora(phase, cols, e)
        "prism" -> pillPrism(phase, e)
        "warp" -> pillWarp(phase, cols, e)
        "embers" -> pillEmbers(phase, accent, e)
        "eclipse" -> pillEclipse(phase, cols, e)
        "monsoon" -> pillMonsoon(phase, e)
        "pulse" -> pillPulse(phase, cols, e)
    }
}

// ===================== physical transforms (graphicsLayer) =====================

/**
 * Physical shake for the whole card, in px (call from a graphicsLayer block —
 * [Density] receiver gives dp→px). Quantized clocks: only glitch styles use it,
 * so the wrap-pop is in character.
 */
fun Density.cardFxOffset(style: String, phase: Float): Offset = when (style) {
    "interference" -> {
        val tq = floor(phase * 5f)
        if (hash(tq * 0.53f) > 0.82f)
            Offset((hash(tq) - 0.5f) * 7f.dp.toPx(), 0f)
        else Offset.Zero
    }
    "caldera" -> {
        val tq = floor(phase * 8f)
        if (hash(tq * 0.31f) > 0.86f)
            Offset((hash(tq) - 0.5f) * 3f.dp.toPx(), (hash(tq * 1.7f) - 0.5f) * 3f.dp.toPx())
        else Offset.Zero
    }
    else -> Offset.Zero
}

/** Beat-thump scale multiplier for the whole card (Pulse only). Seamless. */
fun cardFxScale(style: String, phase: Float): Float = when (style) {
    "pulse" -> 1f + 0.013f * exp(-5f * frac(phase * (36f / TWENTY_PI)))
    else -> 1f
}

// ============================= 1. Interference =============================
// Broadcast interference: the card IS the broken signal — scanlines, torn
// album-art slices smeared sideways, RGB edge ticks, hard white flashes,
// and a quantized horizontal jolt of the whole card.

private fun DrawScope.fgInterference(phase: Float, art: ImageBitmap?, e: Float) {
    val w = size.width; val h = size.height
    // CRT scanlines, whisper-quiet so text stays clean.
    var y = 0f
    val lh = 3.5f.dp.toPx()
    while (y < h) {
        drawRect(Color.Black.copy(alpha = 0.05f * e), Offset(0f, y), Size(w, 1f.dp.toPx()))
        y += lh
    }
    // Torn album-art slices smeared across the card (quantized glitch clock).
    val tq = floor(phase * 5f)
    if (art != null && art.width > 4 && art.height > 12 && hash(tq * 0.63f) > 0.45f) {
        for (s in 0..2) {
            if (hash(tq + s * 1.7f) < 0.4f) continue
            val sy = hash(tq * 1.3f + s) * (h * 0.92f)
            val sh = (4f + hash(tq * 2.9f + s) * 9f).dp.toPx()
            val srcY = (hash(tq * 0.7f + s) * (art.height * 0.9f)).toInt()
            val srcH = max(2, (art.height * 0.08f).toInt())
            val dx = (hash(tq * 3.7f + s) - 0.5f) * 44f.dp.toPx()
            drawImage(
                art,
                srcOffset = IntOffset(0, srcY.coerceIn(0, art.height - srcH)),
                srcSize = IntSize(art.width, srcH),
                dstOffset = IntOffset(dx.toInt(), sy.toInt()),
                dstSize = IntSize(w.toInt(), sh.toInt().coerceAtLeast(1)),
                alpha = (0.30f * e).coerceIn(0f, 1f),
                blendMode = BlendMode.Plus
            )
        }
    }
    // RGB channel ticks biting in from the edges.
    val chans = listOf(Color.Red, Color.Green, Color.Blue)
    val tq2 = floor(phase * 7f)
    if (hash(tq2 * 0.41f) > 0.7f) {
        for (b in 0..2) {
            val by = hash(tq2 + b * 2.3f) * h
            val bh = (2f + hash(tq2 * 1.9f + b) * 8f).dp.toPx()
            val bw = (6f + hash(tq2 * 3.1f + b) * 26f).dp.toPx()
            val left = hash(tq2 * 2.6f + b) > 0.5f
            drawRect(chans[b].copy(alpha = 0.35f * e), Offset(if (left) 0f else w - bw, by), Size(bw, bh), blendMode = BlendMode.Plus)
        }
    }
    // Full-card signal flash.
    val fc = floor(phase * 3f)
    if (hash(fc * 0.37f) > 0.9f) {
        drawRect(Color.White.copy(alpha = 0.07f * e), Offset.Zero, size, blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.pillInterference(phase: Float, e: Float) {
    val w = size.width; val h = size.height
    var y = 1f.dp.toPx()
    while (y < h) {
        drawRect(Color.Black.copy(alpha = 0.06f * e), Offset(0f, y), Size(w, 1f.dp.toPx()))
        y += 4f.dp.toPx()
    }
    val tq = floor(phase * 7f)
    if (hash(tq * 0.41f) > 0.78f) {
        val chans = listOf(Color.Red, Color.Green, Color.Blue)
        val b = (hash(tq * 1.3f) * 3f).toInt().coerceIn(0, 2)
        val by = hash(tq + b * 2.3f) * h
        val bw = (4f + hash(tq * 3.1f + b) * 12f).dp.toPx()
        val left = hash(tq * 2.6f + b) > 0.5f
        drawRect(chans[b].copy(alpha = 0.35f * e), Offset(if (left) 0f else w - bw, by), Size(bw, 2.5f.dp.toPx()), blendMode = BlendMode.Plus)
    }
    val fc = floor(phase * 3f)
    if (hash(fc * 0.37f) > 0.92f) drawRect(Color.White.copy(alpha = 0.08f * e), Offset.Zero, size, blendMode = BlendMode.Plus)
}

// ============================= 2. Cyberpunk =============================
// Night-city HUD: a perspective grid + horizon glow BEHIND the content, a
// cyan/magenta breathing neon frame around the card, and a scan sweep line
// gliding down over everything.

private val NEON_A = Color(0xFF00E5FF)
private val NEON_B = Color(0xFFFF2D95)

private fun DrawScope.bgCyberpunk(phase: Float, e: Float) {
    val w = size.width; val h = size.height
    val hy = h * 0.62f
    // Horizon glow rising from the bottom.
    drawRect(
        brush = Brush.verticalGradient(listOf(Color.Transparent, NEON_B.copy(alpha = 0.16f * e)), hy, h),
        topLeft = Offset(0f, hy), size = Size(w, h - hy), blendMode = BlendMode.Plus
    )
    // Verticals fanning out from a vanishing line at the horizon.
    for (i in -6..6) {
        val xTop = w / 2f + i * w * 0.10f
        val xBot = w / 2f + i * w * 0.28f
        drawLine(NEON_A.copy(alpha = 0.10f * e), Offset(xTop, hy), Offset(xBot, h), strokeWidth = 1f.dp.toPx(), blendMode = BlendMode.Plus)
    }
    // Scrolling horizontal rows racing toward the viewer.
    for (r in 0..4) {
        val t = frac(phase * 0.2f + r / 5f)
        val yy = hy + (h - hy) * t * t
        drawLine(NEON_B.copy(alpha = 0.16f * (1f - t) * e), Offset(0f, yy), Offset(w, yy), strokeWidth = 1f.dp.toPx(), blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.fgCyberpunk(phase: Float, e: Float) {
    val w = size.width; val h = size.height
    // Breathing neon frame, cyan<->magenta.
    val pulse = 0.5f + 0.5f * sin(phase * 1.0f)
    val frameCol = lerp(NEON_A, NEON_B, pulse)
    val inset = 1.2f.dp.toPx()
    drawRoundRect(
        color = frameCol.copy(alpha = (0.30f + 0.25f * pulse) * e),
        topLeft = Offset(inset, inset), size = Size(w - inset * 2, h - inset * 2),
        cornerRadius = CornerRadius(27f.dp.toPx()),
        style = Stroke(2f.dp.toPx()), blendMode = BlendMode.Plus
    )
    // Scan sweep gliding down the card.
    val sy = frac(phase * 0.2f) * h
    val band = 18f.dp.toPx()
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, NEON_A.copy(alpha = 0.10f * e), Color.Transparent),
            sy - band, sy + band
        ),
        topLeft = Offset(0f, sy - band), size = Size(w, band * 2), blendMode = BlendMode.Plus
    )
    drawLine(NEON_A.copy(alpha = 0.28f * e), Offset(0f, sy), Offset(w, sy), strokeWidth = 1.2f.dp.toPx(), blendMode = BlendMode.Plus)
}

private fun DrawScope.pillCyberpunk(phase: Float, e: Float) {
    val w = size.width; val h = size.height
    val pulse = 0.5f + 0.5f * sin(phase * 1.0f)
    val frameCol = lerp(NEON_A, NEON_B, pulse)
    val inset = 1f.dp.toPx()
    drawRoundRect(
        color = frameCol.copy(alpha = (0.30f + 0.25f * pulse) * e),
        topLeft = Offset(inset, inset), size = Size(w - inset * 2, h - inset * 2),
        cornerRadius = CornerRadius(23f.dp.toPx()),
        style = Stroke(1.5f.dp.toPx()), blendMode = BlendMode.Plus
    )
    val sx = frac(phase * 0.2f) * w
    drawLine(NEON_A.copy(alpha = 0.22f * e), Offset(sx, 0f), Offset(sx, h), strokeWidth = 1f.dp.toPx(), blendMode = BlendMode.Plus)
}

// ============================= 3. Caldera =============================
// The storm around the bolt (the user's beloved scrub-bar lightning stays):
// charged haze + thunder-cell flashes behind the content; electric arcs
// crawling the card's border, full-card strike bolts, drifting spark motes,
// and a rumble jolt when a strike lands.

private val ELECTRIC = Color(0xFF66E0FF)

private fun DrawScope.bgCaldera(phase: Float, e: Float) {
    val w = size.width; val h = size.height
    // Charged haze along the top edge.
    drawRect(
        brush = Brush.verticalGradient(listOf(ELECTRIC.copy(alpha = 0.06f * e), Color.Transparent), 0f, h * 0.4f),
        topLeft = Offset.Zero, size = Size(w, h * 0.4f), blendMode = BlendMode.Plus
    )
    // Thunder-cell flash: a soft cloud lighting up somewhere behind the content.
    val tq = floor(phase * 2.5f)
    if (hash(tq * 0.29f) > 0.8f) {
        val cx = hash(tq * 1.3f) * w
        val cy = hash(tq * 2.1f) * h * 0.5f
        val r = w * 0.45f
        drawCircle(
            brush = Brush.radialGradient(listOf(ELECTRIC.copy(alpha = 0.22f * e), Color.Transparent), Offset(cx, cy), r),
            radius = r, center = Offset(cx, cy), blendMode = BlendMode.Plus
        )
    }
}

/** Point + inward normal at distance [d] along a w×h rectangle's perimeter. */
private fun perimPoint(d: Float, w: Float, h: Float): Pair<Offset, Offset> {
    var t = d
    if (t < w) return Offset(t, 0f) to Offset(0f, 1f)
    t -= w
    if (t < h) return Offset(w, t) to Offset(-1f, 0f)
    t -= h
    if (t < w) return Offset(w - t, h) to Offset(0f, -1f)
    t -= w
    return Offset(0f, h - t) to Offset(1f, 0f)
}

private fun DrawScope.drawPerimeterArc(phase: Float, e: Float, count: Int, lenBase: Float) {
    val w = size.width; val h = size.height
    val perim = 2f * (w + h)
    val tq = floor(phase * 8f)
    for (a in 0 until count) {
        if (hash(tq * 0.47f + a * 1.9f) < 0.45f) continue
        val start = hash(tq * 1.1f + a * 3.7f) * perim
        val len = lenBase * (0.6f + hash(tq * 2.3f + a) * 0.9f)
        val step = 4f.dp.toPx()
        val p = Path(); var first = true
        var traveled = 0f
        while (traveled <= len) {
            val (pt, nrm) = perimPoint((start + traveled) % perim, w, h)
            val jitter = 2f.dp.toPx() + (hash((start + traveled) * 0.13f + tq) - 0.5f) * 6f.dp.toPx()
            val jp = Offset(pt.x + nrm.x * jitter, pt.y + nrm.y * jitter)
            if (first) { p.moveTo(jp.x, jp.y); first = false } else p.lineTo(jp.x, jp.y)
            traveled += step
        }
        drawPath(p, ELECTRIC.copy(alpha = 0.45f * e), style = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        drawPath(p, Color.White.copy(alpha = 0.5f * e), style = Stroke(1f.dp.toPx(), cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.fgCaldera(phase: Float, e: Float) {
    val w = size.width; val h = size.height
    // Electric arcs crawling along the card border.
    drawPerimeterArc(phase, e, count = 3, lenBase = 55f.dp.toPx())
    // Occasional full-card strike bolt from the top edge.
    val bq = floor(phase * 4f)
    if (hash(bq * 0.61f) > 0.8f) {
        val bx = (0.15f + 0.7f * hash(bq * 1.7f)) * w
        val p = Path(); p.moveTo(bx, 0f)
        var yy = 0f; var xx = bx
        while (yy < h * 0.72f) {
            yy += (8f + hash(bq + yy) * 14f).dp.toPx()
            xx += (hash(bq * 3.1f + yy) - 0.5f) * 18f.dp.toPx()
            p.lineTo(xx, yy)
        }
        drawPath(p, ELECTRIC.copy(alpha = 0.45f * e), style = Stroke(3f.dp.toPx(), cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        drawPath(p, Color.White.copy(alpha = 0.55f * e), style = Stroke(1.2f.dp.toPx(), cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        drawRect(ELECTRIC.copy(alpha = 0.07f * e), Offset.Zero, size, blendMode = BlendMode.Plus)
    }
    // Drifting spark motes rising through the card.
    for (i in 0 until 14) {
        val k = 6 + (hash(i * 2.3f) * 10f).toInt()
        val ti = frac(phase * (k / TWENTY_PI) + hash(i.toFloat()))
        val x = hash(i * 3.1f) * w + sin(ti * 9f + i) * 8f.dp.toPx()
        val y = h * (1f - ti)
        val a = (min(1f, ti * 5f) * (1f - ti) * 0.5f * e).coerceIn(0f, 1f)
        if (a <= 0.02f) continue
        drawCircle(lerp(ELECTRIC, Color.White, 0.4f).copy(alpha = a), radius = 1.3f.dp.toPx(), center = Offset(x, y), blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.pillCaldera(phase: Float, e: Float) {
    drawPerimeterArc(phase, e * 0.9f, count = 2, lenBase = 24f.dp.toPx())
    val w = size.width; val h = size.height
    for (i in 0 until 4) {
        val k = 8 + (hash(i * 2.3f) * 8f).toInt()
        val ti = frac(phase * (k / TWENTY_PI) + hash(i.toFloat()))
        val a = (min(1f, ti * 5f) * (1f - ti) * 0.5f * e).coerceIn(0f, 1f)
        if (a <= 0.02f) continue
        drawCircle(lerp(ELECTRIC, Color.White, 0.4f).copy(alpha = a), radius = 1f.dp.toPx(), center = Offset(hash(i * 3.1f) * w, h * (1f - ti)), blendMode = BlendMode.Plus)
    }
    val tq = floor(phase * 2.5f)
    if (hash(tq * 0.29f) > 0.88f) drawRect(ELECTRIC.copy(alpha = 0.10f * e), Offset.Zero, size, blendMode = BlendMode.Plus)
}

// ============================= 4. Aurora =============================
// Northern lights: three slow-waving light curtains hanging from the top of
// the card behind the content, twinkling stars, and a faint shimmer ray
// drifting across the foreground.

private val AURORA_HUES = listOf(Color(0xFF3DFFB0), Color(0xFF5AC8FF), Color(0xFFB98BFF))

private fun DrawScope.bgAurora(phase: Float, cols: List<Color>, e: Float) {
    val w = size.width; val h = size.height
    for (c in 0..2) {
        val col = lerp(AURORA_HUES[c], cols[c % cols.size], 0.3f)
        val amp = h * 0.10f
        val base = h * (0.14f + 0.11f * c)
        val depth = h * 0.42f
        fun topY(x: Float) =
            base + amp * sin(x / (w * 0.35f) * TWO_PI + phase * (0.2f + 0.1f * c) + c * 2.1f) +
                amp * 0.5f * sin(x / (w * 0.13f) * TWO_PI - phase * 0.3f + c)
        val p = Path()
        var x = 0f
        p.moveTo(0f, topY(0f))
        while (x <= w) { p.lineTo(x, topY(x)); x += 8f }
        p.lineTo(w, topY(w) + depth)
        x = w
        while (x >= 0f) { p.lineTo(x, topY(x) + depth); x -= 8f }
        p.close()
        drawPath(
            p,
            brush = Brush.verticalGradient(
                listOf(col.copy(alpha = 0.26f * e), col.copy(alpha = 0.10f * e), Color.Transparent),
                base - amp, base + depth
            ),
            blendMode = BlendMode.Plus
        )
    }
    // Twinkling stars in the sky above the curtains.
    for (i in 0 until 10) {
        val sx = hash(i * 1.7f) * w
        val sy = hash(i * 2.9f) * h * 0.35f
        val tw = 0.5f + 0.5f * sin(phase * 1.5f + hash(i.toFloat()) * TWO_PI)
        drawCircle(Color.White.copy(alpha = (0.10f + 0.30f * tw * tw) * e), radius = 1f.dp.toPx(), center = Offset(sx, sy), blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.fgAurora(phase: Float, e: Float) {
    val w = size.width; val h = size.height
    // Two soft light rays drifting across the whole card.
    for (r in 0..1) {
        val rx = frac(phase * 0.1f + r * 0.5f) * (w * 1.4f) - w * 0.2f
        val half = 44f.dp.toPx()
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.05f * e), Color.Transparent),
                rx - half, rx + half
            ),
            topLeft = Offset(rx - half, 0f), size = Size(half * 2, h), blendMode = BlendMode.Plus
        )
    }
}

private fun DrawScope.pillAurora(phase: Float, cols: List<Color>, e: Float) {
    val w = size.width; val h = size.height
    for (c in 0..1) {
        val col = lerp(AURORA_HUES[c], cols[c % cols.size], 0.3f)
        val amp = h * 0.10f
        val base = h * (0.16f + 0.16f * c)
        val depth = h * 0.5f
        fun topY(x: Float) = base + amp * sin(x / (w * 0.4f) * TWO_PI + phase * (0.2f + 0.1f * c) + c * 2.1f)
        val p = Path()
        var x = 0f
        p.moveTo(0f, topY(0f))
        while (x <= w) { p.lineTo(x, topY(x)); x += 6f }
        p.lineTo(w, topY(w) + depth); p.lineTo(0f, topY(0f) + depth)
        p.close()
        drawPath(
            p,
            brush = Brush.verticalGradient(listOf(col.copy(alpha = 0.24f * e), Color.Transparent), base - amp, base + depth),
            blendMode = BlendMode.Plus
        )
    }
    for (i in 0 until 3) {
        val tw = 0.5f + 0.5f * sin(phase * 1.5f + hash(i.toFloat()) * TWO_PI)
        drawCircle(Color.White.copy(alpha = 0.35f * tw * tw * e), radius = 0.9f.dp.toPx(), center = Offset(hash(i * 1.7f) * w, hash(i * 2.9f) * h * 0.4f), blendMode = BlendMode.Plus)
    }
}

// ============================= 5. Prism =============================
// Light through cut glass: five spectral beams slowly counter-rotating behind
// the content, an RGB-fringed frame, and four-point sparkle glints breathing
// over the card.

private val SPECTRUM = listOf(
    Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFB388FF)
)

private fun DrawScope.bgPrism(phase: Float, e: Float) {
    val w = size.width; val h = size.height
    val cx = w / 2f; val cy = h / 2f
    val half = 30f.dp.toPx()
    val degPerRad = (180.0 / Math.PI).toFloat()
    for (b in SPECTRUM.indices) {
        val dir = if (b % 2 == 0) 1f else -1f
        val ang = phase * 0.1f * dir * degPerRad + b * 72f
        rotate(ang, Offset(cx, cy)) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, SPECTRUM[b].copy(alpha = 0.10f * e), Color.Transparent),
                    cx - half, cx + half
                ),
                topLeft = Offset(cx - half, -h), size = Size(half * 2, h * 3f), blendMode = BlendMode.Plus
            )
        }
    }
}

private fun DrawScope.fgPrism(phase: Float, cols: List<Color>, e: Float) {
    val w = size.width; val h = size.height
    // Chromatic-fringed frame: R and B ghosts offset around the true edge.
    val inset = 1.2f.dp.toPx()
    val off = 1.2f.dp.toPx()
    val cr = CornerRadius(27f.dp.toPx())
    val stroke = Stroke(1.4f.dp.toPx())
    drawRoundRect(Color.Red.copy(alpha = 0.20f * e), Offset(inset + off, inset), Size(w - inset * 2, h - inset * 2), cr, style = stroke, blendMode = BlendMode.Plus)
    drawRoundRect(Color.Blue.copy(alpha = 0.20f * e), Offset(inset - off, inset), Size(w - inset * 2, h - inset * 2), cr, style = stroke, blendMode = BlendMode.Plus)
    drawRoundRect(Color.White.copy(alpha = 0.14f * e), Offset(inset, inset), Size(w - inset * 2, h - inset * 2), cr, style = stroke, blendMode = BlendMode.Plus)
    // Breathing four-point sparkles scattered over the card.
    for (i in 0 until 8) {
        val tw = 0.5f + 0.5f * sin(phase * 2f + hash(i.toFloat()) * TWO_PI)
        val x = hash(i * 1.7f) * w
        val y = hash(i * 2.9f) * h
        val a = (tw * tw * 0.45f * e).coerceIn(0f, 1f)
        if (a <= 0.03f) continue
        val r = (1.8f + 2.4f * tw).dp.toPx()
        drawLine(Color.White.copy(alpha = a), Offset(x - r, y), Offset(x + r, y), strokeWidth = 1f.dp.toPx(), blendMode = BlendMode.Plus)
        drawLine(Color.White.copy(alpha = a), Offset(x, y - r), Offset(x, y + r), strokeWidth = 1f.dp.toPx(), blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.pillPrism(phase: Float, e: Float) {
    val w = size.width; val h = size.height
    val inset = 1f.dp.toPx()
    val off = 1f.dp.toPx()
    val cr = CornerRadius(23f.dp.toPx())
    val stroke = Stroke(1.2f.dp.toPx())
    drawRoundRect(Color.Red.copy(alpha = 0.20f * e), Offset(inset + off, inset), Size(w - inset * 2, h - inset * 2), cr, style = stroke, blendMode = BlendMode.Plus)
    drawRoundRect(Color.Blue.copy(alpha = 0.20f * e), Offset(inset - off, inset), Size(w - inset * 2, h - inset * 2), cr, style = stroke, blendMode = BlendMode.Plus)
    for (i in 0 until 3) {
        val tw = 0.5f + 0.5f * sin(phase * 2f + hash(i.toFloat()) * TWO_PI)
        val a = (tw * tw * 0.5f * e).coerceIn(0f, 1f)
        if (a <= 0.03f) continue
        val x = hash(i * 1.7f) * w; val y = hash(i * 2.9f) * h
        val r = (1.2f + 1.6f * tw).dp.toPx()
        drawLine(Color.White.copy(alpha = a), Offset(x - r, y), Offset(x + r, y), strokeWidth = 1f.dp.toPx(), blendMode = BlendMode.Plus)
        drawLine(Color.White.copy(alpha = a), Offset(x, y - r), Offset(x, y + r), strokeWidth = 1f.dp.toPx(), blendMode = BlendMode.Plus)
    }
}

// ============================= 6. Warp =============================
// Hyperspace: a starfield streaking radially out of the card's center behind
// the content, with a handful of bright lens streaks crossing the foreground.

private fun DrawScope.drawStarfield(phase: Float, cols: List<Color>, e: Float, count: Int, alphaScale: Float, widthScale: Float) {
    val w = size.width; val h = size.height
    val cx = w * 0.5f; val cy = h * 0.45f
    val maxR = sqrt(cx * cx + cy * cy) * 1.05f
    for (i in 0 until count) {
        val k = 8 + (hash(i * 2.3f) * 18f).toInt()
        val ti = frac(phase * (k / TWENTY_PI) + hash(i.toFloat()))
        val ang = hash(i * 3.7f) * TWO_PI
        val r0 = ti * ti * maxR
        val r1 = r0 + (2f + ti * 14f).dp.toPx() * (k / 16f)
        val dx = cos(ang); val dy = sin(ang)
        val a = (min(1f, ti * 1.2f) * alphaScale * e).coerceIn(0f, 1f)
        if (a <= 0.02f || r0 > maxR) continue
        val col = lerp(cols[i % cols.size], Color.White, (0.5f + 0.5f * ti).coerceAtMost(1f))
        drawLine(
            col.copy(alpha = a),
            Offset(cx + dx * r0, cy + dy * r0), Offset(cx + dx * r1, cy + dy * r1),
            strokeWidth = (0.8f + ti * 1.6f).dp.toPx() * widthScale, cap = StrokeCap.Round, blendMode = BlendMode.Plus
        )
    }
    // Core glow at the vanishing point.
    val cg = 36f.dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.08f * e), Color.Transparent), Offset(cx, cy), cg),
        radius = cg, center = Offset(cx, cy), blendMode = BlendMode.Plus
    )
}

private fun DrawScope.bgWarp(phase: Float, cols: List<Color>, e: Float) =
    drawStarfield(phase, cols, e, count = 46, alphaScale = 0.5f, widthScale = 1f)

private fun DrawScope.fgWarp(phase: Float, cols: List<Color>, e: Float) =
    drawStarfield(phase, cols, e, count = 10, alphaScale = 0.22f, widthScale = 1.8f)

private fun DrawScope.pillWarp(phase: Float, cols: List<Color>, e: Float) =
    drawStarfield(phase, cols, e * 0.9f, count = 14, alphaScale = 0.4f, widthScale = 0.8f)

// ============================= 7. Embers =============================
// A dying campfire under the card: warm coal-glow at the bottom, embers
// spiralling up through the content, and slow bokeh fireflies wandering.

private fun DrawScope.emberPalette(accent: Color): Pair<Color, Color> =
    lerp(Color(0xFFFFB74D), accent, 0.25f) to lerp(Color(0xFFFF5722), accent, 0.25f)

private fun DrawScope.bgEmbers(phase: Float, accent: Color, e: Float) {
    val w = size.width; val h = size.height
    val (gold, _) = emberPalette(accent)
    val warm = lerp(gold, Color(0xFFFF7A33), 0.5f)
    val flick = 0.85f + 0.15f * sin(phase * 3f)
    drawRect(
        brush = Brush.verticalGradient(listOf(Color.Transparent, warm.copy(alpha = 0.20f * flick * e)), h * 0.5f, h),
        topLeft = Offset(0f, h * 0.5f), size = Size(w, h * 0.5f), blendMode = BlendMode.Plus
    )
    // Bokeh fireflies wandering slowly.
    for (i in 0 until 3) {
        val x = w * (0.2f + 0.6f * hash(i * 5.1f)) + sin(phase * 0.2f + i * 2.1f) * w * 0.14f
        val y = h * (0.25f + 0.5f * hash(i * 6.3f)) + cos(phase * 0.3f + i * 1.7f) * h * 0.16f
        val r = (10f + 5f * hash(i * 7.9f)).dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(listOf(gold.copy(alpha = 0.12f * e), Color.Transparent), Offset(x, y), r),
            radius = r, center = Offset(x, y), blendMode = BlendMode.Plus
        )
    }
}

private fun DrawScope.drawEmberRise(phase: Float, accent: Color, e: Float, count: Int, swayPx: Float) {
    val w = size.width; val h = size.height
    val (gold, red) = emberPalette(accent)
    for (i in 0 until count) {
        val k = 3 + (hash(i * 2.3f) * 6f).toInt()
        val ti = frac(phase * (k / TWENTY_PI) + hash(i.toFloat()))
        val x = hash(i * 1.7f) * w + sin(ti * 5f + i) * swayPx
        val y = h - ti * (h * 1.05f)
        val flick = 0.6f + 0.4f * sin(phase * 3f + i.toFloat())
        val a = (min(1f, ti * 6f) * (1f - ti) * flick * 0.8f * e).coerceIn(0f, 1f)
        if (a <= 0.02f) continue
        val col = lerp(gold, red, hash(i * 4.3f))
        drawCircle(col.copy(alpha = a * 0.35f), radius = 3.2f.dp.toPx(), center = Offset(x, y), blendMode = BlendMode.Plus)
        drawCircle(lerp(col, Color.White, 0.4f).copy(alpha = a), radius = 1.3f.dp.toPx(), center = Offset(x, y), blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.fgEmbers(phase: Float, accent: Color, e: Float) =
    drawEmberRise(phase, accent, e, count = 22, swayPx = 14f.dp.toPx())

private fun DrawScope.pillEmbers(phase: Float, accent: Color, e: Float) =
    drawEmberRise(phase, accent, e, count = 8, swayPx = 5f.dp.toPx())

// ============================= 8. Eclipse =============================
// A black sun with a blazing corona hanging in the card's upper right,
// rotating diffraction rays, an edge vignette, and a quiet lens-flare chain
// crossing the content.

private fun DrawScope.drawEclipseSun(phase: Float, cols: List<Color>, e: Float, cx: Float, cy: Float, R: Float) {
    val breath = 0.5f + 0.5f * sin(phase * 0.5f)
    val corona = lerp(cols[0], Color(0xFFFFE0B2), 0.5f)
    // Corona bloom.
    val cr = R * 3.2f
    drawCircle(
        brush = Brush.radialGradient(
            listOf(corona.copy(alpha = (0.30f + 0.14f * breath) * e), corona.copy(alpha = 0.10f * e), Color.Transparent),
            Offset(cx, cy), cr
        ),
        radius = cr, center = Offset(cx, cy), blendMode = BlendMode.Plus
    )
    // Rotating diffraction rays.
    for (i in 0 until 6) {
        val ang = phase * 0.1f + i * (TWO_PI / 6f)
        val r0 = R * 1.05f
        val r1 = R * (2.1f + 0.5f * breath)
        drawLine(
            corona.copy(alpha = 0.16f * e),
            Offset(cx + cos(ang) * r0, cy + sin(ang) * r0),
            Offset(cx + cos(ang) * r1, cy + sin(ang) * r1),
            strokeWidth = 1f.dp.toPx(), cap = StrokeCap.Round, blendMode = BlendMode.Plus
        )
    }
    // Blazing rim + the black moon itself.
    drawCircle(Color.White.copy(alpha = (0.45f + 0.3f * breath) * e), radius = R, center = Offset(cx, cy), style = Stroke(1.5f.dp.toPx()), blendMode = BlendMode.Plus)
    drawCircle(Color.Black.copy(alpha = 0.85f), radius = R * 0.94f, center = Offset(cx, cy))
}

private fun DrawScope.bgEclipse(phase: Float, cols: List<Color>, e: Float) {
    val w = size.width; val h = size.height
    drawEclipseSun(phase, cols, e, cx = w * 0.80f, cy = h * 0.28f, R = 22f.dp.toPx())
    // Soft vignette so the card reads as a darkened sky.
    drawRect(
        brush = Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)),
            Offset(w / 2f, h / 2f), max(w, h) * 0.72f
        ),
        topLeft = Offset.Zero, size = size
    )
}

private fun DrawScope.fgEclipse(phase: Float, cols: List<Color>, e: Float) {
    val w = size.width; val h = size.height
    val sun = Offset(w * 0.80f, h * 0.28f)
    val ctr = Offset(w / 2f, h / 2f)
    val dir = Offset(ctr.x - sun.x, ctr.y - sun.y)
    val breath = 0.5f + 0.5f * sin(phase * 0.5f)
    // Lens-flare chain along the sun→center axis.
    val stops = listOf(0.55f, 1.0f, 1.45f, 1.9f)
    val radii = listOf(3f, 5f, 2.5f, 7f)
    for (i in stops.indices) {
        val p = Offset(sun.x + dir.x * stops[i], sun.y + dir.y * stops[i])
        val col = lerp(cols[i % cols.size], Color.White, 0.5f)
        drawCircle(col.copy(alpha = (0.10f + 0.06f * breath) * e), radius = radii[i].dp.toPx(), center = p, blendMode = BlendMode.Plus)
    }
    // Anamorphic streak through the sun.
    val sl = w * 0.30f
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = (0.10f + 0.08f * breath) * e), Color.Transparent),
            sun.x - sl, sun.x + sl
        ),
        topLeft = Offset(sun.x - sl, sun.y - 0.8f.dp.toPx()), size = Size(sl * 2, 1.6f.dp.toPx()), blendMode = BlendMode.Plus
    )
}

private fun DrawScope.pillEclipse(phase: Float, cols: List<Color>, e: Float) {
    val w = size.width; val h = size.height
    drawEclipseSun(phase, cols, e * 0.9f, cx = w - h * 0.55f, cy = h * 0.42f, R = h * 0.20f)
}

// ============================= 9. Monsoon =============================
// Blade-Runner rain: neon haze + wet-street reflection smears behind the
// content, sheets of slanted rain falling over the whole card, and splash
// ripples on the bottom edge.

private val RAIN_CYAN = Color(0xFF7FE7FF)
private val RAIN_MAGENTA = Color(0xFFFF6EC7)

private fun DrawScope.bgMonsoon(phase: Float, e: Float) {
    val w = size.width; val h = size.height
    // City-glow haze pooling at the bottom.
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, RAIN_CYAN.copy(alpha = 0.07f * e), RAIN_MAGENTA.copy(alpha = 0.12f * e)),
            h * 0.4f, h
        ),
        topLeft = Offset(0f, h * 0.4f), size = Size(w, h * 0.6f), blendMode = BlendMode.Plus
    )
    // Flickering neon reflection smears on the wet floor.
    for (i in 0 until 6) {
        val x = hash(i * 3.3f) * w
        val col = if (i % 2 == 0) RAIN_CYAN else RAIN_MAGENTA
        val fl = 0.6f + 0.4f * sin(phase * 2f + i * 2.4f)
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, col.copy(alpha = 0.16f * fl * e)), h * 0.72f, h),
            topLeft = Offset(x - 2f.dp.toPx(), h * 0.72f), size = Size(4f.dp.toPx(), h * 0.28f), blendMode = BlendMode.Plus
        )
    }
}

private fun DrawScope.drawRainfall(phase: Float, e: Float, count: Int, splashes: Int) {
    val w = size.width; val h = size.height
    val slant = 0.18f
    for (i in 0 until count) {
        val k = 16 + (hash(i * 2.3f) * 22f).toInt()
        val ti = frac(phase * (k / TWENTY_PI) + hash(i.toFloat()))
        val x0 = hash(i * 1.7f) * (w * 1.2f) - w * 0.1f
        val y = ti * (h + 30f.dp.toPx()) - 20f.dp.toPx()
        val len = (8f + hash(i * 4.1f) * 12f).dp.toPx()
        val x = x0 + slant * y
        val a = (0.18f * e * (0.5f + 0.5f * hash(i * 5.9f))).coerceIn(0f, 1f)
        if (a <= 0.02f) continue
        val col = lerp(if (i % 2 == 0) RAIN_CYAN else RAIN_MAGENTA, Color.White, 0.5f)
        drawLine(col.copy(alpha = a), Offset(x - slant * len, y - len), Offset(x, y), strokeWidth = 1f.dp.toPx(), blendMode = BlendMode.Plus)
    }
    // Splash ripples on the bottom edge.
    for (i in 0 until splashes) {
        val k = 10 + (hash(i * 7.7f) * 10f).toInt()
        val ti = frac(phase * (k / TWENTY_PI) + hash(i * 0.37f))
        val x = hash(i * 2.9f) * w
        val rw = ti * 12f.dp.toPx(); val rh = rw * 0.35f
        val a = ((1f - ti) * 0.28f * e).coerceIn(0f, 1f)
        if (a <= 0.02f || rw < 1f) continue
        drawOval(
            RAIN_CYAN.copy(alpha = a),
            topLeft = Offset(x - rw, h - 3f.dp.toPx() - rh), size = Size(rw * 2, rh * 2),
            style = Stroke(1f.dp.toPx()), blendMode = BlendMode.Plus
        )
    }
}

private fun DrawScope.fgMonsoon(phase: Float, e: Float) = drawRainfall(phase, e, count = 34, splashes = 6)

private fun DrawScope.pillMonsoon(phase: Float, e: Float) = drawRainfall(phase, e, count = 10, splashes = 2)

// ============================= 10. Pulse =============================
// The drop: the whole card THUMPS on the beat (graphicsLayer scale), a glow
// detonates from the center behind the content, and chromatic shockwave
// rings blast outward over everything — synced to the same 36-beats-per-loop
// clock as the scrub bar's rings.

private fun DrawScope.bgPulse(phase: Float, cols: List<Color>, e: Float) {
    val w = size.width; val h = size.height
    val env = exp(-4f * frac(phase * (36f / TWENTY_PI)))
    val glow = lerp(cols[0], Color.White, 0.25f)
    val r = w * 0.5f
    drawCircle(
        brush = Brush.radialGradient(listOf(glow.copy(alpha = (0.20f * env * e).coerceIn(0f, 1f)), Color.Transparent), Offset(w / 2f, h / 2f), r),
        radius = r, center = Offset(w / 2f, h / 2f), blendMode = BlendMode.Plus
    )
}

private fun DrawScope.drawShockRings(phase: Float, cols: List<Color>, e: Float, alphaScale: Float) {
    val w = size.width; val h = size.height
    val ctr = Offset(w / 2f, h / 2f)
    val maxR = max(w, h) * 0.75f
    val c1 = lerp(cols[0], Color.White, 0.4f)
    val c2 = lerp(cols[1 % cols.size], Color.White, 0.2f)
    for (j in 0 until 3) {
        val tr = frac(phase * (36f / TWENTY_PI) + j / 3f)
        val r = tr * maxR
        val a = ((1f - tr) * (1f - tr) * alphaScale * e).coerceIn(0f, 1f)
        if (a <= 0.02f || r <= 1f) continue
        drawCircle(c1.copy(alpha = a), radius = r, center = ctr, style = Stroke((2.2f * (1f - tr) + 0.8f).dp.toPx()), blendMode = BlendMode.Plus)
        drawCircle(c2.copy(alpha = a * 0.6f), radius = r + 2.5f.dp.toPx(), center = ctr, style = Stroke(1f.dp.toPx()), blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.fgPulse(phase: Float, cols: List<Color>, e: Float) = drawShockRings(phase, cols, e, alphaScale = 0.38f)

private fun DrawScope.pillPulse(phase: Float, cols: List<Color>, e: Float) {
    drawShockRings(phase, cols, e, alphaScale = 0.45f)
    val env = exp(-4f * frac(phase * (36f / TWENTY_PI)))
    val w = size.width; val h = size.height
    val glow = lerp(cols[0], Color.White, 0.25f)
    drawCircle(
        brush = Brush.radialGradient(listOf(glow.copy(alpha = (0.22f * env * e).coerceIn(0f, 1f)), Color.Transparent), Offset(w / 2f, h / 2f), h),
        radius = h, center = Offset(w / 2f, h / 2f), blendMode = BlendMode.Plus
    )
}
