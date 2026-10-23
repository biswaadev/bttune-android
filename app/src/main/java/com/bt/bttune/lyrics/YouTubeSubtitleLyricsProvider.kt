package com.bt.bttune.lyrics

import android.content.Context
import com.bt.bttune.constants.EnableYouTubeSubtitleLyricsKey
import com.bt.bttune.innertube.YouTube
import com.bt.bttune.utils.dataStore
import com.bt.bttune.utils.get

object YouTubeSubtitleLyricsProvider : LyricsProvider {
    override val name = "YouTube Subtitle"

    override fun isEnabled(context: Context) = context.dataStore[EnableYouTubeSubtitleLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = YouTube.transcript(id)
}
