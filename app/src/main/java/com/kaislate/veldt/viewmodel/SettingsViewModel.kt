// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaislate.veldt.BuildConfig
import com.kaislate.veldt.data.settings.SettingsDefaults
import com.kaislate.veldt.data.settings.SettingsRepository
import com.kaislate.veldt.update.UpdateChecker
import com.kaislate.veldt.util.PermissionsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Everything the settings screen draws.
 *
 * The eighteen settings default to [SettingsDefaults] rather than to a second set of
 * literals. The screen renders this state before the first DataStore emission
 * arrives, so if these defaults disagreed with the stored contract every control
 * would show one value for a frame and then swap to another.
 *
 * The five permission flags default asymmetrically, and deliberately. The three
 * genuinely required grants start pessimistic — assume absent until
 * [SettingsViewModel.refreshPermissions] has actually looked — because claiming a
 * permission is held when it is not means the screen hides the button that fixes it.
 * The two version-gated ones start optimistic, because they do not exist below
 * API 33 and API 31 respectively, where "granted" is the correct and permanent
 * answer. Flattening all five to `false` would put two warnings on the screen for a
 * frame on every launch, on every device that cannot even be asked.
 */
data class SettingsUiState(
    val islandEnabled: Boolean = SettingsDefaults.ISLAND_ENABLED,
    val vibrantWave: Boolean = SettingsDefaults.VIBRANT_WAVE,
    val topOffsetDp: Int = SettingsDefaults.TOP_OFFSET_DP,
    val hideDelayMs: Long = SettingsDefaults.HIDE_DELAY_MS,
    /** Named for the stored key rather than the setting; the screen reads `ui.positionKey`. */
    val positionKey: String = SettingsDefaults.POSITION,
    val homeOnly: Boolean = SettingsDefaults.HOME_ONLY,
    val thumbShape: String = SettingsDefaults.THUMB_SHAPE,
    val waveColorMode: String = SettingsDefaults.WAVE_COLOR_MODE,
    val waveStyle: String = SettingsDefaults.WAVE_STYLE,
    val consumeProgress: Boolean = SettingsDefaults.CONSUME_PROGRESS,
    val pillTextWidthDp: Int = SettingsDefaults.PILL_TEXT_WIDTH_DP,
    val panelWidthDp: Int = SettingsDefaults.PANEL_WIDTH_DP,
    val hideNotification: Boolean = SettingsDefaults.HIDE_NOTIFICATION,
    val artCrossfade: Boolean = SettingsDefaults.ART_CROSSFADE,
    val crossfadeMs: Int = SettingsDefaults.CROSSFADE_MS,
    val showPillControls: Boolean = SettingsDefaults.SHOW_PILL_CONTROLS,
    val pillControlSet: String = SettingsDefaults.PILL_CONTROL_SET,
    val pillControlPosition: String = SettingsDefaults.PILL_CONTROL_POSITION,

    val overlayGranted: Boolean = false,
    val notifListenerGranted: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val postNotificationsGranted: Boolean = true,
    val bluetoothConnectGranted: Boolean = true,
)

/**
 * One stored setting's round trip: where to read it from and where it lands in the
 * state.
 *
 * [key] is the DataStore record this observes. It is not used to read anything — the
 * repository already owns the typed keys — it is here so the table can be checked
 * against [SettingsDefaults.ALL_KEYS] as a whole. A setting dropped from this list
 * still saves correctly and then never reappears on screen, which is the kind of bug
 * that survives every other test.
 *
 * The element type is erased to `SettingsBinding<*>` in the list, but nothing ever
 * casts: `T` appears only inside [launchInto], so the star projection is enough to
 * call it. That is the point of routing the flow through this class rather than
 * combining eighteen flows positionally.
 */
internal class SettingsBinding<T>(
    val key: String,
    private val select: (SettingsRepository) -> Flow<T>,
    private val read: (SettingsUiState) -> T,
    private val fold: (SettingsUiState, T) -> SettingsUiState,
) {
    fun launchInto(
        repo: SettingsRepository,
        scope: CoroutineScope,
        sink: MutableStateFlow<SettingsUiState>,
    ) {
        select(repo)
            .onEach { value -> sink.update { state -> fold(state, value) } }
            .launchIn(scope)
    }

    /**
     * Copies this one setting's value out of [from] and into [into].
     *
     * [read] exists for this: it makes each entry a complete lens over its setting
     * rather than a write-only half, which is what lets a test prove that a binding
     * reads and writes the field its [key] names. Without it the table can only be
     * counted, and two folds swapped between two settings of the same type — the
     * thumb-shape flow landing in `waveStyle` — type-checks, keeps the count and the
     * key roster intact, and silently wires two controls to each other's setting.
     */
    fun copySetting(into: SettingsUiState, from: SettingsUiState): SettingsUiState =
        fold(into, read(from))
}

/**
 * All eighteen settings, one entry each.
 *
 * Collecting each flow independently rather than combining them: `combine`'s typed
 * overloads stop at five sources, and eighteen of anything is a list, not an
 * argument list.
 */
