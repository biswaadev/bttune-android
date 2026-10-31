package com.bt.bttune.models

import com.bt.bttune.innertube.models.YTItem
import com.bt.bttune.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
