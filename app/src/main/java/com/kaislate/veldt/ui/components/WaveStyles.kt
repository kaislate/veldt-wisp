package com.kaislate.veldt.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Eight scrub-bar motion styles ported from the "Veldt Wisp" JS canvas lab
 * (R.mercury / R.silk / R.shallows / R.sift / R.choir / R.interference /
 * R.caldera / R.loom). Same call contract as [drawHills]: draw the played
 * portion over x in [0, width] with the playhead at x = width, rising above
 * [baseY], and always taper to flat as x approaches either end via
 * [taperStartPx]/[taperEndPx] so nothing is ever cropped at the thumb.
 *
 * Porting notes (apply globally, not repeated per-function):
 * - The JS renderers distinguish the full canvas width `w` from the played
 *   width `playW`. These functions only receive `width` (== playW), so all
 *   spatial-frequency terms are computed relative to `width` instead of a
 *   wider, fixed canvas — wavelengths scale gently with progress instead of
 *   staying pinned to the untouched track. Visually equivalent for a bar
 *   this size.
 * - JS's continuous 0..1 "energy" (`e`, tied to play/pause) isn't passed to
 *   these functions directly — only the already-amplitude-gated [ampPx] is.
 *   Where a renderer needs an energy-like scalar purely for alpha/rate
 *   modulation (not vertical amplitude), [energyOf] derives one from
 *   ampPx/baseY. Vertical *amplitude* uses ampPx directly, mirroring how
 *   JS's own local `amp` variables were already `f(e)` before being handed
 *   to the drawing math.
 */

internal val TWO_PI = (2.0 * Math.PI).toFloat()

