package com.bt.bttune.utils

import com.bt.bttune.innertube.YouTube
import com.bt.bttune.innertube.models.AlbumItem
import com.bt.bttune.innertube.models.ArtistItem
import com.bt.bttune.innertube.models.PlaylistItem
import com.bt.bttune.innertube.models.SongItem
import com.bt.bttune.innertube.utils.completedLibraryPage
import com.bt.bttune.innertube.utils.completedPlaylistPage
import com.bt.bttune.db.MusicDatabase
import com.bt.bttune.db.entities.ArtistEntity
import com.bt.bttune.db.entities.PlaylistEntity
import com.bt.bttune.db.entities.PlaylistSongMap
import com.bt.bttune.db.entities.SongEntity
import com.bt.bttune.models.toMediaMetadata
import android.content.Context
import com.bt.bttune.constants.SpotifyCookieKey
import com.bt.bttune.spotify.Spotify
import com.bt.bttune.spotify.SpotifyAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncUtils @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
) {
    suspend fun syncLikedSongs() {
        YouTube.playlist("LM").completedPlaylistPage().onSuccess { page ->
            val songs = page.songs.reversed()

            database.likedSongsByNameAsc().first()
                .filterNot { it.id in songs.map(SongItem::id) }
                .forEach { database.update(it.song.localToggleLike()) }

            songs.forEach { song ->
                val dbSong = database.song(song.id).firstOrNull()
                database.transaction {
                    when (dbSong) {
                        null -> insert(song.toMediaMetadata(), SongEntity::localToggleLike)
                        else -> if (!dbSong.song.liked) update(dbSong.song.localToggleLike())
                    }
                }
            }
        }
    }

    suspend fun syncLibrarySongs() {
        YouTube.library("FEmusic_liked_videos").completedLibraryPage().onSuccess { page ->
            val songs = page.items.filterIsInstance<SongItem>().reversed()

            database.songsByNameAsc().first()
                .filterNot { it.id in songs.map(SongItem::id) }
                .forEach { database.update(it.song.toggleLibrary()) }

            songs.forEach { song ->
                val dbSong = database.song(song.id).firstOrNull()
                database.transaction {
                    when (dbSong) {
                        null -> insert(song.toMediaMetadata(), SongEntity::toggleLibrary)
                        else -> if (dbSong.song.inLibrary == null) update(dbSong.song.toggleLibrary())
                    }
                }
            }
        }
    }

    suspend fun syncLikedAlbums() {
        YouTube.library("FEmusic_liked_albums").completedLibraryPage().onSuccess { page ->
            val albums = page.items.filterIsInstance<AlbumItem>().reversed()

            database.albumsLikedByNameAsc().first()
                .filterNot { it.id in albums.map(AlbumItem::id) }
                .forEach { database.update(it.album.localToggleLike()) }

            albums.forEach { album ->
                val dbAlbum = database.album(album.id).firstOrNull()
                YouTube.album(album.browseId).onSuccess { albumPage ->
                    when (dbAlbum) {
                        null -> {
                            database.insert(albumPage)
                            database.album(album.id).firstOrNull()?.let {
                                database.update(it.album.localToggleLike())
                            }
                        }
                        else -> if (dbAlbum.album.bookmarkedAt == null)
                            database.update(dbAlbum.album.localToggleLike())
                    }
                }
            }
        }
    }

    suspend fun syncArtistsSubscriptions() {
        YouTube.library("FEmusic_library_corpus_artists").completedLibraryPage().onSuccess { page ->
            val artists = page.items.filterIsInstance<ArtistItem>()

            database.artistsBookmarkedByNameAsc().first()
                .filterNot { it.id in artists.map(ArtistItem::id) }
                .forEach { database.update(it.artist.localToggleLike()) }

            artists.forEach { artist ->
                val dbArtist = database.artist(artist.id).firstOrNull()
                database.transaction {
                    when (dbArtist) {
                        null -> {
                            insert(
                                ArtistEntity(
                                    id = artist.id,
                                    name = artist.title,
                                    thumbnailUrl = artist.thumbnail,
                                    channelId = artist.channelId,
                                    bookmarkedAt = LocalDateTime.now()
                                )
                            )
                        }
                        else -> if (dbArtist.artist.bookmarkedAt == null)
                            update(dbArtist.artist.localToggleLike())
                    }
                }
            }
        }
    }

    suspend fun syncSavedPlaylists() {
        YouTube.library("FEmusic_liked_playlists").completedLibraryPage()
            .onSuccess { page ->
                val playlistList = page.items.filterIsInstance<PlaylistItem>()
                    .filterNot { it.id == "LM" ||  it.id == "SE" }
                    .reversed()
                val dbPlaylists = database.playlistsByNameAsc().first()

                playlistList.onEach { playlist ->
                    var playlistEntity = dbPlaylists.find { playlist.id == it.playlist.browseId }?.playlist
                    if (playlistEntity == null) {
                        playlistEntity = PlaylistEntity(
                            name = playlist.title,
                            browseId = playlist.id,
                            isEditable = playlist.isEditable,
                            bookmarkedAt = LocalDateTime.now(),
                            remoteSongCount = playlist.songCountText?.let {
                                Regex("""\d+""").find(it)?.value?.toIntOrNull()
                            }
                        )

                        database.insert(playlistEntity)
                    } else database.update(playlistEntity, playlist)

                    syncPlaylist(playlist.id, playlistEntity.id)
                }
            }
            .onFailure { exception ->
                reportException(exception)
            }
    }

    suspend fun syncPlaylist(browseId: String, playlistId: String) {
        val playlistPage = YouTube.playlist(browseId).completedPlaylistPage().getOrNull() ?: return
        database.transaction {
            clearPlaylist(playlistId)
            playlistPage.songs
                .map(SongItem::toMediaMetadata)
                .onEach(::insert)
                .mapIndexed { position, song ->
                    PlaylistSongMap(
                        songId = song.id,
                        playlistId = playlistId,
                        position = position,
                        setVideoId = song.setVideoId
                    )
                }.forEach(::insert)
        }
    }

    suspend fun syncSpotifyPlaylists() {
        val spDc = context.dataStore.data.first()[SpotifyCookieKey]
        if (spDc.isNullOrBlank()) return

        val cleanSpDc = if (spDc.startsWith("sp_dc=")) spDc.substringAfter("sp_dc=").substringBefore(";") else spDc
        if (Spotify.accessToken.isNullOrBlank()) {
            SpotifyAuth.fetchAccessToken(cleanSpDc).onSuccess { token ->
                Spotify.accessToken = token.accessToken
            }.onFailure {
                reportException(it)
                return
            }
        }

        Spotify.myPlaylists(limit = 50).onSuccess { paging ->
            val dbPlaylists = database.playlistsByNameAsc().first()
            paging.items.forEach { spPlaylist ->
                val browseId = "sp:${spPlaylist.id}"
                val count = spPlaylist.tracks?.total ?: 0
                val existing = dbPlaylists.find { it.playlist.browseId == browseId }?.playlist

                if (existing == null) {
                    val entity = PlaylistEntity(
                        id = "sp_${spPlaylist.id}",
                        name = spPlaylist.name,
                        browseId = browseId,
                        isEditable = false,
                        bookmarkedAt = LocalDateTime.now(),
                        remoteSongCount = count
                    )
                    database.insert(entity)
                } else {
                    database.update(
                        existing.copy(
                            name = spPlaylist.name,
                            remoteSongCount = count
                        )
                    )
                }
            }
        }.onFailure {
            reportException(it)
        }
    }
}
