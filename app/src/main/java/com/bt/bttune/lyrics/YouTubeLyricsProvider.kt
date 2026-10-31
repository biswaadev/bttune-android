package com.bt.bttune.lyrics

import android.content.Context
import com.bt.bttune.constants.EnableYouTubeMusicLyricsKey
import com.bt.bttune.innertube.YouTube
import com.bt.bttune.innertube.models.WatchEndpoint
import com.bt.bttune.utils.dataStore
import com.bt.bttune.utils.get

object YouTubeLyricsProvider : LyricsProvider {
    override val name = "YouTube Music"

    override fun isEnabled(context: Context) = context.dataStore[EnableYouTubeMusicLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> =
        runCatching {
            val nextResult = YouTube.next(WatchEndpoint(videoId = id)).getOrThrow()
            YouTube
                .lyrics(
                    endpoint = nextResult.lyricsEndpoint
                        ?: throw IllegalStateException("Lyrics endpoint not found"),
                ).getOrThrow() ?: throw IllegalStateException("Lyrics unavailable")
        }
}
