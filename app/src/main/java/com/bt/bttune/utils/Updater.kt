package com.bt.bttune.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bt.bttune.BuildConfig
import com.bt.bttune.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val releaseNotes: String = "",
    val releaseUrl: String = RemoteConfigManager.getLatestReleasePageUrl(),
    val apkDownloadUrl: String = ""
)

object Updater {
    var lastCheckTime = -1L
        private set

    const val UPDATE_NOTIFICATION_CHANNEL_ID = "bttune_updates_channel"
    const val UPDATE_NOTIFICATION_ID = 2002

    private fun fetchJson(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "BTTUNE-App/${BuildConfig.VERSION_NAME}")
            conn.connect()

            val code = conn.responseCode
            if (code !in 200..299) {
                val errorMsg = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                throw IllegalStateException("GitHub API returned HTTP $code: $errorMsg")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun extractApkUrl(releaseObj: JSONObject, versionName: String, isNightly: Boolean): String {
        val assets = releaseObj.optJSONArray("assets")
        if (assets != null && assets.length() > 0) {
            var fallbackApkUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                    if (isNightly && name.contains("nightly", ignoreCase = true)) {
                        return url
                    }
                    if (!isNightly && (
                        name.contains("bttune", ignoreCase = true) ||
                        name.contains("release", ignoreCase = true) ||
                        name.contains("universal", ignoreCase = true) ||
                        name.contains("signed", ignoreCase = true)
                    )) {
                        return url
                    }
                    if (fallbackApkUrl.isBlank()) {
                        fallbackApkUrl = url
                    }
                }
            }
            if (fallbackApkUrl.isNotBlank()) {
                return fallbackApkUrl
            }
        }

        // Direct download fallback URL based on build type & tag
        return RemoteConfigManager.getApkDownloadUrl(versionName, isNightly)
    }

    suspend fun getLatestUpdateInfo(isNightly: Boolean = BuildConfig.IS_NIGHTLY): Result<UpdateInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiUrl = RemoteConfigManager.getLatestReleaseApiUrl(isNightly)
                if (apiUrl.isBlank()) {
                    throw IllegalStateException("Update API URL is empty")
                }

                if (isNightly) {
                    val response = fetchJson(apiUrl)
                    val jsonArray = JSONArray(response)
                    var versionName = ""
                    var releaseNotes = ""
                    var releaseUrl = RemoteConfigManager.getReleasesPageUrl()
                    var apkDownloadUrl = ""
                    for (i in 0 until jsonArray.length()) {
                        val release = jsonArray.getJSONObject(i)
                        if (release.optBoolean("prerelease", false)) {
                            versionName = release.getString("tag_name").removePrefix("v").removeSuffix("-nightly").trim()
                            releaseNotes = release.optString("body", "").trim()
                            releaseUrl = release.optString("html_url", RemoteConfigManager.getReleasesPageUrl())
                            apkDownloadUrl = extractApkUrl(release, versionName, isNightly = true)
                            break
                        }
                    }
                    lastCheckTime = System.currentTimeMillis()
                    UpdateInfo(versionName, releaseNotes, releaseUrl, apkDownloadUrl)
                } else {
                    val response = fetchJson(apiUrl)
                    val json = JSONObject(response)
                    val versionName = json.getString("tag_name").removePrefix("v").trim()
                    val releaseNotes = json.optString("body", "").trim()
                    val releaseUrl = json.optString("html_url", RemoteConfigManager.getLatestReleasePageUrl())
                    val apkDownloadUrl = extractApkUrl(json, versionName, isNightly = false)
                    lastCheckTime = System.currentTimeMillis()
                    UpdateInfo(versionName, releaseNotes, releaseUrl, apkDownloadUrl)
                }
            }.onFailure { e ->
                Timber.e(e, "Updater: Failed to fetch update info")
            }
        }

    suspend fun getLatestVersionName(): Result<String> =
        getLatestUpdateInfo().map { it.versionName }

    fun showUpdateAvailableNotification(context: Context, updateInfo: UpdateInfo) {
        try {
            val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    UPDATE_NOTIFICATION_CHANNEL_ID,
                    "BTTUNE App Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for newly released BTTUNE updates"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                putExtra("EXTRA_SHOW_UPDATE", true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                UPDATE_NOTIFICATION_ID,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notification = NotificationCompat.Builder(context, UPDATE_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bttune_logo)
                .setContentTitle("BTTUNE Update Available")
                .setContentText("Version v${updateInfo.versionName} is ready to install! Tap to update.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(UPDATE_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Timber.w(e, "Failed to display update notification")
        }
    }
}
