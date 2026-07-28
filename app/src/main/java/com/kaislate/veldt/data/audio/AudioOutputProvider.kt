package com.kaislate.veldt.data.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Usb
import androidx.compose.ui.graphics.vector.ImageVector
import com.kaislate.veldt.util.PermissionsHelper

/** Current audio output sink, for display in the expanded panel. */
data class AudioOutput(val label: String, val icon: ImageVector)

/**
 * Resolves the currently active audio output device so the expanded panel can
 * show something like "Galaxy Buds Live" instead of always assuming the
 * phone speaker. Priority when multiple sinks are present (mirrors how the
 * system routes audio): Bluetooth > wired > USB > built-in speaker.
 */
object AudioOutputProvider {

    fun current(ctx: Context): AudioOutput {
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return builtInSpeaker(ctx)

        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val bluetooth = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                // TYPE_BLE_HEADSET was added in API 31; gate on that, not 26.
                (android.os.Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        if (bluetooth != null) {
            val label = if (PermissionsHelper.hasBluetoothConnect(ctx)) {
                bluetooth.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth audio"
            } else {
                "Bluetooth audio"
            }
            return AudioOutput(label, Icons.Filled.Bluetooth)
        }

        val wired = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        }
        if (wired != null) {
            return AudioOutput("Wired headphones", Icons.Filled.Headphones)
        }

        val usb = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
        if (usb != null) {
            return AudioOutput("USB audio", Icons.Filled.Usb)
        }

        return builtInSpeaker(ctx)
    }

    // Built-in speaker: name it per form factor (tablets vs phones).
    private fun builtInSpeaker(ctx: Context): AudioOutput {
        val isTablet = ctx.resources.configuration.smallestScreenWidthDp >= 600
        return AudioOutput(
            if (isTablet) "Tablet speaker" else "Phone speaker",
            Icons.Filled.Speaker
        )
    }
}
