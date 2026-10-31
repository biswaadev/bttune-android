package com.bt.bttune.providers

import com.bt.bttune.models.MediaMetadata

data class ProviderSong(
    val id: String,
    val title: String,
    val artists: List<String>,
    val duration: Int,
    val thumbnailUrl: String?,
    val mediaMetadata: MediaMetadata,
    val provider: String = "YT"
)
