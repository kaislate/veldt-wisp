// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.update

import android.content.Context
import java.io.File

/**
 * The `fdroid` flavor's updater: it never finds, fetches or installs anything.
 *
 * F-Droid will not distribute an app that downloads and installs APKs itself; the
 * F-Droid client delivers the updates instead. This object keeps the same shape as
 * the `github` flavor's checker so the shared view model compiles unchanged, while
 * containing no networking and no installer intent at all. The settings screen also
 * hides the update controls here (`BuildConfig.UPDATER_ENABLED` is false), so none of
 * these functions is reachable from the UI; they are defined only to be inert if
 * something calls them anyway.
 */
object UpdateChecker {

    /** Never reports a release: this build is updated by F-Droid, not by itself. */
    @Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
    suspend fun check(currentVersion: String): UpdateInfo? = null

    /**
     * Refuses. There is nothing to download: [check] never produces an [UpdateInfo],
     * so reaching this is a programming error rather than a user action.
     */
    @Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
    suspend fun download(ctx: Context, info: UpdateInfo): File =
        throw UnsupportedOperationException("The F-Droid build has no in-app updater")

    /** Installs nothing and reports that nothing was handed to the installer. */
    @Suppress("UNUSED_PARAMETER")
    fun install(ctx: Context, file: File): Boolean = false
}
