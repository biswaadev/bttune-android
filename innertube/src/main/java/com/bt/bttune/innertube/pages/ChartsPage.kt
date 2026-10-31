/*
 * BTTUNE Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.bt.bttune.innertube.pages

import com.bt.bttune.innertube.models.*

data class ChartsPage(
    val sections: List<ChartSection>,
    val continuation: String?
) {
    data class ChartSection(
        val title: String,
        val items: List<YTItem>,
        val chartType: ChartType
    )

    enum class ChartType {
        TRENDING, TOP, GENRE, NEW_RELEASES
    }
}
