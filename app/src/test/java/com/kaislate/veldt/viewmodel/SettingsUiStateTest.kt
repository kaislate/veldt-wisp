// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.viewmodel

import com.kaislate.veldt.data.settings.SettingsDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * The drift guard between the settings screen's starting state and the on-disk
 * contract in [SettingsDefaults].
 *
 * A settings screen that renders before its first DataStore emission arrives shows
 * the values in this data class. If they disagree with [SettingsDefaults], the
 * screen shows one thing for a frame and then silently swaps to another — a toggle
 * that appears off, flicks on, and looks like a bug in the toggle. The eighteen
 * assertions below compare the two sources rather than either source to a literal,
 * because the literals are already pinned by `SettingsDefaultsTest`; what is
 * unpinned, and what these tests exist for, is the *link* between them.
 */
class SettingsUiStateTest {

    private val state = SettingsUiState()

    // ---- the eighteen settings default to the stored contract -------------------------

    @Test
    fun `islandEnabled defaults to the stored contract`() {
        assertEquals(SettingsDefaults.ISLAND_ENABLED, state.islandEnabled)
    }

    @Test
    fun `vibrantWave defaults to the stored contract`() {
        assertEquals(SettingsDefaults.VIBRANT_WAVE, state.vibrantWave)
    }

    @Test
    fun `topOffsetDp defaults to the stored contract`() {
        assertEquals(SettingsDefaults.TOP_OFFSET_DP, state.topOffsetDp)
    }

    @Test
    fun `hideDelayMs defaults to the stored contract`() {
        assertEquals(SettingsDefaults.HIDE_DELAY_MS, state.hideDelayMs)
    }

    @Test
    fun `positionKey defaults to the stored contract`() {
        assertEquals(SettingsDefaults.POSITION, state.positionKey)
    }

    @Test
    fun `homeOnly defaults to the stored contract`() {
        assertEquals(SettingsDefaults.HOME_ONLY, state.homeOnly)
    }

    @Test
    fun `thumbShape defaults to the stored contract`() {
        assertEquals(SettingsDefaults.THUMB_SHAPE, state.thumbShape)
    }

    @Test
    fun `waveColorMode defaults to the stored contract`() {
        assertEquals(SettingsDefaults.WAVE_COLOR_MODE, state.waveColorMode)
    }

    @Test
    fun `waveStyle defaults to the stored contract`() {
        assertEquals(SettingsDefaults.WAVE_STYLE, state.waveStyle)
    }

    @Test
    fun `consumeProgress defaults to the stored contract`() {
        assertEquals(SettingsDefaults.CONSUME_PROGRESS, state.consumeProgress)
    }

    @Test
    fun `pillTextWidthDp defaults to the stored contract`() {
        assertEquals(SettingsDefaults.PILL_TEXT_WIDTH_DP, state.pillTextWidthDp)
    }

    @Test
    fun `panelWidthDp defaults to the stored contract`() {
        assertEquals(SettingsDefaults.PANEL_WIDTH_DP, state.panelWidthDp)
    }

    @Test
    fun `hideNotification defaults to the stored contract`() {
        assertEquals(SettingsDefaults.HIDE_NOTIFICATION, state.hideNotification)
    }

    @Test
    fun `artCrossfade defaults to the stored contract`() {
        assertEquals(SettingsDefaults.ART_CROSSFADE, state.artCrossfade)
    }

    @Test
    fun `crossfadeMs defaults to the stored contract`() {
        assertEquals(SettingsDefaults.CROSSFADE_MS, state.crossfadeMs)
    }

    @Test
    fun `showPillControls defaults to the stored contract`() {
        assertEquals(SettingsDefaults.SHOW_PILL_CONTROLS, state.showPillControls)
    }

    @Test
    fun `pillControlSet defaults to the stored contract`() {
        assertEquals(SettingsDefaults.PILL_CONTROL_SET, state.pillControlSet)
    }

    @Test
    fun `pillControlPosition defaults to the stored contract`() {
        assertEquals(SettingsDefaults.PILL_CONTROL_POSITION, state.pillControlPosition)
    }

