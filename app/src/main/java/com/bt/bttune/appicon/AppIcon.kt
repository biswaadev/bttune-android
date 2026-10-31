package com.bt.bttune.appicon

import androidx.compose.ui.graphics.Color

/**
 * Data model representing an app launcher icon variant in BTTUNE.
 */
data class AppIcon(
    val id: String,
    val title: String,
    val subtitle: String,
    val author: String = "BTTUNE Team",
    val aliasName: String,
    val bgColors: List<Color>,
    val fgTint: Color?,
    val isDefault: Boolean = false,
    val isCommunity: Boolean = false,
    val inApp: Boolean = true,
    val svgUrl: String? = null
)
