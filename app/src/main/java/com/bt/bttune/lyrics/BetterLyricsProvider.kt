/*
 * BTTUNE Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.bt.bttune.lyrics

import android.content.Context
import com.bt.bttune.betterlyrics.BetterLyrics
import com.bt.bttune.constants.EnableBetterLyricsKey
import com.bt.bttune.utils.dataStore
import com.bt.bttune.utils.get
import android.util.Log

object BetterLyricsProvider : LyricsProvider {
    init {
        BetterLyrics.logger = { message ->
            Log.i("BetterLyrics", message)
        }
    }

    override val name = "BetterLyrics"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableBetterLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = BetterLyrics.getLyrics(title = title, artist = artist, album = null, durationSeconds = duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        BetterLyrics.getAllLyrics(
            title = title,
            artist = artist,
            album = null,
            durationSeconds = duration,
            callback = callback,
        )
    }
}
