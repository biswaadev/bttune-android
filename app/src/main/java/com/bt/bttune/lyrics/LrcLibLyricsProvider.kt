package com.bt.bttune.lyrics

import android.content.Context
import com.bt.bttune.lrclib.LrcLib
import com.bt.bttune.constants.EnableLrcLibKey
import com.bt.bttune.utils.dataStore
import com.bt.bttune.utils.get

object LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableLrcLibKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = LrcLib.getLyrics(title, artist, duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        runCatching {
            LrcLib.getAllLyrics(title, artist, duration, null, callback)
        }
    }
}
