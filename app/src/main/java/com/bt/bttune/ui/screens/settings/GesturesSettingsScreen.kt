package com.bt.bttune.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import com.bt.bttune.R
import com.bt.bttune.constants.EnableMiniPlayerSwipeKey
import com.bt.bttune.constants.EnablePlayerDoubleTapSeekKey
import com.bt.bttune.constants.EnableSwipeBackGestureKey
import com.bt.bttune.constants.EnableTabSwipeGestureKey
import com.bt.bttune.ui.component.SettingsGeneralCategory
import com.bt.bttune.ui.component.SettingsPage
import com.bt.bttune.ui.component.SwitchPreference
import com.bt.bttune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GesturesSettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (enableSwipeBack, setEnableSwipeBack) = rememberPreference(
        EnableSwipeBackGestureKey,
        defaultValue = true
    )
    val (enableTabSwipe, setEnableTabSwipe) = rememberPreference(
        EnableTabSwipeGestureKey,
        defaultValue = true
    )
    val (enableMiniPlayerSwipe, setEnableMiniPlayerSwipe) = rememberPreference(
        EnableMiniPlayerSwipeKey,
        defaultValue = true
    )
    val (enableDoubleTapSeek, setEnableDoubleTapSeek) = rememberPreference(
        EnablePlayerDoubleTapSeekKey,
        defaultValue = true
    )

    SettingsPage(
        title = "Gestures",
        navController = navController,
        scrollBehavior = scrollBehavior
    ) {
        SettingsGeneralCategory(
            title = "Navigation Gestures",
            items = listOf(
                {
                    SwitchPreference(
                        title = { Text("Swipe to Go Back") },
                        description = "Slide from left edge towards the right. The screen follows your finger and returns to the previous screen",
                        icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                        checked = enableSwipeBack,
                        onCheckedChange = setEnableSwipeBack
                    )
                },
                {
                    SwitchPreference(
                        title = { Text("Universal Tab Swipe") },
                        description = "Swipe left or right across Home, Explore, and Library to switch screens easily",
                        icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                        checked = enableTabSwipe,
                        onCheckedChange = setEnableTabSwipe
                    )
                },
            )
        )

        SettingsGeneralCategory(
            title = "Playback Gestures",
            items = listOf(
                {
                    SwitchPreference(
                        title = { Text("Mini-Player Swipe to Skip") },
                        description = "Slide left or right on the bottom mini-player bar to skip to the next or previous song",
                        icon = { Icon(Icons.Default.SkipNext, contentDescription = null) },
                        checked = enableMiniPlayerSwipe,
                        onCheckedChange = setEnableMiniPlayerSwipe
                    )
                },
                {
                    SwitchPreference(
                        title = { Text("Player Double Tap Seek") },
                        description = "Double tap the left or right side of the now playing screen artwork to rewind or fast forward 10 seconds",
                        icon = { Icon(Icons.Default.FastForward, contentDescription = null) },
                        checked = enableDoubleTapSeek,
                        onCheckedChange = setEnableDoubleTapSeek
                    )
                },
            )
        )
    }
}
