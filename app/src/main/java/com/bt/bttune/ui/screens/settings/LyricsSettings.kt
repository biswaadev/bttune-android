package com.bt.bttune.ui.screens.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import com.bt.bttune.R
import com.bt.bttune.constants.*
import com.bt.bttune.ui.component.EnumListPreference
import com.bt.bttune.ui.component.SettingsGeneralCategory
import com.bt.bttune.ui.component.SettingsPage
import com.bt.bttune.ui.component.SwitchPreference
import com.bt.bttune.utils.rememberEnumPreference
import com.bt.bttune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSettings(navController: NavController, scrollBehavior: TopAppBarScrollBehavior) {
    val (position, setPosition) = rememberEnumPreference(LyricsTextPositionKey, LyricsPosition.CENTER)
    val (clickToSeek, setClickToSeek) = rememberPreference(LyricsClickKey, true)
    val (animate, setAnimate) = rememberPreference(AnimateLyricsKey, true)
    val (lyricsScreenStyle, setLyricsScreenStyle) = rememberEnumPreference(LyricsScreenStyleKey, LyricsScreenStyle.LYRICS_2)
    val (preferred, setPreferred) = rememberEnumPreference(PreferredLyricsProviderKey, PreferredLyricsProvider.LRCLIB)
    val providers = listOf(
        LyricsProviderPreference("LRC Lib", EnableLrcLibKey, R.drawable.lyrics_provider_lrclib),
        LyricsProviderPreference("KuGou", EnableKugouKey, R.drawable.lyrics_provider_kugou),
        LyricsProviderPreference("SimpMusic", EnableSimpMusicLyricsKey, R.drawable.lyrics_provider_simpmusic),
        LyricsProviderPreference("Unison", EnableUnisonLyricsKey, R.drawable.lyrics_provider_unison),
        LyricsProviderPreference("YouLy", EnableYouLyLyricsKey, R.drawable.lyrics_provider_youly),
        LyricsProviderPreference("Megalobiz", EnableMegalobizLyricsKey, R.drawable.lyrics_provider_megalobiz),
        LyricsProviderPreference("Paxsenix", EnablePaxsenixLyricsKey, R.drawable.lyrics_provider_paxsenix),
        LyricsProviderPreference("Portato", EnablePortatoLyricsKey, R.drawable.lyrics_provider_portato),
        LyricsProviderPreference("BetterLyrics", EnableBetterLyricsKey, R.drawable.lyrics_provider_betterlyrics),
        LyricsProviderPreference("YouTube subtitles", EnableYouTubeSubtitleLyricsKey, R.drawable.lyrics_provider_youtube_subtitles),
        LyricsProviderPreference("YouTube Music", EnableYouTubeMusicLyricsKey, R.drawable.lyrics_provider_youtube_music),
    )
    SettingsPage(title = "Lyrics", navController = navController, scrollBehavior = scrollBehavior) {
        SettingsGeneralCategory(title = "Lyrics display", items = listOf(
            { EnumListPreference(title = { Text("Text position") }, icon = { Icon(painterResource(R.drawable.lyrics), null) }, selectedValue = position, onValueSelected = setPosition, valueText = { it.name.lowercase().replaceFirstChar(Char::uppercase) }) },
            { SwitchPreference(title = { Text("Tap lyrics to seek") }, icon = { Icon(painterResource(R.drawable.lyrics), null) }, checked = clickToSeek, onCheckedChange = setClickToSeek) },
            { SwitchPreference(title = { Text("Animate lyrics") }, icon = { Icon(painterResource(R.drawable.lyrics), null) }, checked = animate, onCheckedChange = setAnimate) },
            {
                EnumListPreference(
                    title = { Text("Lyrics Screen Style") },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    selectedValue = lyricsScreenStyle,
                    onValueSelected = setLyricsScreenStyle,
                    valueText = {
                        when (it) {
                            LyricsScreenStyle.LYRICS_1 -> "Lyrics 1 (Classic)"
                            LyricsScreenStyle.LYRICS_2 -> "Lyrics 2 (Modern / Apple Style)"
                        }
                    }
                )
            },
        ))
        SettingsGeneralCategory(title = "Lookup order", items = listOf(
            { EnumListPreference(title = { Text("Prefer provider") }, icon = { Icon(painterResource(R.drawable.lyrics), null) }, selectedValue = preferred, onValueSelected = setPreferred, valueText = { it.displayName() + " first" }) },
        ))
        SettingsGeneralCategory(title = "Providers", items = providers.map { provider ->
            { val (enabled, setEnabled) = rememberPreference(provider.key, true); SwitchPreference(title = { Text(provider.name) }, description = "Used automatically when earlier providers have no lyrics", icon = { Icon(painterResource(provider.icon), null) }, checked = enabled, onCheckedChange = setEnabled) }
        })
    }
}

private data class LyricsProviderPreference(
    val name: String,
    val key: androidx.datastore.preferences.core.Preferences.Key<Boolean>,
    val icon: Int,
)

private fun PreferredLyricsProvider.displayName() = when (this) {
    PreferredLyricsProvider.LRCLIB -> "LRC Lib"
    PreferredLyricsProvider.KUGOU -> "KuGou"
    PreferredLyricsProvider.SIMP_MUSIC -> "SimpMusic"
    PreferredLyricsProvider.YOUTUBE_SUBTITLES -> "YouTube subtitles"
    PreferredLyricsProvider.PAXSENIX -> "Paxsenix"
    PreferredLyricsProvider.UNISON -> "Unison"
    PreferredLyricsProvider.BETTER_LYRICS -> "BetterLyrics"
    PreferredLyricsProvider.PORTATO -> "Portato"
    PreferredLyricsProvider.YOULY -> "YouLy"
    PreferredLyricsProvider.MEGALOBIZ -> "Megalobiz"
    PreferredLyricsProvider.YOUTUBE_MUSIC -> "YouTube Music"
}
