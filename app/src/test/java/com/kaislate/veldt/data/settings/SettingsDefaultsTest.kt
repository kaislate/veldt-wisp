package com.kaislate.veldt.data.settings

import com.kaislate.veldt.util.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the on-disk settings contract.
 *
 * Every key string below is written out as a literal on purpose. Reading a constant
 * back from the same constant would pass no matter what it was changed to; these
 * strings name records inside the user's DataStore file, so the test has to state
 * independently what those records are called. Renaming one is not a refactor — it
 * is a silent factory reset of that single setting.
 *
 * The default values are asserted the same way, and at the type they are declared
 * at, because the defaults are also what the settings screen and the overlay fall
 * back to before the first read completes.
 */
class SettingsDefaultsTest {

    // ---- the datastore file itself -------------------------------------------------

    @Test
    fun `datastore file is named settings`() {
        assertEquals("settings", SettingsDefaults.DATASTORE_NAME)
    }

    // ---- key strings ---------------------------------------------------------------

    @Test
    fun `boolean toggle keys are unchanged`() {
        assertEquals("island_enabled", SettingsDefaults.KEY_ISLAND_ENABLED)
        assertEquals("vibrant_wave", SettingsDefaults.KEY_VIBRANT_WAVE)
        assertEquals("home_only", SettingsDefaults.KEY_HOME_ONLY)
        assertEquals("consume_progress", SettingsDefaults.KEY_CONSUME_PROGRESS)
        assertEquals("hide_notification", SettingsDefaults.KEY_HIDE_NOTIFICATION)
        assertEquals("art_crossfade", SettingsDefaults.KEY_ART_CROSSFADE)
        assertEquals("show_pill_controls", SettingsDefaults.KEY_SHOW_PILL_CONTROLS)
    }

    @Test
    fun `numeric keys are unchanged`() {
        assertEquals("top_offset_dp", SettingsDefaults.KEY_TOP_OFFSET_DP)
        assertEquals("hide_delay_ms", SettingsDefaults.KEY_HIDE_DELAY_MS)
        assertEquals("pill_text_width_dp", SettingsDefaults.KEY_PILL_TEXT_WIDTH_DP)
        assertEquals("panel_width_dp", SettingsDefaults.KEY_PANEL_WIDTH_DP)
        assertEquals("crossfade_ms", SettingsDefaults.KEY_CROSSFADE_MS)
    }

    @Test
    fun `string choice keys are unchanged`() {
        assertEquals("island_position", SettingsDefaults.KEY_POSITION)
        assertEquals("thumb_shape", SettingsDefaults.KEY_THUMB_SHAPE)
        assertEquals("wave_color_mode", SettingsDefaults.KEY_WAVE_COLOR_MODE)
        assertEquals("wave_style", SettingsDefaults.KEY_WAVE_STYLE)
        assertEquals("pill_control_set", SettingsDefaults.KEY_PILL_CONTROL_SET)
        assertEquals("pill_control_position", SettingsDefaults.KEY_PILL_CONTROL_POSITION)
    }

    // ---- boolean defaults ----------------------------------------------------------

    @Test
    fun `island is enabled on a fresh install`() {
        val enabled: Boolean = SettingsDefaults.ISLAND_ENABLED
        assertTrue(enabled)
    }

    @Test
    fun `vibrant wave starts off`() {
        assertFalse(SettingsDefaults.VIBRANT_WAVE)
    }

    @Test
    fun `home only starts off so the pill is not mysteriously absent`() {
        assertFalse(SettingsDefaults.HOME_ONLY)
    }

    @Test
    fun `progress consumption starts on`() {
        assertTrue(SettingsDefaults.CONSUME_PROGRESS)
    }

    @Test
    fun `notification minimising starts off`() {
        assertFalse(SettingsDefaults.HIDE_NOTIFICATION)
    }

    @Test
    fun `art crossfade starts on`() {
        assertTrue(SettingsDefaults.ART_CROSSFADE)
    }

    @Test
    fun `pill controls start hidden`() {
        assertFalse(SettingsDefaults.SHOW_PILL_CONTROLS)
    }