    // ---- the five permission flags, and their deliberate asymmetry --------------------

    @Test
    fun `overlay permission is assumed absent until checked`() {
        assertFalse(state.overlayGranted)
    }

    @Test
    fun `notification listener is assumed absent until checked`() {
        assertFalse(state.notifListenerGranted)
    }

    @Test
    fun `usage access is assumed absent until checked`() {
        assertFalse(state.usageAccessGranted)
    }

    @Test
    fun `post notifications is assumed granted because below API 33 it does not exist`() {
        // Optimistic on purpose. Starting this false makes the settings screen show a
        // warning for one frame on every launch, on every device that cannot even be
        // asked for the permission.
        assertTrue(state.postNotificationsGranted)
    }

    @Test
    fun `bluetooth connect is assumed granted because below API 31 it does not exist`() {
        // Optimistic on purpose, and doubly so: it is optional even where it exists.
        assertTrue(state.bluetoothConnectGranted)
    }

    @Test
    fun `the permission defaults are deliberately asymmetric`() {
        // Guards the whole shape against a tidy-up to all-false or all-true. Written
        // as one list so the asymmetry itself is the assertion.
        val flags = listOf(
            state.overlayGranted,
            state.notifListenerGranted,
            state.usageAccessGranted,
            state.postNotificationsGranted,
            state.bluetoothConnectGranted,
        )
        assertEquals(listOf(false, false, false, true, true), flags)
    }

    // ---- the shape of the state ------------------------------------------------------

    @Test
    fun `the state has exactly twenty-three fields`() {
        // Eighteen settings plus five permission flags. A nineteenth setting added
        // here but not wired into the observation table would otherwise sit at its
        // default forever with nothing to show for it.
        val fields = SettingsUiState::class.java.declaredFields
            .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
        assertEquals(23, fields.size)
    }

    @Test
    fun `copy takes exactly twenty-three parameters`() {
        // Second, independent reading of the same invariant: the Compose compiler adds
        // a static `$stable` field to this class, so field counting alone is a shakier
        // guard than the generated copy signature.
        val copy = SettingsUiState::class.java.declaredMethods.single { it.name == "copy" }
        assertEquals(23, copy.parameterCount)
    }

    // ---- the observation table --------------------------------------------------------

    @Test
    fun `every stored setting is observed`() {
        // The view model folds the repository's flows into the state through this
        // table. A setting missing from it persists correctly and then never appears
        // on screen, which is invisible to every other test in this file.
        assertEquals(18, SettingsBindings.ALL.size)
    }

    @Test
    fun `the observation table covers exactly the stored keys`() {
        assertEquals(SettingsDefaults.ALL_KEYS.toSet(), SettingsBindings.ALL.map { it.key }.toSet())
    }

    @Test
    fun `no setting is observed twice`() {
        assertEquals(18, SettingsBindings.ALL.map { it.key }.toSet().size)
    }

    /**
     * A state in which all eighteen settings differ from their defaults, and in which
     * no two settings of the same type share a value. Both properties matter: the
     * first makes a fold that ignores its input detectable, the second makes a fold
     * wired to the wrong field of the same type detectable.
     */
    private val changed = SettingsUiState(
        islandEnabled = !SettingsDefaults.ISLAND_ENABLED,
        vibrantWave = !SettingsDefaults.VIBRANT_WAVE,
        topOffsetDp = SettingsDefaults.TOP_OFFSET_DP + 7,
        hideDelayMs = SettingsDefaults.HIDE_DELAY_MS + 1_234L,
        positionKey = SettingsDefaults.POSITION + "-changed",
        homeOnly = !SettingsDefaults.HOME_ONLY,
        thumbShape = SettingsDefaults.THUMB_SHAPE + "-changed",
        waveColorMode = SettingsDefaults.WAVE_COLOR_MODE + "-changed",
        waveStyle = SettingsDefaults.WAVE_STYLE + "-changed",
        consumeProgress = !SettingsDefaults.CONSUME_PROGRESS,
        pillTextWidthDp = SettingsDefaults.PILL_TEXT_WIDTH_DP + 11,
        panelWidthDp = SettingsDefaults.PANEL_WIDTH_DP + 13,
        hideNotification = !SettingsDefaults.HIDE_NOTIFICATION,
        artCrossfade = !SettingsDefaults.ART_CROSSFADE,
        crossfadeMs = SettingsDefaults.CROSSFADE_MS + 17,
        showPillControls = !SettingsDefaults.SHOW_PILL_CONTROLS,
        pillControlSet = SettingsDefaults.PILL_CONTROL_SET + "-changed",
        pillControlPosition = SettingsDefaults.PILL_CONTROL_POSITION + "-changed",
    )

