package com.example.snakegame

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/mjakupi-dev/Anar-Unik-Snake/releases/latest"

    data class ReleaseInfo(
        val version: String,
        val downloadUrl: String,
        val notes: String
    )

    fun checkForUpdates(context: Context, currentVersion: String, showUpToDate: Boolean = false) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Anar-Unik-Snake-Android")
                }
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("GitHub returned HTTP ${connection.responseCode}")
                }

                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val tag = json.optString("tag_name", "").removePrefix("v")
                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }
                if (tag.isBlank() || apkUrl.isNullOrBlank()) {
                    throw IllegalStateException("No APK is attached to the latest release")
                }

                val release = ReleaseInfo(
                    version = tag,
                    downloadUrl = apkUrl,
                    notes = json.optString("body", "").take(1200)
                )
                val isNewer = compareVersions(release.version, currentVersion) > 0

                val activity = context as? android.app.Activity
                activity?.let { host ->
                    host.runOnUiThread {
                    if (host.isFinishing || host.isDestroyed) return@runOnUiThread
                    if (isNewer) {
                        AlertDialog.Builder(host)
                            .setTitle("Snake Game update available")
                            .setMessage(
                                "Version ${release.version} is available. You have version $currentVersion.\n\n" +
                                    release.notes.ifBlank { "This update includes improvements and fixes." } +
                                    "\n\nDownload and install now?"
                            )
                            .setNegativeButton("Later", null)
                            .setPositiveButton("Update") { _, _ ->
                                download(host, release)
                            }
                            .show()
                    } else if (showUpToDate) {
                        Toast.makeText(host, "You already have the latest version.", Toast.LENGTH_SHORT).show()
                    }
                    }
                }
            } catch (e: Exception) {
                if (showUpToDate) {
                    val activity = context as? android.app.Activity
                    activity?.let { host ->
                        host.runOnUiThread {
                            if (!host.isFinishing && !host.isDestroyed) {
                                Toast.makeText(host, "Couldn't check for updates. Try again later.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun download(context: Context, release: ReleaseInfo) {
        try {
            val request = DownloadManager.Request(Uri.parse(release.downloadUrl))
                .setTitle("Snake Game ${release.version}")
                .setDescription("Downloading the latest game update")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "Anar-Unik-Snake-${release.version}.apk"
                )

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(
                context,
                "Update downloading. Tap the download notification when it finishes to install.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to start the download.", Toast.LENGTH_LONG).show()
        }
    }

    private fun compareVersions(first: String, second: String): Int {
        val a = Regex("\\d+").findAll(first).map { it.value.toInt() }.toList()
        val b = Regex("\\d+").findAll(second).map { it.value.toInt() }.toList()
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }
}
