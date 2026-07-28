package com.kaislate.veldt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.kaislate.veldt.app.VeldtTheme
import com.kaislate.veldt.ui.settings.SettingsScreen
import com.kaislate.veldt.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app's single activity, and a thin one: it hosts [SettingsScreen] and nothing
 * else.
 *
 * What the user actually installed Wisp for — the pill — is a window this process
 * raises from a service and never an activity, so it survives with no activity on
 * screen at all. That leaves this class two jobs: put the settings up inside the
 * app's theme, and re-check the permissions whenever the user comes back to it.
 *
 * `@AndroidEntryPoint` is what lets [viewModels] reach the Hilt-built
 * [SettingsViewModel]; without it the default factory could not construct one.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Resolved here and passed to [SettingsScreen] explicitly.
     *
     * The screen would obtain an identical instance from its own default argument —
     * this activity is the view-model store owner in both cases — but taking it
     * implicitly would hide the fact that [onResume] below is refreshing the very
     * object the screen is rendering.
     */
    private val settings: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VeldtTheme {
                // Surface lays down the theme's background colour underneath the
                // settings; without it the bare window shows through anywhere the
                // content does not paint.
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(vm = settings)
                }
            }
        }
    }

    /**
     * Every permission Wisp needs — drawing over other apps, reading media
     * notifications, running unthrottled in the background — is granted in the
     * system's own Settings, out of this process's sight. Nothing tells us when one
     * is toggled, so the return trip to this activity is the moment to look again.
     */
    override fun onResume() {
        super.onResume()
        settings.refreshPermissions()
    }
}