/** Smoothstep 0..1 over [0, 1]. */
internal fun sstep(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/** Taper window: 0 near both ends (within taperStartPx/taperEndPx), 1 in the middle. */
internal fun window(x: Float, width: Float, taperStartPx: Float, taperEndPx: Float): Float {
    val a = if (taperStartPx <= 0f) 1f else sstep(x / taperStartPx)
    val b = if (taperEndPx <= 0f) 1f else sstep((width - x) / taperEndPx)
    return a * b
}

internal fun mixF(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Deterministic hash in [0,1), mirrors the JS lab's `hash(n)`. */
internal fun hash(n: Float): Float {
    val s = sin(n * 127.1f) * 43758.5453f
    return s - floor(s)
}

internal fun frac(x: Float): Float = x - floor(x)

/**
 * Pseudo play/pause "energy" in [0,1] derived from the already-gated
 * [ampPx], for renderers that need a continuous liveliness scalar for
 * alpha/rate modulation separate from vertical amplitude.
 */
internal fun energyOf(ampPx: Float, baseY: Float): Float =
    (ampPx / (baseY.coerceAtLeast(1f) * 0.30f + 1f)).coerceIn(0f, 1f)

/**
 * A lens-shaped clip band centered on [midY], reaching ±[halfH] in the middle but
 * pinching to a point at both ends (following the taper window). Block-fill styles
 * (interference/caldera/loom) draw *inside* this so their vertical extent — not just
 * their amplitude — collapses smoothly into the thumb, with no hard edge at the playhead.
 */
private fun taperedBand(midY: Float, halfH: Float, width: Float, ts: Float, te: Float): Path {
    val p = Path()
    p.moveTo(0f, midY)
    var x = 0f
    while (x <= width) { p.lineTo(x, midY - halfH * window(x, width, ts, te)); x += 4f }
    x = width
    while (x >= 0f) { p.lineTo(x, midY + halfH * window(x, width, ts, te)); x -= 4f }
    p.close()
    return p
}

/** Palette: album-art vibrant swatches when available, else shades of [color]. */
internal fun palette(color: Color, vibrant: Boolean, waveColors: List<Color>): List<Color> =
    if (vibrant && waveColors.size >= 2) waveColors
    else listOf(
        color,
        lerp(color, Color.White, 0.35f),
        lerp(color, Color.White, 0.6f),
        lerp(color, Color.White, 0.8f)
    )

// ---------- 1. Mercury ----------

private class MercuryBlob(val speed: Float, val phase: Float, val sigmaFrac: Float, val weight: Float)

private val MERCURY_BLOBS = listOf(
    MercuryBlob(1.0f, 0.0f, 0.11f, 1.0f),
    MercuryBlob(1.7f, 2.1f, 0.085f, 0.8f),
    MercuryBlob(2.3f, 4.2f, 0.07f, 0.92f),
    MercuryBlob(1.35f, 5.6f, 0.09f, 0.7f)
)

/** Liquid-metal domes that merge and pull apart on a still pool, glint on crests. */
fun DrawScope.drawMercury(
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
    val cols = palette(color, vibrant, waveColors)
    val e = energyOf(ampPx, baseY)
    val sig = MERCURY_BLOBS.map { max(8f, it.sigmaFrac * width) }
    val cen = MERCURY_BLOBS.map { b -> width * (0.5f + 0.42f * sin(phase * 0.55f * b.speed + b.phase)) }
    fun heightAt(x: Float): Float {
        var v = 0f
        for (i in MERCURY_BLOBS.indices) {
            val d = (x - cen[i]) / sig[i]
            v += MERCURY_BLOBS[i].weight * exp(-d * d)
        }
        return v
    }
    val fillPath = Path()
    val glintPath = Path()
    fillPath.moveTo(0f, baseY)
    var glintOpen = false
    var x = 0f
    while (x <= width) {
        val wnd = window(x, width, taperStartPx, taperEndPx)
        val hh = heightAt(x)
        val y = baseY - ampPx * hh * wnd
        fillPath.lineTo(x, y)
        if (hh > 0.35f && wnd > 0.05f) {
            val gy = y - 2.4f
            if (!glintOpen) { glintPath.moveTo(x, gy); glintOpen = true } else glintPath.lineTo(x, gy)
        } else {
            glintOpen = false
        }
        x += 3f
    }
    fillPath.lineTo(width, baseY)
    fillPath.close()
    val bottomColor = lerp(color, cols[3 % cols.size], 0.5f).copy(alpha = 0.9f)
    val topColor = lerp(cols[0], Color.White, 0.15f).copy(alpha = 0.96f)
    drawPath(fillPath, Brush.verticalGradient(listOf(bottomColor, topColor), baseY, baseY - ampPx * 1.4f))
    drawPath(
        glintPath,
        lerp(cols[0], Color.White, 0.7f).copy(alpha = (0.55f * e).coerceIn(0f, 1f)),
        style = Stroke(width = 1.4f.dp.toPx()),
        blendMode = BlendMode.Plus
    )
}

// ---------- 2. Silk ----------

private fun DrawScope.drawRibbon(
    color: Color, ampPx: Float, phase: Float, baseY: Float, width: Float,
    vibrant: Boolean, waveColors: List<Color>, taperStartPx: Float, taperEndPx: Float,
    ampScale: Float, thickScale: Float, sheenCycles: Int, sheenAlpha: Float, sheenWidthScale: Float,
    contrastScale: Float = 0f
) {
    if (ampPx <= 0.5f || width <= 0f) return
    val cols = palette(color, vibrant, waveColors)
    val e = energyOf(ampPx, baseY)
    val twentyPi = (20.0 * Math.PI).toFloat()
    val a = ampPx * 0.5f * ampScale
    val thickness = ampPx * 0.12f * thickScale
    // Two slightly out-of-phase travel harmonics + a slow twist give the cloth its motion.
    fun trav(x: Float) = sin(x / (width * 0.22f) + phase * 0.7f) * 0.6f + sin(x / (width * 0.11f) - phase * 0.5f) * 0.4f
    fun thick(x: Float) = 0.5f + 0.5f * sin(x / (width * 0.17f) + phase * 0.9f + 1.3f)

    // Sample the two edges once; reuse for the fill, the definition line, and the
    // migrating contrast line. The ribbon oscillates CENTERED on the baseline, so it
    // crosses above AND dips below the scrub line (the look the user liked). The
    // canvas now has room below the line, and the amp/thick below are sized so the
    // deepest dip still fits the canvas — no bottom clip, no dark band.
    val xs = ArrayList<Float>()
    val tops = ArrayList<Float>()
    val bots = ArrayList<Float>()
    val twists = ArrayList<Float>()
    var x = 0f
    while (x <= width) {
        val wnd = window(x, width, taperStartPx, taperEndPx)
        val center = baseY - a * trav(x) * wnd
        val half = (thickness + a * 0.35f * thick(x)) * wnd
        xs.add(x); tops.add(center - half); bots.add(center + half); twists.add(thick(x))
        x += 3f
    }

    val fill = Path()
    for (i in xs.indices) { if (i == 0) fill.moveTo(xs[0], tops[0]) else fill.lineTo(xs[i], tops[i]) }
    for (i in xs.indices.reversed()) fill.lineTo(xs[i], bots[i])
    fill.close()

    drawPath(
        fill,
        // Middle biased a touch toward white so the cloth never reads as a fully
        // dark band when the sheen happens to be sweeping elsewhere.
        Brush.horizontalGradient(listOf(cols[3 % cols.size], lerp(cols[0], Color.White, 0.25f), cols[1 % cols.size]), 0f, width),
        alpha = 0.9f
    )
    // Colored definition line on the top edge, so the ribbon still reads over
    // near-white album art when the rest of the UI has gone dark.
    val topEdge = Path()
    for (i in xs.indices) { if (i == 0) topEdge.moveTo(xs[0], tops[0]) else topEdge.lineTo(xs[i], tops[i]) }
    drawPath(topEdge, color.copy(alpha = 0.7f), style = Stroke(width = 1.6f.dp.toPx()))

    // 3D fold shading: ONE dark seam that migrates between the edges with the
    // cloth's twist, always paired with a bright highlight on the opposite edge.
    // The two crossfade through zero in the middle so they are never both strong
    // at once (the old version drew both black lines simultaneously and read as
    // a muddy outline). Both sit slightly INSIDE the ribbon so they read as
    // shading ON the cloth, not a border around it.
    if (contrastScale > 0f) {
        val sw = 1.7f.dp.toPx()
        val hw = 1.1f.dp.toPx()
        for (i in 0 until xs.size - 1) {
            val t = twists[i]
            val th = bots[i] - tops[i]
            val th2 = bots[i + 1] - tops[i + 1]
            // Fade the shading out where the ribbon is thin (taper ends, collapsed
            // twist) so a pinched section never turns into a hard dark streak.
            val thinFade = sstep(th / (ampPx * 0.4f))
            val shadeTop = sstep((t - 0.55f) * 3f) * contrastScale * 0.5f * thinFade
            val shadeBot = sstep((0.45f - t) * 3f) * contrastScale * 0.5f * thinFade
            val yt = tops[i] + th * 0.16f;      val yt2 = tops[i + 1] + th2 * 0.16f
            val yb = bots[i] - th * 0.16f;      val yb2 = bots[i + 1] - th2 * 0.16f
            if (shadeTop > 0.02f) {
                drawLine(Color.Black.copy(alpha = shadeTop), Offset(xs[i], yt), Offset(xs[i + 1], yt2), strokeWidth = sw)
                drawLine(Color.White.copy(alpha = shadeTop * 0.6f), Offset(xs[i], yb), Offset(xs[i + 1], yb2), strokeWidth = hw, blendMode = BlendMode.Plus)
            }
            if (shadeBot > 0.02f) {
                drawLine(Color.Black.copy(alpha = shadeBot), Offset(xs[i], yb), Offset(xs[i + 1], yb2), strokeWidth = sw)
                drawLine(Color.White.copy(alpha = shadeBot * 0.6f), Offset(xs[i], yt), Offset(xs[i + 1], yt2), strokeWidth = hw, blendMode = BlendMode.Plus)
            }
        }
    }

    // Two-layer sheen sweep: a wide soft bloom plus a narrow hot core, so the
    // highlight reads as light raking across satin instead of a flat pale bar.
    // Seamless sweep: an integer number of passes per 20π loop, so the highlight
    // never teleports mid-travel when phase wraps. (frac() has period 1, so the
    // speed must be sheenCycles/20π — NOT a raw 0.1 multiple.) The travel is padded
    // by ±sheenHalf so the square slides fully THROUGH and off past the playhead
    // instead of blanking the instant its right edge reaches the thumb (both ends
    // of the padded range sit off the ribbon, so the wrap stays invisible).
    val sheenHalf = width * 0.12f * sheenWidthScale
    val sh = frac(phase * (sheenCycles / twentyPi)) * (width + 2f * sheenHalf) - sheenHalf
    val bloomCol = lerp(cols[2 % cols.size], Color.White, 0.75f)
    clipPath(fill) {
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, bloomCol.copy(alpha = (sheenAlpha * 0.45f * e).coerceIn(0f, 1f)), Color.Transparent),
                sh - sheenHalf, sh + sheenHalf
            ),
            topLeft = Offset.Zero, size = size, blendMode = BlendMode.Plus
        )
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = (sheenAlpha * 0.55f * e).coerceIn(0f, 1f)), Color.Transparent),
                sh - sheenHalf * 0.28f, sh + sheenHalf * 0.28f
            ),
            topLeft = Offset.Zero, size = size, blendMode = BlendMode.Plus
        )
    }
}

/** A translucent ribbon rippling in a faint draft, with a sheen sweeping toward the thumb. */
fun DrawScope.drawSilk(
    color: Color, ampPx: Float, phase: Float, baseY: Float, width: Float,
    vibrant: Boolean = false, waveColors: List<Color> = emptyList(),
    taperStartPx: Float = 0f, taperEndPx: Float = 0f
) = drawRibbon(color, ampPx, phase, baseY, width, vibrant, waveColors, taperStartPx, taperEndPx, 0.7f, 1.0f, 6, 0.55f, 1.4f, 0.3f)

/** Silk turned up: a bolder (but not bloated) ribbon with a tall two-layer
 *  sheen sweep and a migrating shadow fold that flips edges as the cloth
 *  twists — clean satin, not mud. */
fun DrawScope.drawSilkX(
    color: Color, ampPx: Float, phase: Float, baseY: Float, width: Float,
    vibrant: Boolean = false, waveColors: List<Color> = emptyList(),
    taperStartPx: Float = 0f, taperEndPx: Float = 0f
) = drawRibbon(color, ampPx, phase, baseY, width, vibrant, waveColors, taperStartPx, taperEndPx, 0.8f, 1.3f, 13, 1.0f, 2.4f, 1.0f)

