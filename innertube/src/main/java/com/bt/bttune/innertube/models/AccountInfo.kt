/*
 * BTTUNE Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.bt.bttune.innertube.models

data class AccountInfo(
    val name: String,
    val email: String?,
    val channelHandle: String?,
    val thumbnailUrl: String?,
)
