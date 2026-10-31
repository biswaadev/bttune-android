package com.bt.bttune.media

data class MediaInfo(
    val hasAudio: Boolean,
    val isVideo: Boolean,
    val durationMs: Long,
    val sampleRate: Int,
    val channelCount: Int,
    val audioMime: String?,
    val fileSize: Long,
    val audioTrackIndex: Int = -1
)
