// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaislate.veldt.BuildConfig
import com.kaislate.veldt.util.PermissionsHelper
import com.kaislate.veldt.viewmodel.SettingsUiState
import com.kaislate.veldt.viewmodel.SettingsViewModel
import com.kaislate.veldt.viewmodel.UpdateState

/** Where the About card's link button points. */
private const val PROJECT_URL = "https://github.com/kaislate/veldt-wisp"

/**
 * The app's only screen: every stored setting, the permission checklist and the
 * in-app updater.
 *
 * All option tables and slider ranges live in [SettingsOptions] rather than inline,
 * so the parts of this screen that can be *wrong* — a chip key that no longer matches
 * what is stored, a range that excludes its own default — are unit-tested. What is
 * left here is layout, and layout is what the device check is for.
 *
 * Nothing in this file holds state of its own beyond the reset dialog's visibility.
 * Every control reads [SettingsViewModel.ui] and writes through a setter, so what is
 * drawn is always what was persisted rather than an optimistic local copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()

    // Collected once. The About card renders it and the scroll effect below keys on
    // it; collecting it twice would be two subscriptions to one flow for no gain.
    val updateState by vm.updateState.collectAsState()

    val scrollState = rememberScrollState()

    /*
     * Permission grants happen in the system Settings app, which gives us no callback,
     * so the state has to be re-read when the user comes back. ON_RESUME is exactly
     * that moment. The alternative — polling once a second for as long as the screen
     * is composed — approximates the same event while leaving a timer running the
     * whole time the screen simply sits there.
     *
     * The two launchers below still refresh explicitly: an in-app runtime dialog is
     * not an activity change, so it produces no resume.
     */
    // compose-ui's LocalLifecycleOwner is deprecated in favour of the identical one in
    // lifecycle-runtime-compose, which this module deliberately does not depend on --
    // see the commented-out line in build.gradle.kts. The two resolve to the same
    // owner; taking the warning is cheaper than taking the dependency.
    @Suppress("DEPRECATION")
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The About card sits near the bottom. A newly revealed Download or Install button
    // would otherwise appear below the fold, looking as though the tap did nothing.
    LaunchedEffect(updateState) {
        if (updateState !is UpdateState.Idle) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Veldt Wisp") },
                windowInsets = WindowInsets.statusBars,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader("General")

            SwitchCard(
                title = "Enable Veldt Wisp",
                // The summary is the only place the off state is explained, so it says
                // what actually stops rather than repeating the title.
                summary = if (ui.islandEnabled) {
                    "The island appears while music is playing"
                } else {
                    "Veldt Wisp is off — no island, no service, no notification"
                },
                checked = ui.islandEnabled,
                onCheckedChange = vm::setIslandEnabled,
                titleGap = 0.dp,
            )

            DevicePreviewCard(ui, vm)

            /*
             * The appearance chips sit here, above the Appearance section, because they
             * drive the live mini pill in the preview card directly above them. Moving
             * them into Appearance would tidy the outline and hide the effect.
             */
            PillControlsCard(ui, vm)
            SwitchCard(
                title = "Vibrant wave colors",
                summary = "Rainbow gradient hills (One UI style) instead of monochrome",
                checked = ui.vibrantWave,
                onCheckedChange = vm::setVibrantWave,
            )
            ThumbShapeCard(ui, vm)
            WaveColorCard(ui, vm)
            WaveStyleCard(ui, vm)

            SectionHeader("Appearance")

            CrossfadeCard(ui, vm)
            SizeSliderCard(
                title = "Pill width",
                summary = "Maximum width of the title/artist text in the pill",
                value = ui.pillTextWidthDp,
                spec = SettingsOptions.PILL_WIDTH,
                onValueChange = vm::setPillTextWidthDp,
            )
            SizeSliderCard(
                title = "Panel width",
                summary = "Overall width of the expanded media panel",
                value = ui.panelWidthDp,
                spec = SettingsOptions.PANEL_WIDTH,
                onValueChange = vm::setPanelWidthDp,
            )

            SectionHeader("Behavior")

            HideDelayCard(ui, vm)
            SwitchCard(
                title = "Only show on home screen",
                summary = "Hide the island inside apps — it stays on your launcher",
                checked = ui.homeOnly,
                onCheckedChange = vm::setHomeOnly,
            )

            SectionHeader("Permissions")

            PermissionCards(ui, vm)
            PermissionsSummary(ui)

            SectionHeader("About")

            AboutCard(vm, updateState)
            ResetCard(vm)
        }
    }
}

