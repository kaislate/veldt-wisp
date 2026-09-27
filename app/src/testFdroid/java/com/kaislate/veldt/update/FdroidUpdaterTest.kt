// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.update

import com.kaislate.veldt.BuildConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins that the `fdroid` flavor carries the inert updater and not the GitHub one.
 *
 * F-Droid rejects an app that fetches and installs APKs itself, so the failure this
 * guards against is a build-script or source-set slip that quietly compiles the real
 * checker into the F-Droid APK. The manifest half of that (no REQUEST_INSTALL_PACKAGES,
 * no FileProvider) is checked on the built APK with aapt2; this is the code half.
 */
class FdroidUpdaterTest {

    @Test
    fun `the updater UI flag is off`() {
        assertFalse(BuildConfig.UPDATER_ENABLED)
    }

    @Test
    fun `a check from the oldest possible version finds nothing`() = runTest {
        // The GitHub checker would either report the latest release (every release is
        // newer than 0.0.0) or throw for want of a network. Only the no-op returns null.
        assertNull(UpdateChecker.check("0.0.0"))
    }

    @Test
    fun `the compiled checker references no networking and no GitHub endpoint`() {
        // GithubUpdaterTest runs the same scan against the real checker and finds every
        // one of these, so an absence here means absence, not a scan that looks nowhere.
        val pools = UpdaterBytecode.constantPools()
        for (forbidden in UpdaterBytecode.UPDATER_MARKERS) {
            assertFalse("fdroid UpdateChecker mentions $forbidden", pools.contains(forbidden))
        }
    }
}