// ---------- 3. Wisptrail (caustics — the default) ----------

private class Filament(val amp: Float, val periodDp: Float, val speed: Float, val colorIdx: Int)

// speed MUST be a multiple of 0.1 so phase*speed lands on a whole number of 2π
// cycles at the 20π loop point — otherwise the pattern jumps when phase wraps.
private val WISPTRAIL_FILAMENTS = listOf(
    Filament(0.9f, 26f, 1.0f, 0),
    Filament(1.3f, 18f, -0.7f, 1),
    Filament(0.7f, 34f, 0.6f, 2),
    Filament(1.6f, 14f, -1.1f, 3),
    Filament(1.05f, 22f, 0.8f, 0)
)

// Accentuated variant: a chaotic pile of extra filaments at wildly varied speeds
// and wavelengths (all speeds multiples of 0.1 to stay seamless).
private val WISPTRAILX_FILAMENTS = WISPTRAIL_FILAMENTS + listOf(
    Filament(1.9f, 11f, 1.3f, 1),
    Filament(1.4f, 30f, -0.9f, 3),
    Filament(2.1f, 9f, -1.4f, 2),
    Filament(2.4f, 7f, 1.8f, 0),
    Filament(1.7f, 15f, -1.6f, 1),
    Filament(2.8f, 6f, 2.1f, 3),
    Filament(1.2f, 40f, -0.5f, 2),
    Filament(2.0f, 12f, -2.0f, 0),
    Filament(2.6f, 8f, 1.5f, 1),
    Filament(3.0f, 5f, -2.4f, 2)
)

private fun DrawScope.drawCaustics(
    color: Color, ampPx: Float, phase: Float, baseY: Float, width: Float,
    vibrant: Boolean, waveColors: List<Color>, taperStartPx: Float, taperEndPx: Float,
    filaments: List<Filament>, ampScale: Float, alphaScale: Float
) {
    if (ampPx <= 0.5f || width <= 0f) return
    val cols = palette(color, vibrant, waveColors)
    val e = energyOf(ampPx, baseY)
    val vAmp = ampPx * 0.3f * ampScale
    for (f in filaments) {
        val col = cols[f.colorIdx % cols.size]
        val periodPx = f.periodDp.dp.toPx()
        for (pass in 0 until 2) {
            val strokeW = (if (pass == 0) 9f else 3f).dp.toPx()
            val a = ((if (pass == 0) 0.07f else 0.16f) * (0.25f + 0.75f * e) * alphaScale).coerceIn(0f, 1f)
            val path = Path()
            var first = true
            var x = 0f
            while (x <= width) {
                val wnd = window(x, width, taperStartPx, taperEndPx)
                // No abrupt "shallowest near the thumb" step here — that hard offset
                // put a visible kink line across every filament. Pure sine now.
                val y = baseY - f.amp * vAmp * sin(x / periodPx + phase * f.speed) * wnd
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                x += 6f
            }
            drawPath(path, col.copy(alpha = a), style = Stroke(width = strokeW), blendMode = BlendMode.Plus)
        }
    }
}

/** Sunlit caustics on a shallow floor: crossings bloom bright via additive filaments. */
fun DrawScope.drawWisptrail(
    color: Color, ampPx: Float, phase: Float, baseY: Float, width: Float,
    vibrant: Boolean = false, waveColors: List<Color> = emptyList(),
    taperStartPx: Float = 0f, taperEndPx: Float = 0f
) = drawCaustics(color, ampPx, phase, baseY, width, vibrant, waveColors, taperStartPx, taperEndPx, WISPTRAIL_FILAMENTS, 1.0f, 1.0f)

/** Wisptrail turned up: more filaments, taller and brighter blooms. */
fun DrawScope.drawWisptrailX(
    color: Color, ampPx: Float, phase: Float, baseY: Float, width: Float,
    vibrant: Boolean = false, waveColors: List<Color> = emptyList(),
    taperStartPx: Float = 0f, taperEndPx: Float = 0f
) = drawCaustics(color, ampPx, phase, baseY, width, vibrant, waveColors, taperStartPx, taperEndPx, WISPTRAILX_FILAMENTS, 1.8f, 1.5f)

// ---------- 4. Sparks ----------

/** A lit fuse: a flickering ember trail brightest at the playhead, a hot burning
 *  tip, and constant sparks spraying off it and arcing down under gravity. */
fun DrawScope.drawSparks(
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
    val cols = palette(color, vibrant, waveColors)
    val e = energyOf(ampPx, baseY)
    val twentyPi = (20.0 * Math.PI).toFloat()
    val emberCol = lerp(cols[0], Color.White, 0.2f)

    // Glowing ember trail along the fuse, brighter toward the burning tip.
    var x = 0f
    val seg = 6f.dp.toPx()
    while (x <= width) {
        val wnd = window(x, width, taperStartPx, taperEndPx)
        val nearTip = x / width
        val flicker = 0.7f + 0.3f * sin(phase * 3f + x * 0.1f)
        val a = (0.28f * nearTip * flicker * (0.4f + 0.6f * e) * wnd).coerceIn(0f, 1f)
        if (a > 0.01f) drawCircle(emberCol.copy(alpha = a), radius = (2f + 3.5f * nearTip).dp.toPx(), center = Offset(x, baseY), blendMode = BlendMode.Plus)
        x += seg
    }
    // Hot burning tip at the playhead.
    val tipFlicker = 0.6f + 0.4f * sin(phase * 7f)
    drawCircle(Color.White.copy(alpha = (0.65f * tipFlicker * e).coerceIn(0f, 1f)), radius = 4f.dp.toPx(), center = Offset(width, baseY), blendMode = BlendMode.Plus)

    // Constant sparks flying off, clustered near the tip, arcing down under gravity.
    val n = max(14, min(70, (width / 16f.dp.toPx()).toInt() + 22))
    for (i in 0 until n) {
        val seed = hash(i.toFloat())
        val k = 10 + (hash(i * 2.3f) * 20f).toInt()        // fast; integer cycles -> seamless
        val ti = frac(phase * (k / twentyPi) + seed)
        val originX = width - hash(i * 1.7f) * hash(i * 1.7f) * width   // squared -> clustered at the tip
        val wnd = window(originX, width, taperStartPx, taperEndPx)
        val ang = (hash(i * 3.1f) - 0.5f) * 2.4f            // spray angle from vertical
        val speed = (10f + hash(i * 4.7f) * 22f).dp.toPx()
        val sx = originX + sin(ang) * speed * ti
        val sy = baseY - cos(ang) * speed * ti + 0.5f * 22f.dp.toPx() * ti * ti   // gravity
        val a = (min(1f, ti * 8f) * (1f - ti) * (0.5f + 0.5f * e) * wnd).coerceIn(0f, 1f)
        if (a <= 0.01f) continue
        val col = lerp(cols[i % cols.size], Color.White, 0.4f + 0.4f * ti)
        drawCircle(col.copy(alpha = a * 0.5f), radius = 2f.dp.toPx(), center = Offset(sx, sy), blendMode = BlendMode.Plus)
        drawCircle(col.copy(alpha = a), radius = 1f.dp.toPx(), center = Offset(sx, sy))
    }
}

