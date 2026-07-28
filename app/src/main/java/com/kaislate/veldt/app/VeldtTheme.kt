// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Material 3's stock dark palette, unmodified.
 *
 * Held at file scope rather than built inside [VeldtTheme]: the palette is a
 * constant, and rebuilding its several dozen colours on every composition of the
 * theme root would be pure waste.
 */
private val WispDarkColors = darkColorScheme()

/**
 * The app's theme, wrapped around [content].
 *
 * Wisp is dark-only and does not follow the system's light/dark setting. The pill
 * it draws is a dark capsule floating over whatever app is in the foreground, so a
 * settings screen that turned white in daylight would look like a different piece
 * of software from the thing being configured.
 *
 * Typography and shapes are left at Material 3's defaults — only the colours are
 * pinned.
 */
@Composable
fun VeldtTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WispDarkColors,
        content = content
    )
}
