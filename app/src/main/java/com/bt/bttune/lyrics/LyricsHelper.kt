package com.bt.bttune.lyrics

import android.content.Context
import android.util.LruCache
import com.bt.bttune.constants.PreferredLyricsProvider
import com.bt.bttune.constants.PreferredLyricsProviderKey
import com.bt.bttune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.bt.bttune.extensions.toEnum
import com.bt.bttune.models.MediaMetadata
import com.bt.bttune.utils.dataStore
import com.bt.bttune.utils.get
import com.bt.bttune.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import androidx.annotation.Keep

import com.bt.bttune.lyrics.providers.BTTUNESimpLyricsProvider
import com.bt.bttune.lyrics.providers.BTTUNEUnisonLyricsProvider
import com.bt.bttune.lyrics.providers.BTTUNEYouLyLyricsProvider
import com.bt.bttune.lyrics.providers.BTTUNEMegalobizLyricsProvider
import com.bt.bttune.lyrics.providers.BTTUNEPaxsenixLyricsProvider
import com.bt.bttune.lyrics.providers.BTTUNEPortatoLyricsProvider

@Keep
class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    private val allLyricsProviders =
        listOf(
            LrcLibLyricsProvider,
            BTTUNESimpLyricsProvider,
            YouTubeSubtitleLyricsProvider,
            KuGouLyricsProvider,
            BTTUNEPaxsenixLyricsProvider,
            BTTUNEUnisonLyricsProvider,
            BetterLyricsProvider,
            BTTUNEPortatoLyricsProvider,
            BTTUNEYouLyLyricsProvider,
            BTTUNEMegalobizLyricsProvider,
            YouTubeLyricsProvider
        )
    private var lyricsProviders = allLyricsProviders
    val preferred =
        context.dataStore.data
            .map {
                it[PreferredLyricsProviderKey].toEnum(PreferredLyricsProvider.LRCLIB)
            }.distinctUntilChanged()
            .map(::setPreferredProvider)

    private fun setPreferredProvider(preferredProvider: PreferredLyricsProvider) {
        val primary = when (preferredProvider) {
                    PreferredLyricsProvider.LRCLIB -> LrcLibLyricsProvider
                    PreferredLyricsProvider.KUGOU -> KuGouLyricsProvider
                    PreferredLyricsProvider.SIMP_MUSIC -> BTTUNESimpLyricsProvider
                    PreferredLyricsProvider.YOUTUBE_SUBTITLES -> YouTubeSubtitleLyricsProvider
                    PreferredLyricsProvider.PAXSENIX -> BTTUNEPaxsenixLyricsProvider
                    PreferredLyricsProvider.UNISON -> BTTUNEUnisonLyricsProvider
                    PreferredLyricsProvider.BETTER_LYRICS -> BetterLyricsProvider
                    PreferredLyricsProvider.PORTATO -> BTTUNEPortatoLyricsProvider
                    PreferredLyricsProvider.YOULY -> BTTUNEYouLyLyricsProvider
                    PreferredLyricsProvider.MEGALOBIZ -> BTTUNEMegalobizLyricsProvider
                    PreferredLyricsProvider.YOUTUBE_MUSIC -> YouTubeLyricsProvider
        }
        lyricsProviders = listOf(primary) + allLyricsProviders.filter { it !== primary }
    }

    private fun refreshProviderOrder() {
        setPreferredProvider(
            context.dataStore[PreferredLyricsProviderKey]
                .toEnum(PreferredLyricsProvider.LRCLIB),
        )
    }
    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)

    suspend fun getLyrics(mediaMetadata: MediaMetadata): String {
        refreshProviderOrder()
        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return cached.lyrics
        }
        lyricsProviders.forEach { provider ->
            if (provider.isEnabled(context)) {
                provider
                    .getLyrics(
                        mediaMetadata.id,
                        mediaMetadata.title,
                        mediaMetadata.artists.joinToString { it.name },
                        mediaMetadata.duration,
                    ).onSuccess { lyrics ->
                        return lyrics
                    }.onFailure {
                        reportException(it)
                    }
            }
        }
        return LYRICS_NOT_FOUND
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        callback: (LyricsResult) -> Unit,
    ) {
        refreshProviderOrder()
        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }
        val allResult = mutableListOf<LyricsResult>()
        lyricsProviders.forEach { provider ->
            if (provider.isEnabled(context)) {
                runCatching {
                    provider.getAllLyrics(mediaId, songTitle, songArtists, duration) { lyrics ->
                        val result = LyricsResult(provider.name, lyrics)
                        allResult += result
                        callback(result)
                    }
                }.onFailure {
                    reportException(it)
                }
            }
        }
        cache.put(cacheKey, allResult)
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