    /**
     * Which state field each stored key is supposed to drive, written out
     * independently of the table under test. Deriving this from the bindings would
     * make the test agree with whatever they happen to do.
     */
    private val fieldForKey = mapOf(
        SettingsDefaults.KEY_ISLAND_ENABLED to "islandEnabled",
        SettingsDefaults.KEY_VIBRANT_WAVE to "vibrantWave",
        SettingsDefaults.KEY_TOP_OFFSET_DP to "topOffsetDp",
        SettingsDefaults.KEY_HIDE_DELAY_MS to "hideDelayMs",
        SettingsDefaults.KEY_POSITION to "positionKey",
        SettingsDefaults.KEY_HOME_ONLY to "homeOnly",
        SettingsDefaults.KEY_THUMB_SHAPE to "thumbShape",
        SettingsDefaults.KEY_WAVE_COLOR_MODE to "waveColorMode",
        SettingsDefaults.KEY_WAVE_STYLE to "waveStyle",
        SettingsDefaults.KEY_CONSUME_PROGRESS to "consumeProgress",
        SettingsDefaults.KEY_PILL_TEXT_WIDTH_DP to "pillTextWidthDp",
        SettingsDefaults.KEY_PANEL_WIDTH_DP to "panelWidthDp",
        SettingsDefaults.KEY_HIDE_NOTIFICATION to "hideNotification",
        SettingsDefaults.KEY_ART_CROSSFADE to "artCrossfade",
        SettingsDefaults.KEY_CROSSFADE_MS to "crossfadeMs",
        SettingsDefaults.KEY_SHOW_PILL_CONTROLS to "showPillControls",
        SettingsDefaults.KEY_PILL_CONTROL_SET to "pillControlSet",
        SettingsDefaults.KEY_PILL_CONTROL_POSITION to "pillControlPosition",
    )

    @Test
    fun `the expected field roster names every stored key`() {
        assertEquals(SettingsDefaults.ALL_KEYS.toSet(), fieldForKey.keys)
    }

    @Test
    fun `every setting differs from its default in the probe state`() {
        // Without this the next test would pass vacuously for any setting whose probe
        // value happened to equal its default.
        val base = SettingsUiState()
        val settingNames = fieldForKey.values.toSet()
        val same = settingFields()
            .filter { it.name in settingNames && it.get(base) == it.get(changed) }
        assertEquals(emptyList<String>(), same.map { it.name })
    }

    @Test
    fun `each binding drives exactly the field its key names`() {
        // The hole this closes: two folds swapped between settings of the same type
        // type-checks and keeps both the count and the key roster intact, so every
        // other test in this file passes while two controls drive each other's
        // setting. Verified by mutation - swapping the thumb-shape and wave-style
        // folds survived the suite until this test existed.
        val base = SettingsUiState()
        val fields = settingFields()
        for (binding in SettingsBindings.ALL) {
            val folded = binding.copySetting(base, changed)
            val expected = fieldForKey.getValue(binding.key)

            val touched = fields.filter { it.get(base) != it.get(folded) }.map { it.name }
            assertEquals("binding for '${binding.key}' touched the wrong field(s)", listOf(expected), touched)

            val field = fields.single { it.name == expected }
            assertEquals(
                "binding for '${binding.key}' carried the wrong value into $expected",
                field.get(changed), field.get(folded),
            )
        }
    }

    private fun settingFields() = SettingsUiState::class.java.declaredFields
        .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
        .onEach { it.isAccessible = true }
}
