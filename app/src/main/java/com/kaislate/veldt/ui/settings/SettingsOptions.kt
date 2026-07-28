// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.ui.settings

/**
 * One slider's shape: the ends of its range and how many discrete stops sit between
 * them.
 *
 * [steps] follows Material 3's meaning — points *strictly between* [min] and [max] —
 * so a slider from 80 to 200 with 11 steps stops every 10 units. It is carried here
 * rather than recomputed at the call site so a test can assert the increment the user
 * actually feels.
 */
data class SliderSpec(val min: Float, val max: Float, val steps: Int)

/**
 * Every option table the settings screen offers, lifted out of the Compose code.
 *
 * This object holds no Android and no Compose types on purpose. The lists are the
 * part of the settings screen that can be *wrong* rather than merely ugly — a chip
 * whose key does not match what [com.kaislate.veldt.data.settings.SettingsDefaults]
 * stores renders a card with nothing selected, and a slider range that excludes its
 * own default snaps the value on first touch. Neither shows up in a screenshot of a
 * configured device; both show up on a fresh install. Keeping the tables here means
 * `SettingsOptionsTest` can assert them without an instrumentation run.
 *
 * Each entry is a `(stored key, visible label)` pair. The key is what goes into
 * DataStore and is read by the overlay, so keys are frozen for the same reason the
 * DataStore key names are: changing one silently discards the user's choice. Labels
 * are free to change, and are asserted anyway because a typo in one ships to users.
 *
 * Lists are in display order. The chips render by iterating these, so order here is
 * order on screen.
 */
object SettingsOptions {

    /** Shape of the scrub-bar playhead. */
    val THUMB_SHAPES: List<Pair<String, String>> = listOf(
        "circle" to "Circle",
        "bar" to "Bar",
        "ring" to "Ring",
        "square" to "Square",
        "diamond" to "Diamond",
        "triangle" to "Triangle",
        "glow" to "Glow",
        "none" to "None",
    )

    /**
     * The scrub-bar animations that stay inside the bar.
     *
     * Standard and premium are two rows on screen but **one** selection: both write
     * `wave_style` and both read it back, and the `Premium` subheading between them is
     * visual grouping only. Nothing may assume a value lives in one list rather than
     * the other — use [allWaveStyleKeys] for membership questions.
     */
    val WAVE_STYLES_STANDARD: List<Pair<String, String>> = listOf(
        "wisptrail" to "Wisptrail",
        "wisptrailx" to "Wisptrail X",
        "hills" to "Hills",
        "silk" to "Silk",
        "silkx" to "Silk X",
        "mercury" to "Mercury",
        "sparks" to "Sparks",
        "bubbles" to "Bubbles",
        "choir" to "Choir",
        "oneui" to "One UI",
        "squiggly" to "Squiggly",
        "loom" to "Loom",
    )

    /** The animations that spill past the bar and draw over the whole card. */
    val WAVE_STYLES_PREMIUM: List<Pair<String, String>> = listOf(
        "interference" to "Interference",
        "cyberpunk" to "Cyberpunk",
        "caldera" to "Caldera",
        "aurora" to "Aurora",
        "prism" to "Prism",
        "warp" to "Warp",
        "embers" to "Embers",
        "eclipse" to "Eclipse",
        "monsoon" to "Monsoon",
        "pulse" to "Pulse",
    )

    /** Where the scrub-bar accent takes its colour from. */
    val WAVE_COLOR_MODES: List<Pair<String, String>> = listOf(
        "auto" to "Auto",
        "white" to "White",
        "accent-light" to "Light accent",
    )

    /** Which transport buttons appear on the pill once controls are switched on. */
    val PILL_CONTROL_SETS: List<Pair<String, String>> = listOf(
        "prev-play-next" to "Prev·Play·Next",
        "play-next" to "Play·Next",
        "play" to "Play",
    )

    /** Which side of the pill those buttons sit on. */
    val PILL_CONTROL_POSITIONS: List<Pair<String, String>> = listOf(
        "left" to "Left",
        "right" to "Right",
        "below" to "Underneath",
    )

    /**
     * How long the pill lingers after playback stops, as `(milliseconds, label)`.
     *
     * Offered as four chips rather than a slider: the useful values are far apart and
     * an arbitrary 37-second delay helps nobody. That makes the set closed, so the
     * stored default has to be a member of it — see the test.
     */
    val HIDE_DELAYS: List<Pair<Long, String>> = listOf(
        15_000L to "15 s",
        25_000L to "25 s",
        45_000L to "45 s",
        90_000L to "90 s",
    )

    /** Width budget for the title/artist text on the collapsed pill, in dp. */
    val PILL_WIDTH = SliderSpec(80f, 200f, 11)

    /** Overall width of the expanded panel, in dp. */
    val PANEL_WIDTH = SliderSpec(280f, 440f, 7)

    /** Album-art crossfade length, in milliseconds. */
    val CROSSFADE = SliderSpec(100f, 1200f, 21)

    /**
     * Every wave-style key the screen can select, standard row first.
     *
     * This is the set a stored `wave_style` must belong to. Asking either list alone
     * would answer "not selectable" for half the legal values.
     */
    fun allWaveStyleKeys(): List<String> =
        WAVE_STYLES_STANDARD.map { it.first } + WAVE_STYLES_PREMIUM.map { it.first }
}
