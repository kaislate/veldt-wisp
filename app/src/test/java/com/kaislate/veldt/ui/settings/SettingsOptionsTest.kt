// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.ui.settings

import com.kaislate.veldt.data.settings.SettingsDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings screen's option tables, asserted directly.
 *
 * These are chips and slider ranges, so almost every value here is visible to the
 * user and several are load-bearing for whether the screen works at all. Three
 * failure modes motivate the file:
 *
 *  1. A stored default that no chip can select. [SettingsDefaults] is the source of
 *     truth for "unset"; if its value is absent from the matching option list the
 *     card renders with nothing highlighted and the user has no way back to the
 *     default. The "default is selectable" tests below look the value up in
 *     [SettingsDefaults] rather than repeating a literal, which is the whole point —
 *     a literal on both sides would agree with itself while disagreeing with the app.
 *  2. A slider range that excludes its own default, which makes the stored value
 *     unreachable and snaps the control on first touch.
 *  3. A key shared between the standard and premium wave-style rows. The two rows
 *     are one logical selection, so a duplicate key renders two chips that select
 *     each other.
 *
 * Order is asserted, not just membership: the chips render in list order, so
 * reordering a list is a visible change.
 */
class SettingsOptionsTest {

    // ---- thumb shapes ------------------------------------------------------------------

    @Test
    fun `thumb shapes are the eight documented pairs, in display order`() {
        assertEquals(
            listOf(
                "circle" to "Circle",
                "bar" to "Bar",
                "ring" to "Ring",
                "square" to "Square",
                "diamond" to "Diamond",
                "triangle" to "Triangle",
                "glow" to "Glow",
                "none" to "None",
            ),
            SettingsOptions.THUMB_SHAPES,
        )
    }

    @Test
    fun `thumb shapes number eight`() {
        assertEquals(8, SettingsOptions.THUMB_SHAPES.size)
    }

    // ---- wave styles -------------------------------------------------------------------

    @Test
    fun `standard wave styles are the twelve documented pairs, in display order`() {
        assertEquals(
            listOf(
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
            ),
            SettingsOptions.WAVE_STYLES_STANDARD,
        )
    }

    @Test
    fun `standard wave styles number twelve`() {
        assertEquals(12, SettingsOptions.WAVE_STYLES_STANDARD.size)
    }

    @Test
    fun `premium wave styles are the ten documented pairs, in display order`() {
        assertEquals(
            listOf(
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
            ),
            SettingsOptions.WAVE_STYLES_PREMIUM,
        )
    }

    @Test
    fun `premium wave styles number ten`() {
        assertEquals(10, SettingsOptions.WAVE_STYLES_PREMIUM.size)
    }

    /**
     * The §7.6-3 guard. The two chip rows write the same setting, so a key present in
     * both would draw two chips that appear to select one another.
     */
    @Test
    fun `standard and premium wave-style keys do not collide`() {
        val all = SettingsOptions.allWaveStyleKeys()
        assertEquals(22, all.size)
        assertEquals(22, all.distinct().size)
    }

    @Test
    fun `allWaveStyleKeys is the standard keys followed by the premium keys`() {
        assertEquals(
            SettingsOptions.WAVE_STYLES_STANDARD.map { it.first } +
                SettingsOptions.WAVE_STYLES_PREMIUM.map { it.first },
            SettingsOptions.allWaveStyleKeys(),
        )
    }

    // ---- wave colour -------------------------------------------------------------------

    @Test
    fun `wave colour modes are the three documented pairs, in display order`() {
        assertEquals(
            listOf(
                "auto" to "Auto",
                "white" to "White",
                "accent-light" to "Light accent",
            ),
            SettingsOptions.WAVE_COLOR_MODES,
        )
    }

    @Test
    fun `wave colour modes number three`() {
        assertEquals(3, SettingsOptions.WAVE_COLOR_MODES.size)
    }

    // ---- pill controls -----------------------------------------------------------------

    @Test
    fun `pill control sets are the three documented pairs, in display order`() {
        assertEquals(
            listOf(
                "prev-play-next" to "Prev·Play·Next",
                "play-next" to "Play·Next",
                "play" to "Play",
            ),
            SettingsOptions.PILL_CONTROL_SETS,
        )
    }

    @Test
    fun `pill control sets number three`() {
        assertEquals(3, SettingsOptions.PILL_CONTROL_SETS.size)
    }

    @Test
    fun `pill control positions are the three documented pairs, in display order`() {
        assertEquals(
            listOf(
                "left" to "Left",
                "right" to "Right",
                "below" to "Underneath",
            ),
            SettingsOptions.PILL_CONTROL_POSITIONS,
        )
    }

