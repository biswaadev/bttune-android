package com.bt.bttune.lyrics.providers

import android.content.Context
import com.bt.bttune.constants.EnableSimpMusicLyricsKey
import com.bt.bttune.lyrics.LyricsProvider
import com.bt.bttune.utils.dataStore
import com.bt.bttune.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object BTTUNESimpLyricsProvider : LyricsProvider {
    override val name: String = "SimpMusic"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableSimpMusicLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (id.isBlank()) throw IllegalArgumentException("Video ID is blank")

            val cleanId = if (id.startsWith("JS:")) "" else id
            if (cleanId.isBlank()) throw IllegalArgumentException("Unsupported ID format")

            val request = Request.Builder()
                .url("https://api-lyrics.simpmusic.org/v1/$cleanId")
                .header("User-Agent", "BTTUNE/1.0")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")

            val body = response.body?.string() ?: throw IllegalStateException("Empty response")
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) throw IllegalStateException("No lyrics found")

            val dataArray = json.optJSONArray("data")
            if (dataArray == null || dataArray.length() == 0) throw IllegalStateException("Empty data array")

            var bestLyrics: String? = null
            var minDiff = Int.MAX_VALUE

            for (i in 0 until dataArray.length()) {
                val item = dataArray.getJSONObject(i)
                val synced = item.optString("syncedLyrics", "").trim()
                val plain = item.optString("plainLyrics", "").trim()
                val lyrics = if (synced.isNotBlank()) synced else plain
                if (lyrics.isBlank()) continue

                val itemDuration = item.optInt("duration", 0)
                if (duration > 0 && itemDuration > 0) {
                    val diff = abs(itemDuration - duration)
                    if (diff <= 10 && diff < minDiff) {
                        minDiff = diff
                        bestLyrics = lyrics
                    }
                } else if (bestLyrics == null && (duration <= 0 || itemDuration <= 0)) {
                    bestLyrics = lyrics
                }
            }

            bestLyrics ?: throw IllegalStateException("No lyrics matching song duration")
        }
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        getLyrics(id, title, artist, duration).onSuccess(callback)
    }
}
