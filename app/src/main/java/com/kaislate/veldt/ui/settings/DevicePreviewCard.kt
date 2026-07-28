package com.kaislate.veldt.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kaislate.veldt.ui.island.PillContent
import com.kaislate.veldt.util.IslandPosition
import com.kaislate.veldt.viewmodel.SettingsUiState
import com.kaislate.veldt.viewmodel.SettingsViewModel

/**
 * Compact placement control at the top of Settings: a small FIXED-size device
 * mockup (never width-driven, so it doesn't balloon on tablets) with a live,
 * scaled-down pill you position by tapping the frame. Below it: the edge-offset
 * slider and the notification switch.
 */
@Composable
fun DevicePreviewCard(ui: SettingsUiState, vm: SettingsViewModel) {
    val position = IslandPosition.fromKey(ui.positionKey)

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Placement", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Tap the phone to place the island.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))

            // Fixed-size frame — identical on phones and tablets.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .height(164.dp)
                        .width(84.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    val edgePadding =
                        if (position.isBottom) PaddingValues(bottom = (4 + ui.topOffsetDp / 8).dp)
                        else PaddingValues(top = (4 + ui.topOffsetDp / 8).dp)

                    PillContent(
                        vibrant = ui.vibrantWave,
                        waveColorMode = ui.waveColorMode,
                        textWidthDp = ui.pillTextWidthDp,
                        crossfadeMs = if (ui.artCrossfade) ui.crossfadeMs else 0,
                        modifier = Modifier
                            .align(position.alignment)
                            .padding(edgePadding)
                            .graphicsLayer {
                                scaleX = 0.38f
                                scaleY = 0.38f
                                transformOrigin = TransformOrigin(
                                    position.horizontalBias,
                                    if (position.isBottom) 1f else 0f
                                )
                            }
                    )

                    // Six invisible tap zones (2 rows x 3 cells) mapped to the anchors.
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            TapZone(Modifier.weight(1f).fillMaxHeight()) { vm.setPosition(IslandPosition.TOP_LEFT.key) }
                            TapZone(Modifier.weight(1f).fillMaxHeight()) { vm.setPosition(IslandPosition.TOP_CENTER.key) }
                            TapZone(Modifier.weight(1f).fillMaxHeight()) { vm.setPosition(IslandPosition.TOP_RIGHT.key) }
                        }
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            TapZone(Modifier.weight(1f).fillMaxHeight()) { vm.setPosition(IslandPosition.BOTTOM_LEFT.key) }
                            TapZone(Modifier.weight(1f).fillMaxHeight()) { vm.setPosition(IslandPosition.BOTTOM_CENTER.key) }
                            TapZone(Modifier.weight(1f).fillMaxHeight()) { vm.setPosition(IslandPosition.BOTTOM_RIGHT.key) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Edge offset
            Text("Edge offset", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Distance from the screen edge — clears camera cutouts and nav bars",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = ui.topOffsetDp.toFloat(),
                    onValueChange = { vm.setTopOffsetDp(it.toInt()) },
                    valueRange = 0f..60f,
                    steps = 14, // 4dp increments: 0,4,…,60
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Text("${ui.topOffsetDp} dp", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(16.dp))

            // Notification — a real switch, within the placement area.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show notification", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (!ui.hideNotification)
                            "A status-bar notification; tap it to open Veldt Wisp settings."
                        else
                            "Hidden — the only way back to settings is opening the Veldt Wisp app.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = !ui.hideNotification,
                    onCheckedChange = { vm.setHideNotification(!it) }
                )
            }
        }
    }
}

@Composable
private fun TapZone(modifier: Modifier, onTap: () -> Unit) {
    Box(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onTap
        )
    )
}
