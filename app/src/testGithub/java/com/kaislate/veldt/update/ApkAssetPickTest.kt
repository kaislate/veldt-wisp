// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which asset the GitHub build downloads. Releases from 0.7.3 carry both this build and the
 * F-Droid build, and offering the F-Droid APK would silently remove the updater from the
 * user's phone.
 */
class ApkAssetPickTest {

    private val github = "veldt-wisp-0.7.3.apk" to "https://example/github"
    private val fdroid = "veldt-wisp-0.7.3-fdroid.apk" to "https://example/fdroid"

    @Test
    fun `the exact github name wins even when the fdroid apk is listed first`() {
        assertEquals("https://example/github", UpdateChecker.pickApkUrl(listOf(fdroid, github), "0.7.3"))
    }

    @Test
    fun `an fdroid apk alone is never offered`() {
        assertNull(UpdateChecker.pickApkUrl(listOf(fdroid), "0.7.3"))
    }

    @Test
    fun `an older release with another apk name still resolves`() {
        val old = "app-release.apk" to "https://example/old"
        assertEquals("https://example/old", UpdateChecker.pickApkUrl(listOf(fdroid, old), "0.7.3"))
    }

    @Test
    fun `non-apk assets and blank urls are ignored`() {
        val notes = "notes.txt" to "https://example/notes"
        val blank = "veldt-wisp-0.7.3.apk" to ""
        assertNull(UpdateChecker.pickApkUrl(listOf(notes, blank), "0.7.3"))
    }
}
