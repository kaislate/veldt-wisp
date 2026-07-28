package com.kaislate.veldt.viewmodel

import com.kaislate.veldt.update.UpdateInfo
import java.io.File

/**
 * Where the in-app updater is in its one linear errand: check, download, install.
 *
 * The states are public because the About card renders a different control for each
 * one; [UpdateRules] below owns which state follows which.
 */
sealed interface UpdateState {

    /** Nothing has been asked for. The About card offers "Check for updates". */
    data object Idle : UpdateState

    /** A check is in flight. */
    data object Checking : UpdateState

    /** A newer release exists. [info] carries the notes shown to the user and the URL to fetch. */
    data class Available(val info: UpdateInfo) : UpdateState

    /** The check completed and this build is current. */
    data object UpToDate : UpdateState

    /** The APK is being fetched. */
    data object Downloading : UpdateState

    /**
     * The APK is on disk at [file] and the system installer has been offered it.
     *
     * This is terminal as far as this app is concerned. A successful install hands
     * control to the system installer UI and replaces this process, so there is no
     * "installed" state to move to — the app simply stops existing in its old form.
     * The state persists instead so that an install the user *declined* (or that the
     * system refused for want of the "install unknown apps" grant) can be retried
     * from the already-downloaded file.
     */
    data class Downloaded(val info: UpdateInfo, val file: File) : UpdateState

    /** Something threw. [msg] is shown verbatim in the About card. */
    data class Failed(val msg: String) : UpdateState
}

/** What tapping the About card's install control should do from the current state. */
enum class InstallAction {
    /** Fetch the APK, then offer it to the installer. */
    DOWNLOAD_THEN_INSTALL,

    /** Re-offer an APK that is already on disk. */
    INSTALL_EXISTING,

    /** Ignore the tap. */
    NONE,
}

/**
 * The updater's transition table, kept free of Android, the network and the disk so
 * every rule can be asserted directly.
 *
 * [SettingsViewModel] performs the effects — the HTTP request, the file write, the
 * installer intent — but it decides nothing: each of its branches asks this object
 * what the next state is. That means the tests below are testing the real rules and
 * not a paraphrase of them.
 */
object UpdateRules {

    /**
     * Shown when a check throws without a message. All three fallbacks are distinct
     * on purpose: they are the only clue the user gets about which of the three
     * network operations actually went wrong.
     */
    const val CHECK_FAILED = "Update check failed"

    /** Shown when the download throws without a message. */
    const val DOWNLOAD_FAILED = "Download failed"

    /** Shown when re-invoking the installer throws without a message. */
    const val INSTALL_FAILED = "Install failed"

    /**
     * A completed check. A non-null [found] is a newer release; null means the
     * checker looked and this build is current — not that the check failed. Failures
     * arrive at [afterCheckFailure] instead.
     */
    fun afterCheck(found: UpdateInfo?): UpdateState =
        if (found == null) UpdateState.UpToDate else UpdateState.Available(found)

    /** A check that threw. [message] is the throwable's own, which is often null. */
    fun afterCheckFailure(message: String?): UpdateState =
        UpdateState.Failed(message ?: CHECK_FAILED)

    /**
     * What an install tap does from [current].
     *
     * Only two states act. [UpdateState.Downloading] deliberately does not, so a
     * second tap during a download cannot start a second one; [UpdateState.Failed]
     * does not, so the user must re-check rather than retry against stale info.
     */
    fun installAction(current: UpdateState): InstallAction = when (current) {
        is UpdateState.Available -> InstallAction.DOWNLOAD_THEN_INSTALL
        is UpdateState.Downloaded -> InstallAction.INSTALL_EXISTING
        UpdateState.Idle,
        UpdateState.Checking,
        UpdateState.UpToDate,
        UpdateState.Downloading,
        is UpdateState.Failed,
            -> InstallAction.NONE
    }

    /**
     * A finished download, whatever the installer then said about it.
     *
     * Both arguments are carried through. Keeping [file] is what makes a second
     * Install tap — after the user grants "install unknown apps" — re-use the APK
     * instead of fetching it again.
     */
    fun afterDownload(info: UpdateInfo, file: File): UpdateState =
        UpdateState.Downloaded(info, file)

    /** A download that threw. */
    fun afterDownloadFailure(message: String?): UpdateState =
        UpdateState.Failed(message ?: DOWNLOAD_FAILED)

    /** A re-invocation of the installer that threw. */
    fun afterInstallFailure(message: String?): UpdateState =
        UpdateState.Failed(message ?: INSTALL_FAILED)
}
