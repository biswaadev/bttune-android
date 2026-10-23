package com.bt.bttune.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bt.bttune.constants.SpotifyCookieKey
import com.bt.bttune.innertube.YouTube
import com.bt.bttune.innertube.models.AlbumItem
import com.bt.bttune.innertube.models.ArtistItem
import com.bt.bttune.innertube.models.PlaylistItem
import com.bt.bttune.innertube.utils.completedLibraryPage
import com.bt.bttune.spotify.Spotify
import com.bt.bttune.spotify.SpotifyAuth
import com.bt.bttune.spotify.models.SpotifyHomeFeed
import com.bt.bttune.spotify.models.SpotifyPlaylist
import com.bt.bttune.spotify.models.SpotifyUser
import com.bt.bttune.utils.dataStore
import com.bt.bttune.utils.getSuspend
import com.bt.bttune.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    val playlists = MutableStateFlow<List<PlaylistItem>?>(null)
    val albums = MutableStateFlow<List<AlbumItem>?>(null)
    val artists = MutableStateFlow<List<ArtistItem>?>(null)

    val spotifyUser = MutableStateFlow<SpotifyUser?>(null)
    val spotifyPlaylists = MutableStateFlow<List<SpotifyPlaylist>?>(null)
    val spotifyFeed = MutableStateFlow<SpotifyHomeFeed?>(null)
    val isSpotifyLoading = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            YouTube.library("FEmusic_liked_playlists").completedLibraryPage().onSuccess {
                playlists.value = it.items.filterIsInstance<PlaylistItem>()
                    .filterNot { it.id == "SE" }
            }.onFailure {
                reportException(it)
            }
            YouTube.library("FEmusic_liked_albums").completedLibraryPage().onSuccess {
                albums.value = it.items.filterIsInstance<AlbumItem>()
            }.onFailure {
                reportException(it)
            }
            YouTube.library("FEmusic_library_corpus_artists").completedLibraryPage().onSuccess {
                artists.value = it.items.filterIsInstance<ArtistItem>()
            }.onFailure {
                reportException(it)
            }
        }
    }

    fun loadSpotifyData() {
        if (isSpotifyLoading.value || spotifyUser.value != null) return
        isSpotifyLoading.value = true
        viewModelScope.launch {
            try {
                val spDc = context.dataStore.getSuspend(SpotifyCookieKey)
                if (spDc != null) {
                    val tokenResult = SpotifyAuth.fetchAccessToken(spDc)
                    tokenResult.onSuccess { token ->
                        Spotify.accessToken = token.accessToken
                        
                        Spotify.me().onSuccess { user ->
                            spotifyUser.value = user
                        }.onFailure { reportException(it) }

                        Spotify.myPlaylists(limit = 20).onSuccess { paging ->
                            spotifyPlaylists.value = paging.items
                        }.onFailure { reportException(it) }

                        Spotify.home().onSuccess { feed ->
                            spotifyFeed.value = feed
                        }.onFailure { reportException(it) }
                    }.onFailure {
                        reportException(it)
                    }
                }
            } catch (e: Exception) {
                reportException(e)
            } finally {
                isSpotifyLoading.value = false
            }
        }
    }
}