// ---------- 5. Choir (pendulum wave) ----------

/** A row of dots riding one uniform traveling wave — calm, consistent, seamless. */
fun DrawScope.drawChoir(
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
    val cols = palette(color, vibrant, waveColors)
    val spacing = 7f.dp.toPx()
    val waveLen = 46f.dp.toPx()   // one consistent wave width for every dot
    val drift = 1.0f              // multiple of 0.1 -> lands on whole cycles at the 20π loop (no stutter)
    val r = 2.1f.dp.toPx()
    var x = 0f
    while (x <= width) {
        val wnd = window(x, width, taperStartPx, taperEndPx)
        val s = 0.5f + 0.5f * sin(x / waveLen * TWO_PI + phase * drift)
        val y = baseY - ampPx * s * wnd
        val col = lerp(cols[0], cols[1 % cols.size], (x / width).coerceIn(0f, 1f))
        drawCircle(lerp(col, Color.White, 0.15f).copy(alpha = 0.95f), radius = r, center = Offset(x, y))
        x += spacing
    }
}

// ---------- 6. Interference (PREMIUM — glitch) ----------

/** Premium glitch: chromatic RGB-split combs, steppy horizontal tearing,
 *  scanlines, and occasional full-canvas signal flashes that punch past the bar. */
fun DrawScope.drawInterference(
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
    val cols = palette(color, vibrant, waveColors)
    val e = energyOf(ampPx, baseY)
    val mid = baseY
    val hH = ampPx * 0.75f
    val pitch = 8f.dp.toPx()
    val v = phase * 1.4f                                    // comb travel (multiple of 0.1 -> seamless)
    val chans = listOf(Color.Red, Color.Green, Color.Blue)
    clipPath(taperedBand(mid, hH, width, taperStartPx, taperEndPx)) {
        // 1) RGB-split moiré combs — three channels offset for chromatic aberration.
        val split = 2.5f.dp.toPx() + 5f.dp.toPx() * sin(phase * 1.7f)
        for (ci in 0..2) {
            val cxoff = (ci - 1) * split
            var x = -pitch
            while (x <= width) {
                val px = (((x + v + cxoff) % pitch) + pitch) % pitch
                val xx = x - px + pitch * 0.5f + cxoff
                if (xx in -pitch..(width + pitch)) {
                    drawRect(
                        chans[ci].copy(alpha = (0.30f * (0.5f + 0.5f * e)).coerceIn(0f, 1f)),
                        topLeft = Offset(xx - pitch * 0.22f, mid - hH),
                        size = Size(pitch * 0.44f, hH * 2f),
                        blendMode = BlendMode.Plus
                    )
                }
                x += pitch
            }
        }
        // 2) Horizontal tear slices: bright bars jump sideways in steppy bursts.
        val slices = 6
        val sliceH = (hH * 2f) / slices
        for (s in 0 until slices) {
            val tq = floor(phase * 5f + s * 2.3f)              // quantized time -> hard steps
            if (hash(tq * 0.7f + s * 1.3f) < 0.55f) continue
            val sy = mid - hH + s * sliceH
            val off = (hash(tq + s) - 0.5f) * width * 0.4f
            val bw = width * (0.15f + hash(tq * 1.7f + s) * 0.5f)
            val bx = hash(tq * 2.1f + s) * width + off
            drawRect(
                lerp(cols[0], Color.White, 0.6f).copy(alpha = (0.5f * e).coerceIn(0f, 1f)),
                topLeft = Offset(bx, sy),
                size = Size(bw, sliceH * 0.7f),
                blendMode = BlendMode.Plus
            )
        }
        // 3) Scanlines across the whole band.
        var y = mid - hH
        while (y < mid + hH) {
            drawRect(Color.Black.copy(alpha = 0.16f), topLeft = Offset(0f, y), size = Size(width, 1.2f.dp.toPx()))
            y += 3.2f.dp.toPx()
        }
    }
    // 4) Occasional full-height flash + displaced channel bars that reach beyond the
    //    band into the rest of the canvas — the "glitch out everything" moment.
    val flashClock = floor(phase * 3f)
    if (hash(flashClock * 0.37f) > 0.86f) {
        drawRect(Color.White.copy(alpha = (0.12f * e).coerceIn(0f, 1f)), topLeft = Offset.Zero, size = size, blendMode = BlendMode.Plus)
        for (b in 0..2) {
            val bx = hash(flashClock + b * 3.1f) * width
            val bw = 4f.dp.toPx() + hash(flashClock * 2f + b) * 20f.dp.toPx()
            drawRect(chans[b].copy(alpha = (0.2f * e).coerceIn(0f, 1f)), topLeft = Offset(bx, 0f), size = Size(bw, size.height), blendMode = BlendMode.Plus)
        }
    }
}

// ---------- 7. Caldera (PREMIUM — electric chaos) ----------

/** Premium electric chaos: a re-striking lightning bolt down the bar with forking
 *  branches, crackling sparks, a charged plasma head, and thunder flashes that
 *  light up the whole canvas. Over-the-top on purpose. */
