package com.kaislate.veldt.data.settings

import com.kaislate.veldt.util.Constants

/**
 * The single source of truth for what the app stores and what it falls back to.
 *
 * Every string in here names a record inside the user's DataStore file, so the
 * strings are frozen. There is no migration step and no error path: rename a key
 * and the setting it belonged to quietly reverts to its default on the next
 * launch, for everyone who had already changed it. Some of the names — including
 * `island_enabled`, `wave_style` and the datastore file name `settings` — are the
 * same as the ones the upstream project used. They stay that way deliberately.
 * They carry no design in them, they are only labels on disk, and rewording them
 * would cost users their settings to gain nothing.
 *
 * The defaults live here rather than at each call site so the settings screen and
 * the overlay cannot disagree about what "unset" looks like. Both read this table.
 *
 * This object is deliberately free of Android types so it can be unit-tested
 * directly; [SettingsRepository] is the piece that turns it into typed DataStore
 * keys.
 */
object SettingsDefaults {

    /** Name of the DataStore Preferences file, i.e. `settings.preferences_pb`. */
    const val DATASTORE_NAME = "settings"

    // ---- keys ----------------------------------------------------------------------

    const val KEY_ISLAND_ENABLED = "island_enabled"
    const val KEY_VIBRANT_WAVE = "vibrant_wave"
    const val KEY_TOP_OFFSET_DP = "top_offset_dp"
    const val KEY_HIDE_DELAY_MS = "hide_delay_ms"
    const val KEY_POSITION = "island_position"
    const val KEY_HOME_ONLY = "home_only"
    const val KEY_THUMB_SHAPE = "thumb_shape"
    const val KEY_WAVE_COLOR_MODE = "wave_color_mode"
    const val KEY_WAVE_STYLE = "wave_style"
    const val KEY_CONSUME_PROGRESS = "consume_progress"
    const val KEY_PILL_TEXT_WIDTH_DP = "pill_text_width_dp"
    const val KEY_PANEL_WIDTH_DP = "panel_width_dp"
    const val KEY_HIDE_NOTIFICATION = "hide_notification"
    const val KEY_ART_CROSSFADE = "art_crossfade"
    const val KEY_CROSSFADE_MS = "crossfade_ms"
    const val KEY_SHOW_PILL_CONTROLS = "show_pill_controls"
    const val KEY_PILL_CONTROL_SET = "pill_control_set"
    const val KEY_PILL_CONTROL_POSITION = "pill_control_position"

    // ---- defaults ------------------------------------------------------------------

    /** On, because a freshly installed overlay app that shows nothing reads as broken. */
    const val ISLAND_ENABLED = true

    /** Off: the saturated wave is a taste, the calm one is the house style. */
    const val VIBRANT_WAVE = false

    /** Distance from the top edge, in dp, when the pill sits along the top. */
    const val TOP_OFFSET_DP = 40

    /**
     * How long a paused pill lingers before hiding itself, in milliseconds.
     * Shares [Constants.INACTIVITY_TIMEOUT_MS] with the state machine so the
     * stored value and the built-in timeout cannot drift apart.
     */
    const val HIDE_DELAY_MS = Constants.INACTIVITY_TIMEOUT_MS

    /** Where the pill docks; `top-center` is the Now-Bar-like placement. */
    const val POSITION = "top-center"

    /** Off: this narrows the pill to the launcher only, and defaulting it on would look like the app failing to appear. */
    const val HOME_ONLY = false

    /** Shape of the album-art thumbnail on the collapsed pill. */
    const val THUMB_SHAPE = "bar"

    /** Where the wave takes its colour from. */
    const val WAVE_COLOR_MODE = "accent-light"

    /** The wave rendering. `wisptrail` is the default look — not `hills`. */
    const val WAVE_STYLE = "wisptrail"

    /** On: the wave doubles as a progress readout unless the user opts out. */
    const val CONSUME_PROGRESS = true

    /** Width budget, in dp, for the title/artist text on the collapsed pill. */
    const val PILL_TEXT_WIDTH_DP = 160

    /** Width, in dp, of the expanded panel. */
    const val PANEL_WIDTH_DP = 400

    /** Off: the ongoing notification stays at full size unless the user shrinks it. */
    const val HIDE_NOTIFICATION = false

    /** On: album art fades between tracks rather than cutting. Opt-out, like the rest of the polish. */
    const val ART_CROSSFADE = true

    /** Length of that fade, in milliseconds. */
    const val CROSSFADE_MS = 1000

    /** Off: the minimal pill, with no transport buttons, is the intended default look. */
    const val SHOW_PILL_CONTROLS = false

    /** Which transport buttons appear once controls are switched on. */
    const val PILL_CONTROL_SET = "play-next"

    /** Which side of the pill those buttons sit on. */
    const val PILL_CONTROL_POSITION = "right"

    /**
     * Every stored key, for tests that check the table as a whole: that it has the
     * expected number of entries and that no two settings accidentally share a name.
     * Two settings pointing at one key would overwrite each other with no symptom
     * other than the settings screen behaving strangely.
     */
    val ALL_KEYS: List<String> = listOf(
        KEY_ISLAND_ENABLED,
        KEY_VIBRANT_WAVE,
        KEY_TOP_OFFSET_DP,
        KEY_HIDE_DELAY_MS,
        KEY_POSITION,
        KEY_HOME_ONLY,
        KEY_THUMB_SHAPE,
        KEY_WAVE_COLOR_MODE,
        KEY_WAVE_STYLE,
        KEY_CONSUME_PROGRESS,
        KEY_PILL_TEXT_WIDTH_DP,
        KEY_PANEL_WIDTH_DP,
        KEY_HIDE_NOTIFICATION,
        KEY_ART_CROSSFADE,
        KEY_CROSSFADE_MS,
        KEY_SHOW_PILL_CONTROLS,
        KEY_PILL_CONTROL_SET,
        KEY_PILL_CONTROL_POSITION,
    )
}