internal object SettingsBindings {
    val ALL: List<SettingsBinding<*>> = listOf(
        SettingsBinding(
            SettingsDefaults.KEY_ISLAND_ENABLED, { it.islandEnabledFlow }, { it.islandEnabled },
        ) { s, v -> s.copy(islandEnabled = v) },
        SettingsBinding(
            SettingsDefaults.KEY_VIBRANT_WAVE, { it.vibrantWaveFlow }, { it.vibrantWave },
        ) { s, v -> s.copy(vibrantWave = v) },
        SettingsBinding(
            SettingsDefaults.KEY_TOP_OFFSET_DP, { it.topOffsetDpFlow }, { it.topOffsetDp },
        ) { s, v -> s.copy(topOffsetDp = v) },
        SettingsBinding(
            SettingsDefaults.KEY_HIDE_DELAY_MS, { it.hideDelayMsFlow }, { it.hideDelayMs },
        ) { s, v -> s.copy(hideDelayMs = v) },
        SettingsBinding(
            SettingsDefaults.KEY_POSITION, { it.positionFlow }, { it.positionKey },
        ) { s, v -> s.copy(positionKey = v) },
        SettingsBinding(
            SettingsDefaults.KEY_HOME_ONLY, { it.homeOnlyFlow }, { it.homeOnly },
        ) { s, v -> s.copy(homeOnly = v) },
        SettingsBinding(
            SettingsDefaults.KEY_THUMB_SHAPE, { it.thumbShapeFlow }, { it.thumbShape },
        ) { s, v -> s.copy(thumbShape = v) },
        SettingsBinding(
            SettingsDefaults.KEY_WAVE_COLOR_MODE, { it.waveColorModeFlow }, { it.waveColorMode },
        ) { s, v -> s.copy(waveColorMode = v) },
        SettingsBinding(
            SettingsDefaults.KEY_WAVE_STYLE, { it.waveStyleFlow }, { it.waveStyle },
        ) { s, v -> s.copy(waveStyle = v) },
        SettingsBinding(
            SettingsDefaults.KEY_CONSUME_PROGRESS, { it.consumeProgressFlow }, { it.consumeProgress },
        ) { s, v -> s.copy(consumeProgress = v) },
        SettingsBinding(
            SettingsDefaults.KEY_PILL_TEXT_WIDTH_DP, { it.pillTextWidthDpFlow }, { it.pillTextWidthDp },
        ) { s, v -> s.copy(pillTextWidthDp = v) },
        SettingsBinding(
            SettingsDefaults.KEY_PANEL_WIDTH_DP, { it.panelWidthDpFlow }, { it.panelWidthDp },
        ) { s, v -> s.copy(panelWidthDp = v) },
        SettingsBinding(
            SettingsDefaults.KEY_HIDE_NOTIFICATION, { it.hideNotificationFlow }, { it.hideNotification },
        ) { s, v -> s.copy(hideNotification = v) },
        SettingsBinding(
            SettingsDefaults.KEY_ART_CROSSFADE, { it.artCrossfadeFlow }, { it.artCrossfade },
        ) { s, v -> s.copy(artCrossfade = v) },
        SettingsBinding(
            SettingsDefaults.KEY_CROSSFADE_MS, { it.crossfadeMsFlow }, { it.crossfadeMs },
        ) { s, v -> s.copy(crossfadeMs = v) },
        SettingsBinding(
            SettingsDefaults.KEY_SHOW_PILL_CONTROLS, { it.showPillControlsFlow }, { it.showPillControls },
        ) { s, v -> s.copy(showPillControls = v) },
        SettingsBinding(
            SettingsDefaults.KEY_PILL_CONTROL_SET, { it.pillControlSetFlow }, { it.pillControlSet },
        ) { s, v -> s.copy(pillControlSet = v) },
        SettingsBinding(
            SettingsDefaults.KEY_PILL_CONTROL_POSITION, { it.pillControlPositionFlow }, { it.pillControlPosition },
        ) { s, v -> s.copy(pillControlPosition = v) },
    )
}

