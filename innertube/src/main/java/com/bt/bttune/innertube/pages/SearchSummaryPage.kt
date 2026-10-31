/*
 * BTTUNE Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.bt.bttune.innertube.pages

import com.bt.bttune.innertube.models.Album
import com.bt.bttune.innertube.models.AlbumItem
import com.bt.bttune.innertube.models.Artist
import com.bt.bttune.innertube.models.ArtistItem
import com.bt.bttune.innertube.models.MusicCardShelfRenderer
import com.bt.bttune.innertube.models.MusicResponsiveListItemRenderer
import com.bt.bttune.innertube.models.PlaylistItem
import com.bt.bttune.innertube.models.SongItem
import com.bt.bttune.innertube.models.YTItem
import com.bt.bttune.innertube.models.filterExplicit
import com.bt.bttune.innertube.models.filterVideo
import com.bt.bttune.innertube.models.oddElements
import com.bt.bttune.innertube.models.splitBySeparator
import com.bt.bttune.innertube.utils.parseTime

data class SearchSummary(
    val title: String,
    val items: List<YTItem>,
)

data class SearchSummaryPage(
    val summaries: List<SearchSummary>,
) {
    fun filterExplicit(enabled: Boolean) =
        if (enabled) {
            SearchSummaryPage(
                summaries.mapNotNull { s ->
                    SearchSummary(
                        title = s.title,
                        items =
                            s.items.filterExplicit().ifEmpty {
                                return@mapNotNull null
                            },
                    )
                },
            )
        } else {
            this
        }

    fun filterVideo(enabled: Boolean) =
        if (enabled) {
            SearchSummaryPage(
                summaries.mapNotNull { s ->
                    SearchSummary(
                        title = s.title,
                        items = s.items.filterVideo().ifEmpty { return@mapNotNull null },
                    )
                },
            )
        } else {
            this
        }

    companion object {
        fun fromMusicCardShelfRenderer(renderer: MusicCardShelfRenderer): YTItem? {
            val subtitle = renderer.subtitle.runs?.splitBySeparator()
            return when {
                renderer.onTap.watchEndpoint != null -> {
                    SongItem(
                        id = renderer.onTap.watchEndpoint.videoId ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        artists =
                            subtitle?.getOrNull(1)?.oddElements()?.map {
                                Artist(
                                    name = it.text,
                                    id = it.navigationEndpoint?.browseEndpoint?.browseId,
                                )
                            } ?: return null,
                        album =
                            subtitle.getOrNull(2)?.firstOrNull()?.takeIf { it.navigationEndpoint?.browseEndpoint != null }?.let {
                                Album(
                                    name = it.text,
                                    id = it.navigationEndpoint?.browseEndpoint?.browseId!!,
                                )
                            },
                        duration =
                            subtitle
                                .lastOrNull()
                                ?.firstOrNull()
                                ?.text
                                ?.parseTime(),
                        thumbnail = renderer.thumbnail.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        explicit =
                            renderer.subtitleBadges?.find {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } != null,
                    )
                }

                renderer.onTap.browseEndpoint?.isArtistEndpoint == true -> {
                    ArtistItem(
                        id = renderer.onTap.browseEndpoint.browseId,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        thumbnail = renderer.thumbnail.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        shuffleEndpoint =
                            renderer.buttons
                                .find { it.buttonRenderer.icon?.iconType == "MUSIC_SHUFFLE" }
                                ?.buttonRenderer
                                ?.command
                                ?.watchPlaylistEndpoint ?: return null,
                        radioEndpoint =
                            renderer.buttons
                                .find { it.buttonRenderer.icon?.iconType == "MIX" }
                                ?.buttonRenderer
                                ?.command
                                ?.watchPlaylistEndpoint ?: return null,
                    )
                }

                renderer.onTap.browseEndpoint?.isAlbumEndpoint == true -> {
                    AlbumItem(
                        browseId = renderer.onTap.browseEndpoint.browseId,
                        playlistId =
                            renderer.buttons
                                .firstOrNull()
                                ?.buttonRenderer
                                ?.command
                                ?.anyWatchEndpoint
                                ?.playlistId ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        artists =
                            subtitle?.getOrNull(1)?.oddElements()?.map {
                                Artist(
                                    name = it.text,
                                    id = it.navigationEndpoint?.browseEndpoint?.browseId,
                                )
                            } ?: return null,
                        year = null,
                        thumbnail = renderer.thumbnail.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        explicit =
                            renderer.subtitleBadges?.find {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } != null,
                    )
                }

                renderer.onTap.browseEndpoint?.isPlaylistEndpoint == true -> {
                    PlaylistItem(
                        id =
                            renderer.onTap.browseEndpoint.browseId
                                .removePrefix("VL"),
                        title =
                            renderer.header?.musicCardShelfHeaderBasicRenderer?.title?.runs
                                ?.joinToString(separator = "") { it.text }
                                ?: return null,
                        author =
                            Artist(
                                id = null,
                                name = renderer.subtitle.runs?.joinToString { it.text } ?: return null,
                            ),
                        songCountText = null,
                        thumbnail = renderer.thumbnail.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        playEndpoint =
                            renderer.buttons
                                .find { it.buttonRenderer.icon?.iconType == "PLAY_ARROW" }
                                ?.buttonRenderer
                                ?.command
                                ?.watchPlaylistEndpoint
                                ?: return null,
                        shuffleEndpoint =
                            renderer.buttons
                                .find { it.buttonRenderer.icon?.iconType == "MUSIC_SHUFFLE" }
                                ?.buttonRenderer
                                ?.command
                                ?.watchPlaylistEndpoint
                                ?: return null,
                        radioEndpoint = null,
                    )
                }

                else -> null
            }
        }

        fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): YTItem? =
            SearchPage.toYTItem(renderer)
    }
}
