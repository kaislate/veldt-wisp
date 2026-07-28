package com.kaislate.veldt.viewmodel

import com.kaislate.veldt.update.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the update transition table.
 *
 * These are the rules behind the About card's one interactive control, and every
 * one of them is a place where a plausible-looking simplification costs the user
 * something concrete: a swallowed "up to date", a wrong error message, or — the
 * expensive one — a second Install tap that re-downloads the APK it already has.
 *
 * The three fallback strings are asserted as literals rather than against the
 * constants that produce them. Comparing a constant to itself passes whatever it
 * is changed to; these strings are user-visible text, so the test has to state
 * independently what they say.
 */
class UpdateRulesTest {

    private fun info(version: String = "9.9.9") =
        UpdateInfo(
            version = version,
            apkUrl = "https://example.invalid/veldt-$version.apk",
            notes = "release notes for $version",
        )

    // ---- afterCheck ------------------------------------------------------------------

    @Test
    fun `a found release becomes Available`() {
        val found = info()
        assertEquals(UpdateState.Available(found), UpdateRules.afterCheck(found))
    }

    @Test
    fun `Available carries the very instance the checker returned`() {
        val found = info()
        val state = UpdateRules.afterCheck(found) as UpdateState.Available
        // Same instance, not merely an equal one: the About card shows these notes
        // and installUpdate downloads this apkUrl.
        assertSame(found, state.info)
    }

    @Test
    fun `no release means UpToDate`() {
        assertEquals(UpdateState.UpToDate, UpdateRules.afterCheck(null))
    }

    // ---- the three failure paths -----------------------------------------------------

    @Test
    fun `a check failure reports the throwable's own message`() {
        assertEquals(UpdateState.Failed("boom"), UpdateRules.afterCheckFailure("boom"))
    }

    @Test
    fun `a check failure with no message falls back to Update check failed`() {
        assertEquals(UpdateState.Failed("Update check failed"), UpdateRules.afterCheckFailure(null))
    }

    @Test
    fun `a download failure reports the throwable's own message`() {
        assertEquals(UpdateState.Failed("disk full"), UpdateRules.afterDownloadFailure("disk full"))
    }

    @Test
    fun `a download failure with no message falls back to Download failed`() {
        assertEquals(UpdateState.Failed("Download failed"), UpdateRules.afterDownloadFailure(null))
    }

    @Test
    fun `an install failure reports the throwable's own message`() {
        assertEquals(UpdateState.Failed("no installer"), UpdateRules.afterInstallFailure("no installer"))
    }

    @Test
    fun `an install failure with no message falls back to Install failed`() {
        assertEquals(UpdateState.Failed("Install failed"), UpdateRules.afterInstallFailure(null))
    }

    @Test
    fun `the three fallback messages are three different messages`() {
        val check = (UpdateRules.afterCheckFailure(null) as UpdateState.Failed).msg
        val download = (UpdateRules.afterDownloadFailure(null) as UpdateState.Failed).msg
        val install = (UpdateRules.afterInstallFailure(null) as UpdateState.Failed).msg
        // One message reused for all three would tell the user "Update check failed"
        // when the check succeeded and the download did not.
        assertNotEquals(check, download)
        assertNotEquals(download, install)
        assertNotEquals(check, install)
        assertEquals(3, setOf(check, download, install).size)
    }

    @Test
    fun `an empty message is a message and is not replaced by the fallback`() {
        // Only null means "the throwable told us nothing"; "" is what it told us.
        assertEquals(UpdateState.Failed(""), UpdateRules.afterCheckFailure(""))
    }

    // ---- installAction, all seven states ---------------------------------------------

    @Test
    fun `Available downloads then installs`() {
        assertEquals(
            InstallAction.DOWNLOAD_THEN_INSTALL,
            UpdateRules.installAction(UpdateState.Available(info())),
        )
    }

    @Test
    fun `Downloaded installs the file it already has`() {
        // The second tap after granting "install unknown apps" must not re-download.
        assertEquals(
            InstallAction.INSTALL_EXISTING,
            UpdateRules.installAction(UpdateState.Downloaded(info(), File("update.apk"))),
        )
    }

    @Test
    fun `Idle does nothing`() {
        assertEquals(InstallAction.NONE, UpdateRules.installAction(UpdateState.Idle))
    }

    @Test
    fun `Checking does nothing`() {
        assertEquals(InstallAction.NONE, UpdateRules.installAction(UpdateState.Checking))
    }

    @Test
    fun `UpToDate does nothing`() {
        assertEquals(InstallAction.NONE, UpdateRules.installAction(UpdateState.UpToDate))
    }

    @Test
    fun `Downloading does nothing so a second tap cannot start a second download`() {
        assertEquals(InstallAction.NONE, UpdateRules.installAction(UpdateState.Downloading))
    }

    @Test
    fun `Failed does nothing`() {
        assertEquals(InstallAction.NONE, UpdateRules.installAction(UpdateState.Failed("boom")))
    }

    @Test
    fun `exactly two states act on an install tap`() {
        val states = listOf(
            UpdateState.Idle,
            UpdateState.Checking,
            UpdateState.Available(info()),
            UpdateState.UpToDate,
            UpdateState.Downloading,
            UpdateState.Downloaded(info(), File("update.apk")),
            UpdateState.Failed("boom"),
        )
        assertEquals(7, states.size)
        assertEquals(5, states.count { UpdateRules.installAction(it) == InstallAction.NONE })
    }

    // ---- afterDownload ---------------------------------------------------------------

    @Test
    fun `a finished download becomes Downloaded carrying both the info and the file`() {
        val downloaded = info()
        val file = File("update.apk")
        assertEquals(
            UpdateState.Downloaded(downloaded, file),
            UpdateRules.afterDownload(downloaded, file),
        )
    }

    @Test
    fun `Downloaded keeps the very file that was downloaded`() {
        val downloaded = info()
        val file = File("cache", "update.apk")
        val state = UpdateRules.afterDownload(downloaded, file) as UpdateState.Downloaded
        // Losing the file here is the bug that makes the second Install tap
        // re-download the APK, so identity is asserted, not just equality.
        assertSame(file, state.file)
        assertSame(downloaded, state.info)
    }

    @Test
    fun `a downloaded state is what an install tap acts on`() {
        // Ties the two rules together: whatever afterDownload produces must be a
        // state installAction is willing to install from.
        val state = UpdateRules.afterDownload(info(), File("update.apk"))
        assertTrue(state is UpdateState.Downloaded)
        assertEquals(InstallAction.INSTALL_EXISTING, UpdateRules.installAction(state))
    }
}
