// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal, dependency-free GitHub Releases update checker.
 *
 * Only ever hits the network when the user explicitly taps "Check for updates" —
 * no background polling, no analytics, no third-party services.
 */
data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val notes: String
)

object UpdateChecker {

    private const val RELEASES_URL = "https://api.github.com/repos/kaislate/veldt-wisp/releases/latest"
    private const val USER_AGENT = "VeldtWisp-Updater"

    /**
     * Checks GitHub's latest release against [currentVersion].
     * Returns null when there is no newer release or the release has no `.apk` asset.
     * Network/parse failures propagate as exceptions so the caller can surface them.
     */
    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
            connectTimeout = 15_000
            readTimeout = 15_000
        }

        val body = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }

        val json = JSONObject(body)
        val tagName = json.optString("tag_name", "")
        require(tagName.isNotBlank()) { "Release tag missing from GitHub response" }
        val remoteVersion = tagName.removePrefix("v")
        val notes = json.optString("body", "")

        val assets = json.optJSONArray("assets")
        var apkUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url", "")
                    break
                }
            }
        }
        if (apkUrl.isNullOrBlank()) return@withContext null

        if (isNewer(remoteVersion, currentVersion)) {
            UpdateInfo(version = remoteVersion, apkUrl = apkUrl, notes = notes)
        } else {
            null
        }
    }

    /** True when [remote] > [local], comparing dot-separated numeric parts, padding the shorter one with zeros. */
    private fun isNewer(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".")
        val localParts = local.split(".")
        val size = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until size) {
            val r = remoteParts.getOrNull(i)?.toIntOrNull() ?: 0
            val l = localParts.getOrNull(i)?.toIntOrNull() ?: 0
            if (r != l) return r > l
        }
        return false
    }

    /** Downloads the APK to cache and launches the system installer. */
    /** Downloads the APK to cache and returns the file (no install prompt yet). */
    suspend fun download(ctx: Context, info: UpdateInfo): File = withContext(Dispatchers.IO) {
        require(info.apkUrl.startsWith("https://")) { "Insecure update URL" }
        val file = File(ctx.cacheDir, "update.apk")
        if (file.exists()) file.delete()

        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 15_000
        }

        conn.inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        conn.disconnect()
        file
    }

    /**
     * Fires the installer for an already-downloaded APK. If the app lacks the
     * "install unknown apps" permission, opens the system grant screen instead
     * and returns false — the caller should keep its Install button available
     * so the user can tap again after granting (no re-download needed).
     */
    fun install(ctx: Context, file: File): Boolean {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:" + ctx.packageName)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
