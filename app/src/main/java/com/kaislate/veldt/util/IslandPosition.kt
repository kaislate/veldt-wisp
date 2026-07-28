// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.util

import android.view.Gravity
import androidx.compose.ui.Alignment

enum class IslandPosition(val key: String, val isBottom: Boolean, val horizontalBias: Float) {
    TOP_LEFT("top-left", false, 0f),
    TOP_CENTER("top-center", false, 0.5f),
    TOP_RIGHT("top-right", false, 1f),
    BOTTOM_LEFT("bottom-left", true, 0f),
    BOTTOM_CENTER("bottom-center", true, 0.5f),
    BOTTOM_RIGHT("bottom-right", true, 1f);

    val gravity: Int
        get() = (if (isBottom) Gravity.BOTTOM else Gravity.TOP) or when (horizontalBias) {
            0f -> Gravity.START
            1f -> Gravity.END
            else -> Gravity.CENTER_HORIZONTAL
        }

    val alignment: Alignment
        get() = when {
            !isBottom && horizontalBias == 0f -> Alignment.TopStart
            !isBottom && horizontalBias == 1f -> Alignment.TopEnd
            !isBottom -> Alignment.TopCenter
            horizontalBias == 0f -> Alignment.BottomStart
            horizontalBias == 1f -> Alignment.BottomEnd
            else -> Alignment.BottomCenter
        }

    companion object {
        fun fromKey(s: String?): IslandPosition = entries.firstOrNull { it.key == s } ?: TOP_CENTER
    }
}
