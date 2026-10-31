/*
 * BTTUNE Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.bt.bttune.innertube.pages

import com.bt.bttune.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