// ---- shared building blocks -------------------------------------------------------------

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** A Material 3 card with the screen's standard content padding. No custom styling. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

/** The title-then-summary block every card opens with. */
@Composable
private fun CardHeading(title: String, summary: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(summary, style = MaterialTheme.typography.bodySmall)
}

/** The label that introduces a sub-block inside a card. */
@Composable
private fun SubLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
}

/**
 * A title/summary column with a switch on the right.
 *
 * [titleGap] is a parameter because the screen genuinely uses two values. Most cards
 * separate the title from its summary by 4.dp; the master enable card, the consume-bar
 * row and the permission cards set the lines solid at 0.dp. That is how the screen
 * already looks and this is appearance-preserving work, so the difference is carried
 * rather than smoothed away.
 */
@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    titleGap: Dp = 4.dp,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (titleGap > 0.dp) Spacer(Modifier.height(titleGap))
            Text(summary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * A card whose main control is a switch, optionally revealing more when it is on.
 *
 * [subOptions] are added to and removed from the composition, not merely disabled: an
 * option that cannot apply is clutter, and a greyed-out row invites tapping.
 */
@Composable
private fun SwitchCard(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    titleGap: Dp = 4.dp,
    subOptions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    SettingsCard {
        SwitchRow(title, summary, checked, onCheckedChange, titleGap)
        if (checked && subOptions != null) {
            Spacer(Modifier.height(12.dp))
            subOptions()
        }
    }
}

/**
 * A single-select row of chips.
 *
 * Selection is by equality against the current value rather than by index, so a chip
 * whose key does not match anything stored simply renders unselected — which is the
 * failure `SettingsOptionsTest` exists to prevent.
 */
@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

/** As [ChipRow], for sets too long to fit one line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipFlow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

// ---- the cards --------------------------------------------------------------------------

/**
 * A slider over an integer setting, with a live readout.
 *
 * The float the slider works in is truncated on the way out; [spec]'s step count means
 * it only ever lands on whole increments, so nothing is lost.
 */
@Composable
private fun SizeSliderCard(
    title: String,
    summary: String,
    value: Int,
    spec: SliderSpec,
    unit: String = "dp",
    onValueChange: (Int) -> Unit,
) {
    SettingsCard {
        CardHeading(title, summary)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = spec.min..spec.max,
                steps = spec.steps,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text("$value $unit", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun HideDelayCard(ui: SettingsUiState, vm: SettingsViewModel) {
    SettingsCard {
        CardHeading("Hide delay", "How long the island waits before hiding after playback stops")
        Spacer(Modifier.height(8.dp))
        ChipRow(SettingsOptions.HIDE_DELAYS, ui.hideDelayMs, vm::setHideDelayMs)
    }
}

@Composable
private fun ThumbShapeCard(ui: SettingsUiState, vm: SettingsViewModel) {
    SettingsCard {
        CardHeading("Thumb shape", "Shape of the scrub-bar playhead")
        Spacer(Modifier.height(8.dp))
        ChipFlow(SettingsOptions.THUMB_SHAPES, ui.thumbShape, vm::setThumbShape)
    }
}

@Composable
private fun WaveColorCard(ui: SettingsUiState, vm: SettingsViewModel) {
    SettingsCard {
        CardHeading(
            "Wave color",
            "Color of the scrub-bar accent and hills — auto keeps it readable on dark art",
        )
        Spacer(Modifier.height(8.dp))
        ChipRow(SettingsOptions.WAVE_COLOR_MODES, ui.waveColorMode, vm::setWaveColorMode)
    }
}

/**
 * The wave-style card: two chip rows and an unrelated switch.
 *
 * The standard and premium rows are **one** selection. Both bind to `ui.waveStyle` and
 * both call `setWaveStyle`, so picking a premium style deselects the standard chip and
 * vice versa; the `Premium` heading between them is grouping, not a second control.
 * Giving each row its own selected value would let the user hold two styles at once
 * and store only whichever was tapped last.
 *
 * The consume-bar switch is independent of the style and is shown in every state.
 */
@Composable
private fun WaveStyleCard(ui: SettingsUiState, vm: SettingsViewModel) {
    SettingsCard {
        CardHeading(
            "Scrub-bar animation",
            "How the played portion of the scrub bar moves while playing",
        )
        Spacer(Modifier.height(8.dp))
        ChipFlow(SettingsOptions.WAVE_STYLES_STANDARD, ui.waveStyle, vm::setWaveStyle)

        Spacer(Modifier.height(12.dp))
        Text("Premium", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            "Over-the-top effects that spill past the bar into the whole card",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(6.dp))
        ChipFlow(SettingsOptions.WAVE_STYLES_PREMIUM, ui.waveStyle, vm::setWaveStyle)

        Spacer(Modifier.height(12.dp))
        SwitchRow(
            title = "Consume bar",
            summary = "Fade the already-played (left) side as the playhead advances",
            checked = ui.consumeProgress,
            onCheckedChange = vm::setConsumeProgress,
            titleGap = 0.dp,
        )
    }
}

@Composable
private fun PillControlsCard(ui: SettingsUiState, vm: SettingsViewModel) {
    SwitchCard(
        title = "Controls on pill",
        summary = "Show transport buttons on the pill, without tapping to expand",
        checked = ui.showPillControls,
        onCheckedChange = vm::setShowPillControls,
    ) {
        SubLabel("Buttons")
        ChipRow(SettingsOptions.PILL_CONTROL_SETS, ui.pillControlSet, vm::setPillControlSet)
        Spacer(Modifier.height(10.dp))
        SubLabel("Position")
        ChipRow(
            SettingsOptions.PILL_CONTROL_POSITIONS,
            ui.pillControlPosition,
            vm::setPillControlPosition,
        )
    }
}

@Composable
private fun CrossfadeCard(ui: SettingsUiState, vm: SettingsViewModel) {
    SwitchCard(
        title = "Crossfade album art",
        summary = "Fade the pill and panel to the new artwork when the song changes",
        checked = ui.artCrossfade,
        onCheckedChange = vm::setArtCrossfade,
    ) {
        SubLabel("Fade time")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = ui.crossfadeMs.toFloat(),
                onValueChange = { vm.setCrossfadeMs(it.toInt()) },
                valueRange = SettingsOptions.CROSSFADE.min..SettingsOptions.CROSSFADE.max,
                steps = SettingsOptions.CROSSFADE.steps,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text("${ui.crossfadeMs} ms", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ---- permissions ------------------------------------------------------------------------

/**
 * The permission checklist.
 *
 * Two of the five cards are version-gated and simply do not exist on devices that
 * cannot be asked, rather than rendering as permanently satisfied.
 */
@Composable
private fun PermissionCards(ui: SettingsUiState, vm: SettingsViewModel) {
    val ctx = LocalContext.current

    // A runtime dialog keeps the activity resumed, so the ON_RESUME refresh in
    // SettingsScreen never fires for these two. They refresh themselves.
    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.refreshPermissions() }
    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.refreshPermissions() }

    PermissionCard(
        title = "Draw over other apps",
        description = "Required to show the island over other apps.",
        granted = ui.overlayGranted,
    ) { ctx.startActivity(PermissionsHelper.overlaySettingsIntent(ctx)) }

    PermissionCard(
        title = "Notification access",
        description = "Required to read and control the music session.",
        granted = ui.notifListenerGranted,
    ) { ctx.startActivity(PermissionsHelper.notificationListenerSettingsIntent()) }

    PermissionCard(
        title = "Usage access",
        description = "To hide the island while the playing app is in the foreground.",
        granted = ui.usageAccessGranted,
    ) { ctx.startActivity(PermissionsHelper.usageAccessSettingsIntent()) }

    if (PermissionsHelper.needsPostNotifications()) {
        PermissionCard(
            title = "Notifications permission",
            description = "Recommended for the service notification on Android 13+.",
            granted = ui.postNotificationsGranted,
        ) { postNotificationsLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }

    if (Build.VERSION.SDK_INT >= 31) {
        PermissionCard(
            title = "Output device names",
            description = "Grant to show your Bluetooth device's name, e.g. Galaxy Buds",
            granted = ui.bluetoothConnectGranted,
        ) { bluetoothLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT) }
    }
}

/**
 * One permission, its state and the way to change it.
 *
 * [onGrant] fires whether or not the permission is already held — the button becomes
 * "Open" rather than disappearing, because the usual reason to look at a granted
 * permission is to revoke it.
 */
@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Card {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Title, description and status are set solid, with no spacers between
            // them -- the three lines read as one block against the button.
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = if (granted) "Granted" else "Not granted",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onGrant) { Text(if (granted) "Open" else "Grant") }
        }
    }
}

@Composable
private fun PermissionsSummary(ui: SettingsUiState) {
    /*
     * bluetoothConnectGranted is deliberately excluded from this conjunction. It is
     * optional: without it the panel shows a generic output name instead of the real
     * device name, and nothing else changes. Counting it would leave the summary
     * permanently red for every user who is content with that, which trains them to
     * ignore the line that is supposed to mean something.
     */
    val allGranted = ui.overlayGranted &&
        ui.notifListenerGranted &&
        ui.usageAccessGranted &&
        ui.postNotificationsGranted

    Text(
        text = if (allGranted) {
            "All permissions granted. The island will work when you play music."
        } else {
            "Missing permissions. Grant the ones shown in red."
        },
        // bodyMedium, a step up from the bodySmall used inside the cards: this line is
        // the verdict on the whole section and sits outside any card.
        style = MaterialTheme.typography.bodyMedium,
        color = if (allGranted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
    )
}

// ---- about and reset --------------------------------------------------------------------

@Composable
private fun AboutCard(vm: SettingsViewModel, updateState: UpdateState) {
    val ctx = LocalContext.current

    SettingsCard {
        CardHeading(
            "Veldt Wisp ${BuildConfig.VERSION_NAME}",
            "A One UI-style now-playing pill for any Android device.",
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { openUrl(ctx, PROJECT_URL) }) { Text("Project on GitHub") }

        // Only 4.dp: the two text buttons already carry their own generous vertical
        // padding, so the usual 12.dp sub-block gap reads as a hole between them.
        Spacer(Modifier.height(4.dp))
        // Exactly one block per state; the updater is never in two of these at once.
        when (updateState) {
            // A text button, matching the GitHub link directly above it: from Idle this
            // is a link-weight action, not the card's primary call to action. The
            // filled buttons below are reserved for the states that actually install.
            is UpdateState.Idle ->
                TextButton(onClick = vm::checkForUpdates) { Text("Check for updates") }

            is UpdateState.Checking ->
                Text("Checking…", style = MaterialTheme.typography.bodySmall)

            is UpdateState.UpToDate ->
                Text("Up to date ✓", style = MaterialTheme.typography.bodySmall)

            is UpdateState.Available -> {
                Text(
                    "v${updateState.info.version} available",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.installUpdate(ctx) }) { Text("Download & install") }
            }

            is UpdateState.Downloading ->
                Text("Downloading…", style = MaterialTheme.typography.bodySmall)

            is UpdateState.Downloaded -> {
                Text(
                    "v${updateState.info.version} downloaded — if Android asked you to " +
                        "allow installs from Veldt Wisp, grant it and tap Install",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.installUpdate(ctx) }) { Text("Install") }
            }

            is UpdateState.Failed -> {
                Text(
                    updateState.msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                // Retry re-checks rather than re-installing: the updater refuses to act
                // on info it already failed with.
                Button(onClick = vm::checkForUpdates) { Text("Retry") }
            }
        }
    }
}

/**
 * Reset, behind a confirmation.
 *
 * The button opens the dialog and does nothing else. This is the only destructive
 * control on the screen and it sits next to an ordinary toggle, so a mis-tap has to
 * cost nothing.
 */
@Composable
private fun ResetCard(vm: SettingsViewModel) {
    var confirming by remember { mutableStateOf(false) }

    SettingsCard {
        CardHeading(
            "Reset to defaults",
            "Restores every setting on this screen to its default value.",
        )
        // 12.dp, not the usual 8.dp: the extra room keeps the destructive button from
        // sitting tight under the text that explains it.
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = { confirming = true }) { Text("Reset") }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Reset to defaults?") },
            text = {
                Text(
                    "All Veldt Wisp settings will return to their default values. " +
                        "This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.resetToDefaults()
                        confirming = false
                    },
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Opens [url] in whatever handles it, or does nothing.
 *
 * A device with no browser at all is unusual but real — a stripped ROM, or a work
 * profile with the browser blocked — and an unhandled intent throws. Failing to open
 * a link is not worth crashing the settings screen over.
 */
private fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        // No handler for http(s). Nothing useful to say and nothing to recover.
    }
}