    // ---- numeric defaults ----------------------------------------------------------

    @Test
    fun `top offset defaults to forty dp`() {
        val dp: Int = SettingsDefaults.TOP_OFFSET_DP
        assertEquals(40, dp)
    }

    @Test
    fun `hide delay defaults to twenty-five thousand milliseconds as a Long`() {
        val ms: Long = SettingsDefaults.HIDE_DELAY_MS
        assertEquals(25_000L, ms)
    }

    @Test
    fun `hide delay default is the same value the state machine uses`() {
        assertEquals(Constants.INACTIVITY_TIMEOUT_MS, SettingsDefaults.HIDE_DELAY_MS)
    }

    @Test
    fun `pill text width defaults to 160 dp not 128`() {
        val dp: Int = SettingsDefaults.PILL_TEXT_WIDTH_DP
        assertEquals(160, dp)
    }

    @Test
    fun `panel width defaults to 400 dp`() {
        val dp: Int = SettingsDefaults.PANEL_WIDTH_DP
        assertEquals(400, dp)
    }

    @Test
    fun `crossfade defaults to 1000 ms not 450`() {
        val ms: Int = SettingsDefaults.CROSSFADE_MS
        assertEquals(1000, ms)
    }

    // ---- string defaults -----------------------------------------------------------

    @Test
    fun `position defaults to top-center`() {
        assertEquals("top-center", SettingsDefaults.POSITION)
    }

    @Test
    fun `thumb shape defaults to bar`() {
        assertEquals("bar", SettingsDefaults.THUMB_SHAPE)
    }

    @Test
    fun `wave colour mode defaults to accent-light`() {
        assertEquals("accent-light", SettingsDefaults.WAVE_COLOR_MODE)
    }

    @Test
    fun `wave style defaults to wisptrail and not hills`() {
        assertEquals("wisptrail", SettingsDefaults.WAVE_STYLE)
    }

    @Test
    fun `pill control set defaults to play-next`() {
        assertEquals("play-next", SettingsDefaults.PILL_CONTROL_SET)
    }

    @Test
    fun `pill control position defaults to right`() {
        assertEquals("right", SettingsDefaults.PILL_CONTROL_POSITION)
    }

    // ---- the table as a whole ------------------------------------------------------

    @Test
    fun `there are exactly eighteen settings`() {
        assertEquals(18, SettingsDefaults.ALL_KEYS.size)
    }

    @Test
    fun `no two settings share a key`() {
        assertEquals(18, SettingsDefaults.ALL_KEYS.toSet().size)
    }

    @Test
    fun `the key roster lists every declared key`() {
        val declared = listOf(
            SettingsDefaults.KEY_ISLAND_ENABLED,
            SettingsDefaults.KEY_VIBRANT_WAVE,
            SettingsDefaults.KEY_TOP_OFFSET_DP,
            SettingsDefaults.KEY_HIDE_DELAY_MS,
            SettingsDefaults.KEY_POSITION,
            SettingsDefaults.KEY_HOME_ONLY,
            SettingsDefaults.KEY_THUMB_SHAPE,
            SettingsDefaults.KEY_WAVE_COLOR_MODE,
            SettingsDefaults.KEY_WAVE_STYLE,
            SettingsDefaults.KEY_CONSUME_PROGRESS,
            SettingsDefaults.KEY_PILL_TEXT_WIDTH_DP,
            SettingsDefaults.KEY_PANEL_WIDTH_DP,
            SettingsDefaults.KEY_HIDE_NOTIFICATION,
            SettingsDefaults.KEY_ART_CROSSFADE,
            SettingsDefaults.KEY_CROSSFADE_MS,
            SettingsDefaults.KEY_SHOW_PILL_CONTROLS,
            SettingsDefaults.KEY_PILL_CONTROL_SET,
            SettingsDefaults.KEY_PILL_CONTROL_POSITION,
        )
        assertEquals(declared.toSet(), SettingsDefaults.ALL_KEYS.toSet())
    }
}
