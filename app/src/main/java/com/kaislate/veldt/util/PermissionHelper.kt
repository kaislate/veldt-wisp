package com.kaislate.veldt.util

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * Everything the app needs to know about the five permissions it depends on, in one
 * object: a "do we have it?" question and, where the user has to grant it from a
 * system screen, the [Intent] that takes them there.
 *
 * The questions are all cheap and side-effect free, because the settings screen asks
 * every one of them again on each resume rather than caching an answer that the user
 * may have revoked while they were away. Nothing here requests, prompts or launches
 * anything — building the Intent and starting it are kept apart so the caller decides
 * which of them is on screen.
 *
 * The five, and why:
 *
 *  * overlay — without it there is no pill at all;
 *  * notification listener — the only way to see and drive the media session;
 *  * usage access — lets the pill get out of the way of the app that is playing;
 *  * post notifications — the foreground-service notification on 33+;
 *  * bluetooth connect — cosmetic; supplies the real name of the output device.
 *
 * The last two are version-gated: below the release that introduced them there is
 * nothing to grant, so the answer is an unconditional yes rather than a refusal the
 * user would have no way to act on.
 */
object PermissionsHelper {

    /** Shared shape of a runtime-permission check. */
    private fun granted(ctx: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    // ---- Draw over other apps -------------------------------------------------

    fun hasOverlayPermission(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    /**
     * The overlay screen is per-app, and the package has to be named in the data URI
     * or the user lands on the full list and has to find us in it.
     */
    fun overlaySettingsIntent(ctx: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:${ctx.packageName}".toUri(),
    )

    // ---- Notification access --------------------------------------------------

    /**
     * Asked of the framework's own list of enabled listeners rather than of our
     * service, which cannot report on itself before it has been bound.
     */
    fun hasNotificationListener(ctx: Context): Boolean =
        ctx.packageName in NotificationManagerCompat.getEnabledListenerPackages(ctx)

    fun notificationListenerSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    // ---- Usage access ---------------------------------------------------------

    /**
     * Usage access is an app-op, not a runtime permission, so it has to be read from
     * [AppOpsManager]. The op is checked against our own uid — [Process.myUid] rather
     * than the binder caller's, since this is also asked from inside service
     * callbacks the system originates.
     *
     * A default answer means "fall back to the permission", which for a package that
     * is neither privileged nor platform-signed will always come back denied; it is
     * spelled out anyway so the three outcomes are visible.
     */
    fun hasUsageAccess(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        // Deprecated on 36 in favour of an attribution-aware overload that does not
        // exist on our minimum, 29. Suppressed rather than version-branched: the
        // replacement answers the same question and we would only ever take the old
        // path on the devices we actually support.
        @Suppress("DEPRECATION")
        val op = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            ctx.packageName,
        )
        return when (op) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT -> granted(ctx, Manifest.permission.PACKAGE_USAGE_STATS)
            else -> false
        }
    }

    /** No per-app variant exists; this opens the list and the user picks us out of it. */
    fun usageAccessSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    // ---- Post notifications (Android 13+) -------------------------------------

    /** False below 33, where the notification needs no permission at all. */
    fun needsPostNotifications(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun hasPostNotifications(ctx: Context): Boolean =
        !needsPostNotifications() || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)

    // ---- Bluetooth connect (Android 12+) --------------------------------------

    /**
     * Below 12 the device name came with the old, install-time Bluetooth permission,
     * so there is nothing left to ask for and the answer is yes.
     */
    fun hasBluetoothConnect(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            granted(ctx, Manifest.permission.BLUETOOTH_CONNECT)
}
