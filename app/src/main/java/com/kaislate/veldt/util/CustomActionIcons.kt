// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.util

import android.content.Context
import android.graphics.drawable.Drawable
import android.media.session.PlaybackState
import androidx.core.content.res.ResourcesCompat

private val cache = mutableMapOf<Pair<String, Int>, Drawable?>()

fun loadCustomActionIcon(ctx: Context, pkg: String, action: PlaybackState.CustomAction): Drawable? {
    if (action.icon == 0) return null
    return cache.getOrPut(pkg to action.icon) {
        runCatching {
            val appCtx = ctx.createPackageContext(pkg, 0)
            ResourcesCompat.getDrawable(appCtx.resources, action.icon, appCtx.theme)
        }.getOrNull()
    }
}
