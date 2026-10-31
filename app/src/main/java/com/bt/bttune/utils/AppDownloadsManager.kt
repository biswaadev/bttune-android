package com.bt.bttune.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

object AppDownloadsManager {
    private const val PREFS_NAME = "bttune_install_stats"
    private const val KEY_INSTALL_REGISTERED = "device_install_registered"
    private const val KEY_CACHED_DOWNLOADS_COUNT = "cached_downloads_count"
    private const val COUNTER_KEY = "bttune_android_app_installs"
    private const val PRIMARY_API_URL = "https://countapi.mileshilliard.com/api/v1"
    const val DEFAULT_BASE_COUNT = 0L

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val _downloadsCount = MutableStateFlow<Long>(DEFAULT_BASE_COUNT)
    val downloadsCount: StateFlow<Long> = _downloadsCount.asStateFlow()

    private val _isRegistering = MutableStateFlow(false)
    val isRegistering: StateFlow<Boolean> = _isRegistering.asStateFlow()

    fun init(context: Context) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cached = prefs.getLong(KEY_CACHED_DOWNLOADS_COUNT, DEFAULT_BASE_COUNT)
            if (cached > _downloadsCount.value) {
                _downloadsCount.value = cached
            }
        }
    }

    suspend fun registerDeviceInstallIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getLong(KEY_CACHED_DOWNLOADS_COUNT, DEFAULT_BASE_COUNT)
        if (cached > _downloadsCount.value) {
            _downloadsCount.value = cached
        }

        val isRegistered = prefs.getBoolean(KEY_INSTALL_REGISTERED, false)

        if (!isRegistered) {
            // Check if this is an app UPDATE rather than a fresh install
            // When an app is updated, packageInfo.lastUpdateTime > packageInfo.firstInstallTime
            val isAppUpdate = runCatching {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                // If lastUpdateTime is significantly greater than firstInstallTime, it's an update
                (packageInfo.lastUpdateTime - packageInfo.firstInstallTime) > 3000L
            }.getOrDefault(false)

            if (isAppUpdate) {
                // Device updated the app; mark registered so it never increments downloads on updates
                prefs.edit()
                    .putBoolean(KEY_INSTALL_REGISTERED, true)
                    .commit()
                Timber.i("AppDownloadsManager: Detected app update (firstInstall != lastUpdate); skipping install increment")
                fetchLatestDownloadsCount(context)
                return@withContext
            }

            _isRegistering.value = true
            try {
                val hitUrl = "$PRIMARY_API_URL/hit/$COUNTER_KEY"
                val request = Request.Builder()
                    .url(hitUrl)
                    .header("User-Agent", "BTTUNE-Android/${com.bt.bttune.BuildConfig.VERSION_NAME}")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val count = json.optLong("value", -1L)
                        if (count >= 0) {
                            prefs.edit()
                                .putBoolean(KEY_INSTALL_REGISTERED, true)
                                .putLong(KEY_CACHED_DOWNLOADS_COUNT, count)
                                .commit()
                            _downloadsCount.value = count
                            Timber.i("AppDownloadsManager: Successfully registered new device install! Count: $count")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "AppDownloadsManager: Failed to register install on first attempt; will retry next time")
            } finally {
                _isRegistering.value = false
            }
        } else {
            // Already registered on this device, simply refresh latest global count without incrementing
            fetchLatestDownloadsCount(context)
        }
    }

    suspend fun fetchLatestDownloadsCount(context: Context? = null): Long = withContext(Dispatchers.IO) {
        try {
            if (context != null) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val cached = prefs.getLong(KEY_CACHED_DOWNLOADS_COUNT, DEFAULT_BASE_COUNT)
                if (cached > _downloadsCount.value) {
                    _downloadsCount.value = cached
                }
            }

            val getUrl = "$PRIMARY_API_URL/get/$COUNTER_KEY?_t=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(getUrl)
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "BTTUNE-Android/${com.bt.bttune.BuildConfig.VERSION_NAME}")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val count = json.optLong("value", -1L)
                    if (count >= 0) {
                        _downloadsCount.value = count
                        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            ?.edit()
                            ?.putLong(KEY_CACHED_DOWNLOADS_COUNT, count)
                            ?.commit()
                        return@withContext count
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "AppDownloadsManager: Error fetching latest downloads count")
        }
        return@withContext _downloadsCount.value
    }
}