/**
 * Backs the settings screen: the stored settings, the permission checklist and the
 * in-app updater.
 *
 * Every setter here is write-only. None of them touches [ui]; they hand the value to
 * the repository and stop. The displayed value comes back the long way round —
 * setter, DataStore write, flow emission, [SettingsBindings] fold — which is what
 * guarantees the screen can never show a setting that failed to persist. A setter
 * that also wrote the state optimistically would make a failed write invisible.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    app: Application,
    private val repo: SettingsRepository,
) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    init {
        SettingsBindings.ALL.forEach { it.launchInto(repo, viewModelScope, _ui) }
        // After observation is wired, so the first permission answers are never
        // overwritten by a settings emission carrying stale flags.
        refreshPermissions()
    }

    // ---- permissions -------------------------------------------------------------------

    /**
     * Re-reads all five permission grants.
     *
     * Synchronous on purpose. The screen calls this from a lifecycle callback when it
     * resumes — typically returning from the very system settings page that granted
     * the permission — and the state has to be right for the next frame. Launching it
     * would show the pre-grant checklist for a frame, which looks like the grant
     * silently failing.
     *
     * All five land in a single update so the checklist can never be rendered
     * half-refreshed.
     */
    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        _ui.update {
            it.copy(
                overlayGranted = PermissionsHelper.hasOverlayPermission(ctx),
                notifListenerGranted = PermissionsHelper.hasNotificationListener(ctx),
                usageAccessGranted = PermissionsHelper.hasUsageAccess(ctx),
                postNotificationsGranted = PermissionsHelper.hasPostNotifications(ctx),
                bluetoothConnectGranted = PermissionsHelper.hasBluetoothConnect(ctx),
            )
        }
    }

    // ---- the updater -------------------------------------------------------------------

    /** Asks GitHub whether there is a newer release than this build. */
    fun checkForUpdates() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            _updateState.value = try {
                UpdateRules.afterCheck(UpdateChecker.check(BuildConfig.VERSION_NAME))
            } catch (e: Exception) {
                UpdateRules.afterCheckFailure(e.message)
            }
        }
    }

    /**
     * Acts on the About card's install control, from whichever state it is in.
     *
     * The two live branches use different concurrency deliberately. Downloading an
     * APK is genuinely long-running and is launched; re-offering one that is already
     * on disk only fires an intent, so it runs inline and the caller sees any failure
     * reflected in [updateState] immediately.
     */
    fun installUpdate(ctx: Context) {
        val current = _updateState.value
        when (UpdateRules.installAction(current)) {
            InstallAction.DOWNLOAD_THEN_INSTALL -> if (current is UpdateState.Available) {
                val info = current.info
                viewModelScope.launch {
                    _updateState.value = UpdateState.Downloading
                    _updateState.value = try {
                        val file = UpdateChecker.download(ctx, info)
                        // The installer's answer is deliberately discarded. It returns
                        // false when "install unknown apps" is not granted for this
                        // app, having sent the user to the grant screen instead. That
                        // is not a failure of the download, and moving to Downloaded
                        // regardless is exactly what lets the user come back, tap
                        // Install again and have it work without a second fetch.
                        UpdateChecker.install(ctx, file)
                        UpdateRules.afterDownload(info, file)
                    } catch (e: Exception) {
                        UpdateRules.afterDownloadFailure(e.message)
                    }
                }
            }

            InstallAction.INSTALL_EXISTING -> if (current is UpdateState.Downloaded) {
                try {
                    UpdateChecker.install(ctx, current.file)
                    // No state change on success: handing the APK to the system
                    // installer is the end of this app's involvement, and there is no
                    // "installed" state to move to. Staying in Downloaded also keeps
                    // the Install control available for another attempt if the user
                    // backs out of the installer.
                } catch (e: Exception) {
                    _updateState.value = UpdateRules.afterInstallFailure(e.message)
                }
            }

            InstallAction.NONE -> Unit
        }
    }

    // ---- settings ----------------------------------------------------------------------

    /**
     * Clears every stored preference. Nothing is written back: the repository empties
     * the store, the flows fall through to [SettingsDefaults] and the observation
     * above carries them onto the screen.
     */
    fun resetToDefaults() {
        viewModelScope.launch { repo.resetToDefaults() }
    }

    fun setIslandEnabled(value: Boolean) = write { repo.setIslandEnabled(value) }
    fun setVibrantWave(value: Boolean) = write { repo.setVibrantWave(value) }
    fun setTopOffsetDp(dp: Int) = write { repo.setTopOffsetDp(dp) }
    fun setHideDelayMs(ms: Long) = write { repo.setHideDelayMs(ms) }
    fun setPosition(key: String) = write { repo.setPosition(key) }
    fun setHomeOnly(value: Boolean) = write { repo.setHomeOnly(value) }
    fun setThumbShape(value: String) = write { repo.setThumbShape(value) }
    fun setWaveColorMode(value: String) = write { repo.setWaveColorMode(value) }
    fun setWaveStyle(value: String) = write { repo.setWaveStyle(value) }
    fun setConsumeProgress(value: Boolean) = write { repo.setConsumeProgress(value) }
    fun setPillTextWidthDp(dp: Int) = write { repo.setPillTextWidthDp(dp) }
    fun setPanelWidthDp(dp: Int) = write { repo.setPanelWidthDp(dp) }
    fun setHideNotification(hide: Boolean) = write { repo.setHideNotification(hide) }
    fun setArtCrossfade(on: Boolean) = write { repo.setArtCrossfade(on) }
    fun setCrossfadeMs(ms: Int) = write { repo.setCrossfadeMs(ms) }
    fun setShowPillControls(on: Boolean) = write { repo.setShowPillControls(on) }
    fun setPillControlSet(set: String) = write { repo.setPillControlSet(set) }
    fun setPillControlPosition(pos: String) = write { repo.setPillControlPosition(pos) }

    /** Runs one repository write on the view-model scope. The state is not touched. */
    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
