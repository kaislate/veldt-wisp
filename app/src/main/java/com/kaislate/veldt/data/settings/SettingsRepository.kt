// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The one DataStore this app owns. The library requires the delegate at file scope. */
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(
    name = SettingsDefaults.DATASTORE_NAME,
)

/**
 * Reads and writes the user's settings.
 *
 * Deliberately dumb: each setting is one flow that reports the stored value or the
 * [SettingsDefaults] fallback, and one setter that stores whatever it is handed.
 * Nothing is clamped or validated on the way in — the settings screen already
 * restricts what the user can pick, and quietly correcting a value here would mean
 * the app disagreeing with the number the user is looking at.
 *
 * Keys and defaults all come from [SettingsDefaults] rather than being spelled out
 * again here, so there is exactly one place where the on-disk contract lives.
 */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {

    private val store = context.settingsStore

    private object Keys {
        val islandEnabled = booleanPreferencesKey(SettingsDefaults.KEY_ISLAND_ENABLED)
        val vibrantWave = booleanPreferencesKey(SettingsDefaults.KEY_VIBRANT_WAVE)
        val topOffsetDp = intPreferencesKey(SettingsDefaults.KEY_TOP_OFFSET_DP)
        val hideDelayMs = longPreferencesKey(SettingsDefaults.KEY_HIDE_DELAY_MS)
        val position = stringPreferencesKey(SettingsDefaults.KEY_POSITION)
        val homeOnly = booleanPreferencesKey(SettingsDefaults.KEY_HOME_ONLY)
        val thumbShape = stringPreferencesKey(SettingsDefaults.KEY_THUMB_SHAPE)
        val waveColorMode = stringPreferencesKey(SettingsDefaults.KEY_WAVE_COLOR_MODE)
        val waveStyle = stringPreferencesKey(SettingsDefaults.KEY_WAVE_STYLE)
        val consumeProgress = booleanPreferencesKey(SettingsDefaults.KEY_CONSUME_PROGRESS)
        val pillTextWidthDp = intPreferencesKey(SettingsDefaults.KEY_PILL_TEXT_WIDTH_DP)
        val panelWidthDp = intPreferencesKey(SettingsDefaults.KEY_PANEL_WIDTH_DP)
        val hideNotification = booleanPreferencesKey(SettingsDefaults.KEY_HIDE_NOTIFICATION)
        val artCrossfade = booleanPreferencesKey(SettingsDefaults.KEY_ART_CROSSFADE)
        val crossfadeMs = intPreferencesKey(SettingsDefaults.KEY_CROSSFADE_MS)
        val showPillControls = booleanPreferencesKey(SettingsDefaults.KEY_SHOW_PILL_CONTROLS)
        val pillControlSet = stringPreferencesKey(SettingsDefaults.KEY_PILL_CONTROL_SET)
        val pillControlPosition = stringPreferencesKey(SettingsDefaults.KEY_PILL_CONTROL_POSITION)
    }

    /** Stored value for [key], or [fallback] while it has never been written. */
    private fun <T> watch(key: Preferences.Key<T>, fallback: T): Flow<T> =
        store.data.map { stored -> stored[key] ?: fallback }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    // ---- reads ---------------------------------------------------------------------

    val islandEnabledFlow: Flow<Boolean> =
        watch(Keys.islandEnabled, SettingsDefaults.ISLAND_ENABLED)
    val vibrantWaveFlow: Flow<Boolean> =
        watch(Keys.vibrantWave, SettingsDefaults.VIBRANT_WAVE)
    val topOffsetDpFlow: Flow<Int> =
        watch(Keys.topOffsetDp, SettingsDefaults.TOP_OFFSET_DP)
    val hideDelayMsFlow: Flow<Long> =
        watch(Keys.hideDelayMs, SettingsDefaults.HIDE_DELAY_MS)
    val positionFlow: Flow<String> =
        watch(Keys.position, SettingsDefaults.POSITION)
    val homeOnlyFlow: Flow<Boolean> =
        watch(Keys.homeOnly, SettingsDefaults.HOME_ONLY)
    val thumbShapeFlow: Flow<String> =
        watch(Keys.thumbShape, SettingsDefaults.THUMB_SHAPE)
    val waveColorModeFlow: Flow<String> =
        watch(Keys.waveColorMode, SettingsDefaults.WAVE_COLOR_MODE)
    val waveStyleFlow: Flow<String> =
        watch(Keys.waveStyle, SettingsDefaults.WAVE_STYLE)
    val consumeProgressFlow: Flow<Boolean> =
        watch(Keys.consumeProgress, SettingsDefaults.CONSUME_PROGRESS)
    val pillTextWidthDpFlow: Flow<Int> =
        watch(Keys.pillTextWidthDp, SettingsDefaults.PILL_TEXT_WIDTH_DP)
    val panelWidthDpFlow: Flow<Int> =
        watch(Keys.panelWidthDp, SettingsDefaults.PANEL_WIDTH_DP)
    val hideNotificationFlow: Flow<Boolean> =
        watch(Keys.hideNotification, SettingsDefaults.HIDE_NOTIFICATION)
    val artCrossfadeFlow: Flow<Boolean> =
        watch(Keys.artCrossfade, SettingsDefaults.ART_CROSSFADE)
    val crossfadeMsFlow: Flow<Int> =
        watch(Keys.crossfadeMs, SettingsDefaults.CROSSFADE_MS)
    val showPillControlsFlow: Flow<Boolean> =
        watch(Keys.showPillControls, SettingsDefaults.SHOW_PILL_CONTROLS)
    val pillControlSetFlow: Flow<String> =
        watch(Keys.pillControlSet, SettingsDefaults.PILL_CONTROL_SET)
    val pillControlPositionFlow: Flow<String> =
        watch(Keys.pillControlPosition, SettingsDefaults.PILL_CONTROL_POSITION)

    // ---- writes --------------------------------------------------------------------

    suspend fun setIslandEnabled(value: Boolean) = put(Keys.islandEnabled, value)
    suspend fun setVibrantWave(value: Boolean) = put(Keys.vibrantWave, value)
    suspend fun setTopOffsetDp(value: Int) = put(Keys.topOffsetDp, value)
    suspend fun setHideDelayMs(value: Long) = put(Keys.hideDelayMs, value)
    suspend fun setPosition(value: String) = put(Keys.position, value)
    suspend fun setHomeOnly(value: Boolean) = put(Keys.homeOnly, value)
    suspend fun setThumbShape(value: String) = put(Keys.thumbShape, value)
    suspend fun setWaveColorMode(value: String) = put(Keys.waveColorMode, value)
    suspend fun setWaveStyle(value: String) = put(Keys.waveStyle, value)
    suspend fun setConsumeProgress(value: Boolean) = put(Keys.consumeProgress, value)
    suspend fun setPillTextWidthDp(value: Int) = put(Keys.pillTextWidthDp, value)
    suspend fun setPanelWidthDp(value: Int) = put(Keys.panelWidthDp, value)
    suspend fun setHideNotification(value: Boolean) = put(Keys.hideNotification, value)
    suspend fun setArtCrossfade(value: Boolean) = put(Keys.artCrossfade, value)
    suspend fun setCrossfadeMs(value: Int) = put(Keys.crossfadeMs, value)
    suspend fun setShowPillControls(value: Boolean) = put(Keys.showPillControls, value)
    suspend fun setPillControlSet(value: String) = put(Keys.pillControlSet, value)
    suspend fun setPillControlPosition(value: String) = put(Keys.pillControlPosition, value)

    /**
     * Wipes every stored preference so all the flows above fall back to
     * [SettingsDefaults]. It clears rather than writing the defaults back one by one,
     * which means a key introduced in some later version is still cleared by this
     * code — a hand-written list would silently miss it.
     */
    suspend fun resetToDefaults() {
        store.edit { it.clear() }
    }
}
