package com.bt.bttune.models

import com.bt.bttune.innertube.models.YTItem

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)
