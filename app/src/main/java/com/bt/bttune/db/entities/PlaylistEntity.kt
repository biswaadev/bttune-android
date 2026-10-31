package com.bt.bttune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bt.bttune.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey val id: String = generatePlaylistId(),
    val name: String,
    val browseId: String? = null,
    val createdAt: LocalDateTime? = LocalDateTime.now(),
    val lastUpdateTime: LocalDateTime? = LocalDateTime.now(),
    @ColumnInfo(name = "isEditable", defaultValue = true.toString())
    val isEditable: Boolean = true,
    val bookmarkedAt: LocalDateTime? = null,
    val remoteSongCount: Int? = null,
    val playEndpointParams: String? = null,
    val shuffleEndpointParams: String? = null,
    val radioEndpointParams: String? = null
) {
    companion object {
        const val LIKED_PLAYLIST_ID = "LP_LIKED"
        const val DOWNLOADED_PLAYLIST_ID = "LP_DOWNLOADED"

        fun generatePlaylistId() = "LP" + (1..8).map { (('a'..'z') + ('A'..'Z')).random() }.joinToString("")
    }

    val shareLink: String?
        get() {
            return if (browseId != null)
                com.bt.bttune.utils.RemoteConfigManager.getPlaylistShareUrl(browseId)
            else null
        }

    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now()
    )

    fun toggleLike() = localToggleLike().also {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                if (browseId != null)
                    YouTube.likePlaylist(browseId, bookmarkedAt == null)
            }
        }
    }
}
