// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.update

import com.kaislate.veldt.BuildConfig
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `github` flavor's half of the flavor split: the updater is present and on.
 *
 * The bytecode test also keeps FdroidUpdaterTest honest. That test passes by *not*
 * finding GitHub networking in the compiled checker; this one proves the same scan
 * does find it when it is really there, so the F-Droid pass is not vacuous.
 */
class GithubUpdaterTest {

    @Test
    fun `the updater UI flag is on`() {
        assertTrue(BuildConfig.UPDATER_ENABLED)
    }

    @Test
    fun `the compiled checker talks to the GitHub Releases API`() {
        val pools = UpdaterBytecode.constantPools()
        for (expected in UpdaterBytecode.UPDATER_MARKERS) {
            assertTrue("github UpdateChecker should mention $expected", pools.contains(expected))
        }
    }
}