fun DrawScope.drawCaldera(
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
    val cols = palette(color, vibrant, waveColors)
    val e = energyOf(ampPx, baseY)
    val H = size.height
    val electric = Color(0xFF66E0FF)
    val hot = lerp(cols[0], Color.White, 0.7f)

    // Main jagged bolt — noise re-seeds in hard steps (re-strike flicker).
    fun boltY(x: Float): Float {
        val wnd = window(x, width, taperStartPx, taperEndPx)
        val tq = floor(phase * 8f)
        val n = (hash(x * 0.05f + tq * 0.3f) - 0.5f) * 2f
        val n2 = sin(x / 12f.dp.toPx() + phase * 3f)
        return baseY - (n * ampPx * 0.9f + n2 * ampPx * 0.4f) * wnd
    }
    // Glow underlay + white core.
    for (pass in 0..1) {
        val c = if (pass == 0) electric else Color.White
        val sw = if (pass == 0) 6f.dp.toPx() else 2f.dp.toPx()
        val p = Path(); var x = 0f; var first = true
        while (x <= width) {
            val y = boltY(x)
            if (first) { p.moveTo(x, y); first = false } else p.lineTo(x, y)
            x += 4f
        }
        drawPath(p, c.copy(alpha = (0.85f * (0.4f + 0.6f * e)).coerceIn(0f, 1f)), style = Stroke(sw, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    }

    // Forking branch bolts.
    for (i in 0 until 7) {
        val tq = floor(phase * 6f + i)
        if (hash(tq * 0.5f + i) < 0.6f) continue
        val bx = hash(i * 3.3f) * width
        val wnd = window(bx, width, taperStartPx, taperEndPx)
        if (wnd < 0.05f) continue
        val p = Path(); var jx = bx; var jy = boltY(bx)
        p.moveTo(jx, jy)
        val dir = if (hash(tq + i) > 0.5f) 1f else -1f
        val hdir = if (hash(i * 1.1f) > 0.5f) 1f else -1f
        val steps = 3 + (hash(i * 1.7f) * 3f).toInt()
        for (s in 0 until steps) {
            jx += hdir * hash(tq + s + i * 2f) * 10f.dp.toPx()
            jy += dir * (8f.dp.toPx() + hash(tq * 2f + s) * 10f.dp.toPx())
            p.lineTo(jx, jy)
        }
        drawPath(p, electric.copy(alpha = (0.6f * e).coerceIn(0f, 1f)), style = Stroke(2f.dp.toPx(), cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    }

    // Crackling sparks flung off the bolt.
    val twentyPi = (20.0 * Math.PI).toFloat()
    for (i in 0 until 22) {
        val seed = hash(i.toFloat())
        val k = 10 + (hash(i * 2.3f) * 20f).toInt()
        val ti = frac(phase * (k / twentyPi) + seed)
        val sx = hash(i * 4.1f) * width
        val wnd = window(sx, width, taperStartPx, taperEndPx)
        val sy = boltY(sx) + (hash(i * 7.7f) - 0.5f) * ampPx * 1.6f * ti
        val a = (min(1f, ti * 6f) * (1f - ti) * (0.4f + 0.6f * e) * wnd).coerceIn(0f, 1f)
        if (a <= 0.01f) continue
        drawCircle(hot.copy(alpha = a), radius = 1.5f.dp.toPx(), center = Offset(sx, sy), blendMode = BlendMode.Plus)
    }

    // Charged plasma head at the thumb.
    val headY = boltY(width)
    val headCenter = Offset(width, headY)
    val headRadius = 20f.dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(listOf(electric.copy(alpha = (0.55f * e).coerceIn(0f, 1f)), Color.Transparent), headCenter, headRadius),
        radius = headRadius, center = headCenter
    )
    drawCircle(Color.White.copy(alpha = (0.8f * e).coerceIn(0f, 1f)), radius = 3f.dp.toPx(), center = headCenter, blendMode = BlendMode.Plus)

    // Thunder flash lighting the whole canvas.
    val tclock = floor(phase * 2.5f)
    if (hash(tclock * 0.29f) > 0.9f) {
        drawRect(electric.copy(alpha = (0.1f * e).coerceIn(0f, 1f)), topLeft = Offset.Zero, size = size, blendMode = BlendMode.Plus)
    }
}

// ---------- 7b. Cyberpunk (PREMIUM — neon grid + glitch) ----------

/** Premium cyberpunk: a neon perspective grid filling the whole canvas, a
 *  chromatic neon waveform scanning across it, glitch slabs, and a bright scan
 *  head at the thumb. Reaches beyond the bar into the rest of the canvas. */
fun DrawScope.drawCyberpunk(
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
    val e = energyOf(ampPx, baseY)
    val H = size.height
    val neonA = Color(0xFF00E5FF)   // cyan
    val neonB = Color(0xFFFF2D95)   // magenta

    // 1) Perspective grid over the full canvas — scrolling verticals + horizon rows.
    val vGap = 22f.dp.toPx()
    val scroll = frac(phase * 0.3f) * vGap
    var gx = -scroll
    while (gx <= width) {
        drawLine(neonA.copy(alpha = (0.18f * (0.4f + 0.6f * e)).coerceIn(0f, 1f)), Offset(gx, 0f), Offset(gx, H), strokeWidth = 1f.dp.toPx(), blendMode = BlendMode.Plus)
        gx += vGap
    }
    for (r in 0..5) {
        val t = frac(phase * 0.25f + r / 6f)
        val yy = mixF(baseY, H, t * t)                     // bunched near the baseline, spread below
        drawLine(neonB.copy(alpha = (0.22f * (1f - t) * (0.4f + 0.6f * e)).coerceIn(0f, 1f)), Offset(0f, yy), Offset(width, yy), strokeWidth = 1f.dp.toPx(), blendMode = BlendMode.Plus)
    }

    // 2) Chromatic neon waveform (magenta/cyan glow offset around a white core).
    val waveLen = 26f.dp.toPx()
    fun wy(x: Float): Float {
        val wnd = window(x, width, taperStartPx, taperEndPx)
        return baseY - ampPx * 0.8f * sin(x / waveLen * TWO_PI + phase * 1.3f) * wnd
    }
    for (pass in 0..2) {
        val off = when (pass) { 0 -> 3f.dp.toPx(); 1 -> -3f.dp.toPx(); else -> 0f }
        val c = when (pass) { 0 -> neonB; 1 -> neonA; else -> Color.White }
        val sw = if (pass == 2) 2f.dp.toPx() else 5f.dp.toPx()
        val p = Path(); var x = 0f; var first = true
        while (x <= width) {
            val y = wy(x)
            if (first) { p.moveTo(x + off, y); first = false } else p.lineTo(x + off, y)
            x += 3f
        }
        drawPath(p, c.copy(alpha = (0.7f * (0.4f + 0.6f * e)).coerceIn(0f, 1f)), style = Stroke(sw, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    }

    // 3) Occasional glitch slabs across full height.
    val gclock = floor(phase * 4f)
    if (hash(gclock * 0.41f) > 0.82f) {
        for (b in 0..2) {
            val bx = hash(gclock + b * 2.7f) * width
            val bw = 6f.dp.toPx() + hash(gclock * 1.9f + b) * 30f.dp.toPx()
            drawRect(if (b % 2 == 0) neonA.copy(alpha = (0.18f * e).coerceIn(0f, 1f)) else neonB.copy(alpha = (0.18f * e).coerceIn(0f, 1f)), topLeft = Offset(bx, 0f), size = Size(bw, H), blendMode = BlendMode.Plus)
        }
    }

    // 4) Bright scan head at the thumb.
    val hy = wy(width)
    drawCircle(neonA.copy(alpha = (0.5f * e).coerceIn(0f, 1f)), radius = 7f.dp.toPx(), center = Offset(width, hy), blendMode = BlendMode.Plus)
    drawCircle(Color.White.copy(alpha = (0.85f * e).coerceIn(0f, 1f)), radius = 3f.dp.toPx(), center = Offset(width, hy), blendMode = BlendMode.Plus)
}

// ---------- 8. Loom (weaving) ----------

/** The song woven as you listen: a checkerboard cloth fill with a fluttering fresh edge. */
fun DrawScope.drawLoom(
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
    val cols = palette(color, vibrant, waveColors)
    val e = energyOf(ampPx, baseY)
    val y0 = baseY - ampPx * 0.55f    // centered on the bar
    val y1 = baseY + ampPx * 0.55f
    val cell = 6f.dp.toPx()
    val mid = (y0 + y1) / 2f
    val half = (y1 - y0) / 2f
    val warpC = lerp(color, cols[3 % cols.size], 0.5f)
    val weftC = lerp(cols[0], Color.White, 0.1f)

    // Everything but the fresh edge draws inside the tapered band, so the cloth
    // pinches into the thumb instead of ending on a hard rectangular edge.
    clipPath(taperedBand(mid, half, width, taperStartPx, taperEndPx)) {
        var wx = 4f.dp.toPx()
        while (wx < width) {
            drawLine(
                lerp(color, Color.White, 0.25f).copy(alpha = 0.10f),
                Offset(wx, y0), Offset(wx, y1), strokeWidth = 1f.dp.toPx()
            )
            wx += cell
        }
        var gy = 0f
        var ry = 0
        while (y0 + gy < y1) {
            var gx = 0f
            var rx = 0
            while (gx < width) {
                val over = (rx + ry) % 2 == 1
                drawRect(
                    (if (over) weftC else warpC).copy(alpha = if (over) 0.6f else 0.42f),
                    topLeft = Offset(gx, y0 + gy),
                    size = Size(cell - 1f, cell - 1f)
                )
                gx += cell; rx++
            }
            gy += cell; ry++
        }
        val sh = frac(phase * 0.1f) * width
        val sheenHalf = width * 0.1f
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, lerp(cols[2 % cols.size], Color.White, 0.5f).copy(alpha = 0.35f * e), Color.Transparent),
                sh - sheenHalf, sh + sheenHalf
            ),
            topLeft = Offset(0f, y0), size = Size(width, (y1 - y0).coerceAtLeast(0f)),
            blendMode = BlendMode.Plus
        )
    }

    // Threads flow out from the middle of the playhead and travel left through the
    // cloth, fanning out vertically like the weave (replaces the old edge flutter).
    val threads = 7
    val threadColor = lerp(cols[0], Color.White, 0.25f)
    for (t in 0 until threads) {
        val spread = if (threads > 1) (t - (threads - 1) / 2f) / ((threads - 1) / 2f) else 0f  // -1..1
        val path = Path()
        var firstT = true
        var x = width
        while (x >= 0f) {
            val prog = (width - x) / width                 // 0 at playhead, 1 at far left
            val wnd = window(x, width, taperStartPx, taperEndPx)
            val y = mid + spread * (y1 - y0) * 0.42f * prog * wnd +
                sin(prog * 8f - phase * 0.8f + t) * ampPx * 0.12f * wnd
            if (firstT) { path.moveTo(x, y); firstT = false } else path.lineTo(x, y)
            x -= 4f
        }
        drawPath(
            path,
            threadColor.copy(alpha = (0.5f * (0.4f + 0.6f * e)).coerceIn(0f, 1f)),
            style = Stroke(width = 1.4f.dp.toPx()),
            blendMode = BlendMode.Plus
        )
    }
}

// ---------- 9. One UI (Samsung One UI 8 media bar) ----------

/** A smooth, bold single wave with a bright crest edge — Samsung One UI's calm look. */
fun DrawScope.drawOneUi(
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
    val cols = palette(color, vibrant, waveColors)
    val waveLen = 62f.dp.toPx()   // long, gentle undulation
    val amp = ampPx * 0.66f
    // Layered harmonics (all speeds multiples of 0.1 -> seamless) so the crest is
    // fluid and slightly asymmetric rather than a rigid, symmetric sine.
    fun yAt(x: Float): Float {
        val wnd = window(x, width, taperStartPx, taperEndPx)
        val w1 = sin(x / waveLen * TWO_PI + phase * 1.0f)
        val w2 = sin(x / (waveLen * 0.55f) * TWO_PI - phase * 0.6f + 1.1f)
        val w3 = sin(x / (waveLen * 1.8f) * TWO_PI + phase * 0.3f)
        val s = (0.5f + 0.32f * w1 + 0.12f * w2 + 0.1f * w3).coerceIn(0f, 1f)
        return baseY - amp * s * wnd
    }
    val fill = Path()
    fill.moveTo(0f, baseY)
    var x = 0f
    while (x <= width) { fill.lineTo(x, yAt(x)); x += 3f }
    fill.lineTo(width, baseY)
    fill.close()
    drawPath(
        fill,
        Brush.verticalGradient(
            listOf(
                lerp(cols[0], cols[1 % cols.size], 0.4f).copy(alpha = 0.85f),
                lerp(cols[0], Color.White, 0.25f).copy(alpha = 0.95f)
            ),
            baseY, baseY - amp
        )
    )
    val edge = Path()
    var first = true
    x = 0f
    while (x <= width) {
        val y = yAt(x)
        if (first) { edge.moveTo(x, y); first = false } else edge.lineTo(x, y)
        x += 3f
    }
    drawPath(edge, lerp(cols[0], Color.White, 0.5f).copy(alpha = 0.6f), style = Stroke(width = 2f.dp.toPx(), cap = StrokeCap.Round))
}

// ---------- 10. Squiggly (Pixel media player) ----------

/** The Pixel media player's traveling squiggle — tight, thin, round-capped. */
fun DrawScope.drawSquiggly(
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
    val cols = palette(color, vibrant, waveColors)
    val waveLen = 17f.dp.toPx()   // slightly wider peaks/troughs than stock Pixel
    val drift = 1.5f              // +phase => crests slide backward, trailing the playhead (multiple of 0.1)
    val amp = ampPx * 0.55f
    val path = Path()
    var first = true
    var x = 0f
    while (x <= width) {
        val wnd = window(x, width, taperStartPx, taperEndPx)
        val main = sin(x / waveLen * TWO_PI + phase * drift)
        val fluid = sin(x / (waveLen * 2.3f) * TWO_PI + phase * 0.5f) * 0.12f  // gentle secondary sway -> more fluid, less rigid
        val y = baseY - amp * (main * 0.9f + fluid) * wnd
        if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
        x += 2f
    }
    drawPath(
        path,
        lerp(cols[0], Color.White, 0.15f),
        style = Stroke(width = 3f.dp.toPx(), cap = StrokeCap.Round)
    )
}

// ---------- 11. Bubbles ----------

/** Bubbles born at the playhead, floating left and swelling as they drift — ridiculous on purpose. */
fun DrawScope.drawBubbles(
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
    val cols = palette(color, vibrant, waveColors)
    val e = energyOf(ampPx, baseY)
    val twentyPi = (20.0 * Math.PI).toFloat()
    val n = max(18, min(96, (width / 8f.dp.toPx()).toInt()))     // many more bubbles
    for (i in 0 until n) {
        val seed = hash(i.toFloat())
        val k = 5 + (hash(i * 2.3f) * 8f).toInt()          // faster travel; integer cycles -> seamless
        val ti = frac(phase * (k / twentyPi) + seed)        // 0 at the playhead, 1 far left
        val bx = width - ti * width
        if (bx < 0f) continue
        val wnd = window(bx, width, taperStartPx, taperEndPx)
        val by = baseY - sin(ti * 6f + i) * ampPx * 0.45f * wnd + (hash(i * 7.1f) - 0.5f) * ampPx * 0.5f
        val r = (2f.dp.toPx() + ti * ampPx * 1.2f * (0.6f + hash(i * 3.3f) * 0.9f))  // swell as they float
        val a = (min(1f, ti * 5f) * (1f - ti * 0.7f) * (0.4f + 0.6f * e) * wnd).coerceIn(0f, 1f)
        if (a <= 0.01f) continue
        val col = lerp(cols[i % cols.size], Color.White, 0.2f)
        // Body: soft additive core + rim stroke.
        drawCircle(col.copy(alpha = a * 0.18f), radius = r, center = Offset(bx, by), blendMode = BlendMode.Plus)
        drawCircle(col.copy(alpha = a * 0.7f), radius = r, center = Offset(bx, by), style = Stroke(1.4f.dp.toPx()))
        // Spinning specular highlight orbiting the rim + a trailing reflective arc.
        val spin = phase * (1.6f + hash(i * 4.7f) * 2.2f) + seed * TWO_PI
        val hx = bx + cos(spin) * r * 0.5f
        val hy = by + sin(spin) * r * 0.5f
        drawCircle(Color.White.copy(alpha = a * 0.9f), radius = r * 0.22f, center = Offset(hx, hy), blendMode = BlendMode.Plus)
        drawArc(
            color = Color.White.copy(alpha = a * 0.4f),
            startAngle = (spin * 180f / Math.PI).toFloat() + 150f, sweepAngle = 70f, useCenter = false,
            topLeft = Offset(bx - r * 0.72f, by - r * 0.72f), size = Size(r * 1.44f, r * 1.44f),
            style = Stroke(1.2f.dp.toPx()), blendMode = BlendMode.Plus
        )
        // Small secondary reflection opposite the highlight (glassy look).
        drawCircle(Color.White.copy(alpha = a * 0.45f), radius = r * 0.1f, center = Offset(bx - cos(spin) * r * 0.4f, by - sin(spin) * r * 0.4f), blendMode = BlendMode.Plus)
    }
}

// ---------- 12. Prism (PREMIUM scrub layer — chromatic refraction) ----------

/** Premium prism: one waveform refracted into red/green/cyan strands that
 *  converge to white where they cross, with twinkling glints on the crests. */
fun DrawScope.drawPrism(
    color: Color, ampPx: Float, phase: Float, baseY: Float, width: Float,
    vibrant: Boolean = false, waveColors: List<Color> = emptyList(),
    taperStartPx: Float = 0f, taperEndPx: Float = 0f
) {
    if (ampPx <= 0.5f || width <= 0f) return
    val e = energyOf(ampPx, baseY)
    val waveLen = 30f.dp.toPx()
    val chans = listOf(Color(0xFFFF5252), Color(0xFF69F0AE), Color(0xFF40C4FF))
    fun wy(x: Float, disp: Float, off: Float): Float {
        val wnd = window(x, width, taperStartPx, taperEndPx)
        return baseY - ampPx * 0.75f * sin(x / waveLen * TWO_PI + phase * 1.2f + disp) * wnd - off * wnd
    }
    for (ci in 0..2) {
        val p = Path(); var first = true; var x = 0f
        while (x <= width) {
            val y = wy(x, ci * 0.45f, (ci - 1) * 1.5f.dp.toPx())
            if (first) { p.moveTo(x, y); first = false } else p.lineTo(x, y)
            x += 3f
        }
        drawPath(p, chans[ci].copy(alpha = (0.55f * (0.4f + 0.6f * e)).coerceIn(0f, 1f)), style = Stroke(2.2f.dp.toPx(), cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    }
    // White core along the middle strand — the recombined beam.
    run {
        val p = Path(); var first = true; var x = 0f
        while (x <= width) {
            val y = wy(x, 0.45f, 0f)
            if (first) { p.moveTo(x, y); first = false } else p.lineTo(x, y)
            x += 3f
        }
        drawPath(p, Color.White.copy(alpha = (0.35f * e).coerceIn(0f, 1f)), style = Stroke(1.2f.dp.toPx(), cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    }
    // Twinkling 4-point glints riding the wave.
    for (i in 0 until 6) {
        val sx = hash(i * 1.7f) * width
        val wnd = window(sx, width, taperStartPx, taperEndPx)
        val tw = 0.5f + 0.5f * sin(phase * 2f + hash(i.toFloat()) * TWO_PI)
        val sy = wy(sx, 0.45f, 0f)
        val a = (tw * tw * 0.7f * e * wnd).coerceIn(0f, 1f)
        if (a <= 0.02f) continue
        val r = (1.5f + 2f * tw).dp.toPx()
        drawLine(Color.White.copy(alpha = a), Offset(sx - r, sy), Offset(sx + r, sy), strokeWidth = 1f.dp.toPx(), blendMode = BlendMode.Plus)
        drawLine(Color.White.copy(alpha = a), Offset(sx, sy - r), Offset(sx, sy + r), strokeWidth = 1f.dp.toPx(), blendMode = BlendMode.Plus)
    }
}

// ---------- 13. Warp (PREMIUM scrub layer — hyperspace streaks) ----------

/** Premium warp: star-streaks accelerating INTO the playhead, stretching and
 *  brightening as they arrive at a glowing jump-point. */
fun DrawScope.drawWarp(
    color: Color, ampPx: Float, phase: Float, baseY: Float, width: Float,
    vibrant: Boolean = false, waveColors: List<Color> = emptyList(),
    taperStartPx: Float = 0f, taperEndPx: Float = 0f
) {
    if (ampPx <= 0.5f || width <= 0f) return
    val cols = palette(color, vibrant, waveColors)
    val e = energyOf(ampPx, baseY)
    val twentyPi = (20.0 * Math.PI).toFloat()
    for (i in 0 until 30) {
        val k = 12 + (hash(i * 2.3f) * 20f).toInt()
        val ti = frac(phase * (k / twentyPi) + hash(i.toFloat()))
        val x = ti * width
        val wnd = window(x, width, taperStartPx, taperEndPx)
        val y = baseY - hash(i * 3.7f) * ampPx * 1.2f * wnd
        val len = (3f + k * 0.5f).dp.toPx() * (0.3f + 0.7f * ti)
        val a = (min(1f, ti * 4f) * (0.25f + 0.75f * ti) * 0.7f * e * wnd).coerceIn(0f, 1f)
        if (a <= 0.01f) continue
        val col = lerp(cols[i % cols.size], Color.White, (0.5f + 0.4f * ti).coerceAtMost(1f))
        drawLine(col.copy(alpha = a), Offset(x - len, y), Offset(x, y), strokeWidth = (0.8f + ti * 1.4f).dp.toPx(), cap = StrokeCap.Round, blendMode = BlendMode.Plus)
    }
    // Jump-point glow at the thumb.
    val hc = Offset(width, baseY - ampPx * 0.25f)
    drawCircle(
        brush = Brush.radialGradient(listOf(Color.White.copy(alpha = (0.30f * e).coerceIn(0f, 1f)), Color.Transparent), hc, 10f.dp.toPx()),
        radius = 10f.dp.toPx(), center = hc
    )
}

// ---------- 14. Monsoon (PREMIUM scrub layer — neon rain) ----------

/** Premium monsoon: slanted neon rain falling onto the baseline, splash
 *  ripples where drops land, and a flickering wet-street shimmer. */
fun DrawScope.drawMonsoon(
    color: Color, ampPx: Float, phase: Float, baseY: Float, width: Float,
    vibrant: Boolean = false, waveColors: List<Color> = emptyList(),
    taperStartPx: Float = 0f, taperEndPx: Float = 0f
) {
    if (ampPx <= 0.5f || width <= 0f) return
    val e = energyOf(ampPx, baseY)
    val twentyPi = (20.0 * Math.PI).toFloat()
    val cyan = Color(0xFF7FE7FF)
    val magenta = Color(0xFFFF6EC7)
    // Rain streaks above the bar.
    for (i in 0 until 26) {
        val k = 14 + (hash(i * 2.3f) * 18f).toInt()
        val ti = frac(phase * (k / twentyPi) + hash(i.toFloat()))
        val dx = hash(i * 1.7f) * width
        val wnd = window(dx, width, taperStartPx, taperEndPx)
        if (wnd < 0.03f) continue
        val top = baseY - ampPx * 1.6f
        val y = top + ti * (baseY - top)
        val len = (3f + hash(i * 4.1f) * 5f).dp.toPx()
        val slant = 1.6f.dp.toPx()
        val a = (min(1f, (1f - ti) * 3f) * 0.6f * e * wnd * (0.4f + 0.6f * hash(i * 5.3f))).coerceIn(0f, 1f)
        if (a > 0.01f) {
            val col = lerp(if (i % 2 == 0) cyan else magenta, Color.White, 0.4f)
            drawLine(col.copy(alpha = a), Offset(dx - slant, y - len), Offset(dx, y), strokeWidth = 1f.dp.toPx(), blendMode = BlendMode.Plus)
        }
        // Splash ripple as the drop lands.
        if (ti > 0.75f) {
            val rt = (ti - 0.75f) / 0.25f
            val rr = rt * 5f.dp.toPx()
            val ra = ((1f - rt) * 0.55f * e * wnd).coerceIn(0f, 1f)
            if (ra > 0.02f) drawArc(
                color = cyan.copy(alpha = ra),
                startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(dx - rr, baseY - rr * 0.5f), size = Size(rr * 2f, rr),
                style = Stroke(1f.dp.toPx()), blendMode = BlendMode.Plus
            )
        }
    }
    // Wet-street shimmer: short glimmer dashes flickering on the baseline.
    for (i in 0 until 9) {
        val gx = hash(i * 6.1f) * width
        val wnd = window(gx, width, taperStartPx, taperEndPx)
        val fl = 0.5f + 0.5f * sin(phase * 2f + i * 2.4f)
        val a = (fl * fl * 0.4f * e * wnd).coerceIn(0f, 1f)
        if (a <= 0.02f) continue
        val gl = (2f + hash(i * 8.3f) * 5f).dp.toPx()
        drawLine(lerp(if (i % 2 == 0) cyan else magenta, Color.White, 0.3f).copy(alpha = a), Offset(gx - gl, baseY), Offset(gx + gl, baseY), strokeWidth = 1.4f.dp.toPx(), blendMode = BlendMode.Plus)
    }
}

// ---------- 15. Pulse (PREMIUM scrub layer — bass-drop shockwaves) ----------

/** Premium pulse: bass-drop shockwave rings detonating out of the playhead on
 *  a steady beat, with a thumping glow core and a baseline flash per hit. */
fun DrawScope.drawPulse(
    color: Color, ampPx: Float, phase: Float, baseY: Float, width: Float,
    vibrant: Boolean = false, waveColors: List<Color> = emptyList(),
    taperStartPx: Float = 0f, taperEndPx: Float = 0f
) {
    if (ampPx <= 0.5f || width <= 0f) return
    val cols = palette(color, vibrant, waveColors)
    val e = energyOf(ampPx, baseY)
    val twentyPi = (20.0 * Math.PI).toFloat()
    val beat = frac(phase * (36f / twentyPi))          // 36 integer beats per loop -> seamless
    val env = exp(-4f * beat)
    val c1 = lerp(cols[0], Color.White, 0.35f)
    val c2 = lerp(cols[1 % cols.size], Color.White, 0.2f)
    // Shockwave rings expanding from the thumb, clipped to a band around the bar.
    clipRect(0f, baseY - ampPx * 1.15f, width + 1f, size.height) {
        for (j in 0 until 3) {
            val tr = frac(phase * (36f / twentyPi) + j / 3f)
            val r = tr * width * 0.85f
            val frontX = width - r
            val wnd = window(frontX.coerceIn(0f, width), width, taperStartPx, taperEndPx)
            val a = ((1f - tr) * (1f - tr) * 0.8f * e * (0.35f + 0.65f * wnd)).coerceIn(0f, 1f)
            if (a <= 0.02f || r <= 1f) continue
            drawCircle(c1.copy(alpha = a), radius = r, center = Offset(width, baseY), style = Stroke((2.5f * (1f - tr) + 0.8f).dp.toPx()), blendMode = BlendMode.Plus)
            drawCircle(c2.copy(alpha = a * 0.6f), radius = r + 2.5f.dp.toPx(), center = Offset(width, baseY), style = Stroke(1f.dp.toPx()), blendMode = BlendMode.Plus)
        }
    }
    // Thumping glow core at the playhead.
    val cr = (3f + 7f * env).dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(listOf(c1.copy(alpha = (0.6f * env * e).coerceIn(0f, 1f)), Color.Transparent), Offset(width, baseY), cr * 2.2f),
        radius = cr * 2.2f, center = Offset(width, baseY)
    )
    drawCircle(Color.White.copy(alpha = (0.7f * env * e).coerceIn(0f, 1f)), radius = cr * 0.4f, center = Offset(width, baseY), blendMode = BlendMode.Plus)
    // Baseline flash on the hit.
    if (env > 0.15f) {
        drawLine(c1.copy(alpha = (0.35f * env * e).coerceIn(0f, 1f)), Offset(0f, baseY), Offset(width, baseY), strokeWidth = 4f.dp.toPx(), cap = StrokeCap.Round, blendMode = BlendMode.Plus)
    }
}

// ---------- dispatcher ----------

/**
 * Routes to a named scrub-bar style; unknown styles fall back to the original
 * [drawHills]. When [consume] is on, the already-played (left) side fades to
 * invisible: the animation is confined to a trailing window just behind the
 * playhead by forcing a large left taper.
 */
fun DrawScope.drawWave(
    style: String,
    color: Color,
    ampPx: Float,
    phase: Float,
    baseY: Float,
    width: Float,
    vibrant: Boolean = false,
    waveColors: List<Color> = emptyList(),
    taperStartPx: Float = 0f,
    taperEndPx: Float = 0f,
    consume: Boolean = false
) {
    val ts = if (consume) max(taperStartPx, width - min(width * 0.45f, 100f.dp.toPx())) else taperStartPx
    when (style) {
        "mercury" -> drawMercury(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "silk" -> drawSilk(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "silkx" -> drawSilkX(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "wisptrail", "shallows" -> drawWisptrail(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "wisptrailx" -> drawWisptrailX(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "sparks" -> drawSparks(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "choir" -> drawChoir(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "interference" -> drawInterference(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "cyberpunk" -> drawCyberpunk(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "caldera" -> drawCaldera(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "aurora" -> drawWisptrailX(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "prism" -> drawPrism(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "warp" -> drawWarp(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "embers" -> drawSparks(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "eclipse" -> drawMercury(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "monsoon" -> drawMonsoon(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "pulse" -> drawPulse(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "loom" -> drawLoom(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "oneui" -> drawOneUi(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "squiggly" -> drawSquiggly(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        "bubbles" -> drawBubbles(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
        else -> drawHills(color, ampPx, phase, baseY, width, vibrant, waveColors, ts, taperEndPx)
    }
}