    @Test
    fun `pill control positions number three`() {
        assertEquals(3, SettingsOptions.PILL_CONTROL_POSITIONS.size)
    }

    // ---- hide delay --------------------------------------------------------------------

    @Test
    fun `hide delays are the four documented pairs, in display order`() {
        assertEquals(
            listOf(
                15_000L to "15 s",
                25_000L to "25 s",
                45_000L to "45 s",
                90_000L to "90 s",
            ),
            SettingsOptions.HIDE_DELAYS,
        )
    }

    @Test
    fun `hide delays number four`() {
        assertEquals(4, SettingsOptions.HIDE_DELAYS.size)
    }

    /**
     * There is no slider for the hide delay, only these four chips, so a default
     * outside the set would render the card with nothing selected on a fresh install.
     */
    @Test
    fun `the default hide delay is one of the four offered values`() {
        assertTrue(
            "hide delay default ${SettingsDefaults.HIDE_DELAY_MS} is not offered by any chip",
            SettingsOptions.HIDE_DELAYS.any { it.first == SettingsDefaults.HIDE_DELAY_MS },
        )
    }

    // ---- every default is selectable (the §7.6-1 guards) --------------------------------

    @Test
    fun `the default thumb shape is selectable`() {
        assertTrue(
            "thumb shape default '${SettingsDefaults.THUMB_SHAPE}' has no chip",
            SettingsOptions.THUMB_SHAPES.any { it.first == SettingsDefaults.THUMB_SHAPE },
        )
    }

    @Test
    fun `the default wave style is selectable across both chip rows`() {
        assertTrue(
            "wave style default '${SettingsDefaults.WAVE_STYLE}' has no chip",
            SettingsDefaults.WAVE_STYLE in SettingsOptions.allWaveStyleKeys(),
        )
    }

    @Test
    fun `the default wave colour mode is selectable`() {
        assertTrue(
            "wave colour default '${SettingsDefaults.WAVE_COLOR_MODE}' has no chip",
            SettingsOptions.WAVE_COLOR_MODES.any { it.first == SettingsDefaults.WAVE_COLOR_MODE },
        )
    }

    @Test
    fun `the default pill control set is selectable`() {
        assertTrue(
            "control set default '${SettingsDefaults.PILL_CONTROL_SET}' has no chip",
            SettingsOptions.PILL_CONTROL_SETS.any { it.first == SettingsDefaults.PILL_CONTROL_SET },
        )
    }

    @Test
    fun `the default pill control position is selectable`() {
        assertTrue(
            "control position default '${SettingsDefaults.PILL_CONTROL_POSITION}' has no chip",
            SettingsOptions.PILL_CONTROL_POSITIONS.any {
                it.first == SettingsDefaults.PILL_CONTROL_POSITION
            },
        )
    }

    // ---- slider specs, literally --------------------------------------------------------

    @Test
    fun `the pill width slider spans 80 to 200 dp in 11 steps`() {
        assertEquals(SliderSpec(80f, 200f, 11), SettingsOptions.PILL_WIDTH)
    }

    @Test
    fun `the panel width slider spans 280 to 440 dp in 7 steps`() {
        assertEquals(SliderSpec(280f, 440f, 7), SettingsOptions.PANEL_WIDTH)
    }

    @Test
    fun `the crossfade slider spans 100 to 1200 ms in 21 steps`() {
        assertEquals(SliderSpec(100f, 1200f, 21), SettingsOptions.CROSSFADE)
    }

    // ---- every slider range contains its default (the §7.6-2 guards) --------------------

    @Test
    fun `the pill width range contains the stored default`() {
        assertInRange(SettingsOptions.PILL_WIDTH, SettingsDefaults.PILL_TEXT_WIDTH_DP, "pill width")
    }

    @Test
    fun `the panel width range contains the stored default`() {
        assertInRange(SettingsOptions.PANEL_WIDTH, SettingsDefaults.PANEL_WIDTH_DP, "panel width")
    }

    @Test
    fun `the crossfade range contains the stored default`() {
        assertInRange(SettingsOptions.CROSSFADE, SettingsDefaults.CROSSFADE_MS, "crossfade")
    }

    private fun assertInRange(spec: SliderSpec, default: Int, what: String) {
        assertTrue(
            "$what default $default falls outside the slider range ${spec.min}..${spec.max}",
            default >= spec.min && default <= spec.max,
        )
    }
}
