// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.update

/**
 * A newer release the update check found.
 *
 * Lives in `main` rather than beside the GitHub checker because the updater's state
 * machine ([com.kaislate.veldt.viewmodel.UpdateRules]) is shared by both flavors and
 * names this type; only the code that fetches one is flavor-specific.
 */
data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val notes: String
)
