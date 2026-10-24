package com.bt.bttune.ui.screens

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import com.bt.bttune.ui.component.BottomSheetState
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.bt.bttune.BuildConfig
import com.bt.bttune.constants.HomeScreenStyle
import com.bt.bttune.constants.HomeScreenStyleKey
import com.bt.bttune.utils.rememberEnumPreference
import com.bt.bttune.ui.screens.artist.ArtistItemsScreen
import com.bt.bttune.ui.screens.artist.ArtistScreen
import com.bt.bttune.ui.screens.artist.ArtistSongsScreen
import com.bt.bttune.ui.screens.library.CachePlaylistScreen
import com.bt.bttune.ui.screens.library.LibraryScreen
import com.bt.bttune.ui.screens.library.LocalSongsScreen
import com.bt.bttune.ui.screens.library.PlayfulLibraryScreen
import com.bt.bttune.ui.screens.playlist.AutoPlaylistScreen
import com.bt.bttune.ui.screens.playlist.LocalPlaylistScreen
import com.bt.bttune.ui.screens.playlist.OnlinePlaylistScreen
import com.bt.bttune.ui.screens.playlist.TopPlaylistScreen
import com.bt.bttune.ui.screens.search.OnlineSearchResult
import com.bt.bttune.ui.screens.settings.AboutScreen
import com.bt.bttune.ui.screens.settings.AccountSettings
import com.bt.bttune.ui.screens.settings.AODSettings
import com.bt.bttune.ui.screens.settings.AppearanceSettings
import com.bt.bttune.ui.screens.settings.BackupAndRestore
import com.bt.bttune.ui.screens.settings.ContentSettings
import com.bt.bttune.ui.screens.settings.DiscordLoginScreen
import com.bt.bttune.ui.screens.settings.DiscordSettings
import com.bt.bttune.ui.screens.settings.PlayerSettings
import com.bt.bttune.ui.screens.settings.PrivacySettings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import com.bt.bttune.ui.screens.settings.SettingsScreen
import com.bt.bttune.ui.screens.settings.StorageSettings

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
    playerBottomSheetState: BottomSheetState,
    onSearchClick: () -> Unit,
    rootPagerState: PagerState? = null,
    navigationItems: List<Screens> = emptyList(),
) {
    composable(Screens.Home.route) {
        if (rootPagerState != null && navigationItems.isNotEmpty()) {
            HorizontalPager(
                state = rootPagerState,
                modifier = Modifier.fillMaxSize(),
                key = { index -> navigationItems.getOrNull(index)?.route ?: index.toString() }
            ) { pageIndex ->
                val screen = navigationItems.getOrNull(pageIndex) ?: Screens.Home
                val (homeScreenStyle, _) = rememberEnumPreference(
                    HomeScreenStyleKey,
                    defaultValue = HomeScreenStyle.CLASSIC
                )
                when (screen.route) {
                    Screens.Home.route -> {
                        when (homeScreenStyle) {
                            HomeScreenStyle.PLAYFUL -> PlayfulHomeScreen(navController = navController, playerBottomSheetState = playerBottomSheetState, onSearchClick = onSearchClick)
                            HomeScreenStyle.SPOTIFY -> SpotifyHomeScreen(navController = navController)
                            HomeScreenStyle.APPLE -> com.bt.bttune.ui.screens.apple.AppleHomeScreen(navController = navController)
                            HomeScreenStyle.NEW_CLASSIC -> NewClassicHomeScreen(navController = navController, onSearchClick = onSearchClick)
                            HomeScreenStyle.BETTER -> BetterHomeScreen(navController = navController, onSearchClick = onSearchClick)
                            HomeScreenStyle.MATERIAL -> com.bt.bttune.ui.screens.material.MaterialHomeScreen(navController = navController, onSearchClick = onSearchClick)
                            else -> HomeScreen(navController = navController, onSearchClick = onSearchClick)
                        }
                    }
                    Screens.Explore.route -> {
                        when (homeScreenStyle) {
                            HomeScreenStyle.PLAYFUL -> PlayfulExploreScreen(navController = navController, playerBottomSheetState = playerBottomSheetState, onSearchClick = onSearchClick)
                            HomeScreenStyle.SPOTIFY -> SpotifyExploreScreen(navController = navController)
                            HomeScreenStyle.APPLE -> com.bt.bttune.ui.screens.apple.AppleExploreScreen(navController = navController)
                            HomeScreenStyle.MATERIAL -> com.bt.bttune.ui.screens.material.MaterialExploreScreen(navController = navController, scrollBehavior = scrollBehavior)
                            else -> ExploreScreen(navController, scrollBehavior)
                        }
                    }
                    Screens.Library.route -> {
                        when (homeScreenStyle) {
                            HomeScreenStyle.PLAYFUL -> PlayfulLibraryScreen(navController = navController, playerBottomSheetState = playerBottomSheetState, onSearchClick = onSearchClick)
                            HomeScreenStyle.SPOTIFY -> SpotifyLibraryScreen(navController)
                            HomeScreenStyle.APPLE -> com.bt.bttune.ui.screens.apple.AppleLibraryScreen(navController = navController)
                            HomeScreenStyle.MATERIAL -> com.bt.bttune.ui.screens.material.MaterialLibraryScreen(navController = navController)
                            else -> LibraryScreen(navController)
                        }
                    }
                    Screens.Search.route -> {
                        com.bt.bttune.ui.screens.material.MaterialSearchScreen(navController = navController)
                    }
                    "stats" -> {
                        if (homeScreenStyle == HomeScreenStyle.APPLE) {
                            com.bt.bttune.ui.screens.apple.AppleStatsScreen(navController = navController)
                        } else {
                            StatsScreen(navController)
                        }
                    }
                    else -> {
                        HomeScreen(navController = navController, onSearchClick = onSearchClick)
                    }
                }
            }
        } else {
            val (homeScreenStyle, _) = rememberEnumPreference(
                HomeScreenStyleKey,
                defaultValue = HomeScreenStyle.CLASSIC
            )

            if (homeScreenStyle == HomeScreenStyle.PLAYFUL) {
                PlayfulHomeScreen(navController = navController, playerBottomSheetState = playerBottomSheetState, onSearchClick = onSearchClick)
            } else if (homeScreenStyle == HomeScreenStyle.SPOTIFY) {
                SpotifyHomeScreen(navController = navController)
            } else if (homeScreenStyle == HomeScreenStyle.APPLE) {
                com.bt.bttune.ui.screens.apple.AppleHomeScreen(navController = navController)
            } else if (homeScreenStyle == HomeScreenStyle.NEW_CLASSIC) {
                NewClassicHomeScreen(navController = navController, onSearchClick = onSearchClick)
            } else if (homeScreenStyle == HomeScreenStyle.BETTER) {
                BetterHomeScreen(navController = navController, onSearchClick = onSearchClick)
            } else if (homeScreenStyle == HomeScreenStyle.MATERIAL) {
                com.bt.bttune.ui.screens.material.MaterialHomeScreen(navController = navController, onSearchClick = onSearchClick)
            } else {
                HomeScreen(navController = navController, onSearchClick = onSearchClick)
            }
        }
    }

    composable(
        Screens.Library.route,
    ) {
        val (homeScreenStyle, _) = rememberEnumPreference(
            HomeScreenStyleKey,
            defaultValue = HomeScreenStyle.CLASSIC
        )

        if (homeScreenStyle == HomeScreenStyle.PLAYFUL) {
            PlayfulLibraryScreen(
                navController = navController,
                playerBottomSheetState = playerBottomSheetState,
                onSearchClick = onSearchClick
            )
        } else if (homeScreenStyle == HomeScreenStyle.SPOTIFY) {
            SpotifyLibraryScreen(navController)
        } else if (homeScreenStyle == HomeScreenStyle.APPLE) {
            com.bt.bttune.ui.screens.apple.AppleLibraryScreen(navController = navController)
        } else if (homeScreenStyle == HomeScreenStyle.MATERIAL) {
            com.bt.bttune.ui.screens.material.MaterialLibraryScreen(navController = navController)
        } else {
            LibraryScreen(navController)
        }
    }
    composable(Screens.Explore.route) {
        val (homeScreenStyle, _) = rememberEnumPreference(
            HomeScreenStyleKey,
            defaultValue = HomeScreenStyle.CLASSIC
        )

        if (homeScreenStyle == HomeScreenStyle.PLAYFUL) {
            PlayfulExploreScreen(
                navController = navController,
                playerBottomSheetState = playerBottomSheetState,
                onSearchClick = onSearchClick
            )
        } else if (homeScreenStyle == HomeScreenStyle.SPOTIFY) {
            SpotifyExploreScreen(navController = navController)
        } else if (homeScreenStyle == HomeScreenStyle.APPLE) {
            com.bt.bttune.ui.screens.apple.AppleExploreScreen(navController = navController)
        } else if (homeScreenStyle == HomeScreenStyle.MATERIAL) {
            com.bt.bttune.ui.screens.material.MaterialExploreScreen(navController = navController, scrollBehavior = scrollBehavior)
        } else {
            ExploreScreen(navController,scrollBehavior)
        }
    }
    composable(Screens.Search.route) {
        com.bt.bttune.ui.screens.material.MaterialSearchScreen(navController = navController)
    }
    composable("search/") {
        com.bt.bttune.ui.screens.material.MaterialSearchScreen(navController = navController)
    }
    composable("history") {
        HistoryScreen(navController)
    }
    composable("onboarding") {
        com.bt.bttune.ui.screens.onboarding.OnboardingScreen(
            navController = navController
        )
    }
    composable("guest_profile_setup") {
        com.bt.bttune.ui.screens.onboarding.GuestProfileSetupScreen(navController = navController)
    }
    composable("stats") {
        val (homeScreenStyle, _) = rememberEnumPreference(
            HomeScreenStyleKey,
            defaultValue = HomeScreenStyle.CLASSIC
        )
        if (homeScreenStyle == HomeScreenStyle.APPLE) {
            com.bt.bttune.ui.screens.apple.AppleStatsScreen(navController = navController)
        } else {
            StatsScreen(navController)
        }
    }
    composable("account") {
        AccountScreen(navController, scrollBehavior)
    }
    composable("spotify_login") {
        SpotifyLoginScreen(navController)
    }
    composable("spotify_account") {
        com.bt.bttune.ui.screens.settings.SpotifyAccountScreen(navController)
    }
    composable("new_release") {
        NewReleaseScreen(navController, scrollBehavior)
    }
    composable("insight") {
        InsightScreen(navController)
    }
    composable("year_in_music") {
        YearInMusicScreen(navController)
    }
    composable(
        route = "listen_together",
        deepLinks = listOf(
            navDeepLink { uriPattern = "bttune://together.*" }
        )
    ) {
        ListenTogetherScreen(navController, scrollBehavior)
    }
    composable(com.bt.bttune.ui.screens.musicrecognition.MusicRecognitionRoute) {
        com.bt.bttune.ui.screens.musicrecognition.MusicRecognitionScreen(navController)
    }

    composable("generator") {
        com.bt.bttune.ui.screens.generator.GenerateScreen(
            navController = navController,
            onBack = { navController.popBackStack() },
            onNavigateToPlaylist = { playlistId ->
                navController.popBackStack()
                navController.navigate("local_playlist/$playlistId")
            }
        )
    }

    composable(
        route = "search/{query}",
        arguments =
            listOf(
                navArgument("query") {
                    type = NavType.StringType
                },
            ),
        enterTransition = {
            fadeIn(tween(250))
        },
        exitTransition = {
            if (targetState.destination.route?.startsWith("search/") == true) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }
        },
        popEnterTransition = {
            if (initialState.destination.route?.startsWith("search/") == true) {
                fadeIn(tween(250))
            } else {
                fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
            }
        },
        popExitTransition = {
            fadeOut(tween(200))
        },
    ) {
        OnlineSearchResult(navController)
    }
    composable(
        route = "album/{albumId}",
        arguments =
            listOf(
                navArgument("albumId") {
                    type = NavType.StringType
                },
            ),
    ) {
        AlbumScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) { backStackEntry ->
        val artistId = backStackEntry.arguments?.getString("artistId")!!
        if (artistId.startsWith("LA")) {
            ArtistSongsScreen(navController, scrollBehavior)
        } else {
            ArtistScreen(navController, scrollBehavior)
        }
    }
    composable(
        route = "artist/{artistId}/songs",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistSongsScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/items?browseId={browseId}&params={params}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        ArtistItemsScreen(navController, scrollBehavior)
    }
    composable(
        route = "online_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        OnlinePlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "local_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        LocalPlaylistScreen(navController, scrollBehavior)
    }
    composable(route = "local_songs") {
        LocalSongsScreen(navController)
    }
    composable(
        route = "auto_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        AutoPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "cache_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        CachePlaylistScreen(navController, scrollBehavior)
    }



    composable(
        route = "top_playlist/{top}",
        arguments =
            listOf(
                navArgument("top") {
                    type = NavType.StringType
                },
            ),
    ) {
        TopPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        YouTubeBrowseScreen(navController)
    }


    composable("settings") {
        val latestVersion by mutableLongStateOf(BuildConfig.VERSION_CODE.toLong())
        SettingsScreen(latestVersion, navController, scrollBehavior)
    }
    composable("settings/appearance") {
        AppearanceSettings(navController, scrollBehavior)
    }
    composable("settings/appearance/app_icon") {
        com.bt.bttune.ui.screens.settings.AppIconScreen(navController, scrollBehavior)
    }
    composable("settings/dynamic_island") {
        com.bt.bttune.ui.screens.settings.DynamicIslandSettings(navController, scrollBehavior)
    }
    composable("settings/always_on_display") {
        AODSettings(navController, scrollBehavior)
    }
    composable("settings/account") {
        AccountSettings(navController, scrollBehavior)
    }
    composable("settings/content") {
        ContentSettings(navController, scrollBehavior)
    }
    composable("settings/content/excluded_songs") {
        com.bt.bttune.ui.screens.settings.ExcludedSongsScreen(navController, scrollBehavior)
    }
    composable("settings/lyrics") {
        com.bt.bttune.ui.screens.settings.LyricsSettings(navController, scrollBehavior)
    }
    composable("settings/ai") {
        com.bt.bttune.ui.screens.settings.AiSettings(navController, scrollBehavior)
    }
    composable("settings/player") {
        PlayerSettings(navController, scrollBehavior)
    }
    composable("settings/scrobbler") {
        com.bt.bttune.ui.screens.settings.ScrobblerSettingsScreen(navController, scrollBehavior)
    }
    composable("settings/scrobbler/apps") {
        com.bt.bttune.ui.screens.settings.ScrobblerAppsScreen(navController)
    }
    composable("settings/storage") {
        StorageSettings(navController, scrollBehavior)
    }
    composable("settings/privacy") {
        PrivacySettings(navController, scrollBehavior)
    }
    composable("settings/backup_restore") {
        BackupAndRestore(navController, scrollBehavior)
    }
    composable("settings/discord") {
        DiscordSettings(navController, scrollBehavior)
    }
    composable("settings/experimental") {
            com.bt.bttune.ui.screens.settings.DebugSettings(navController)
        }
        composable("settings/voice_assistant") {
            com.bt.bttune.ui.screens.settings.VoiceAssistantSettings(navController, scrollBehavior)
        }
    composable("settings/discord/login") {
        DiscordLoginScreen(navController)
    }
    composable("settings/android_auto") {
        com.bt.bttune.ui.screens.settings.AndroidAutoSettings(
            navController,
            scrollBehavior
        )
    }
    composable("settings/about") {
        AboutScreen(navController, scrollBehavior)
    }
    composable("settings/app_info") {
        com.bt.bttune.ui.screens.settings.AppInfoScreen(navController, scrollBehavior)
    }
    composable("settings/developer_news") {
        com.bt.bttune.ui.screens.settings.DeveloperNewsScreen(navController, scrollBehavior)
    }
    composable("settings/home_sections") {
        com.bt.bttune.ui.screens.settings.MaterialHomeSectionsScreen(
            onBack = { navController.popBackStack() }
        )
    }
    composable("settings/gestures") {
        com.bt.bttune.ui.screens.settings.GesturesSettingsScreen(
            navController = navController,
            scrollBehavior = scrollBehavior
        )
    }
    composable("charts") {
        com.bt.bttune.ui.screens.charts.ChartsScreen(
            navController = navController,
            scrollBehavior = scrollBehavior
        )
    }
    composable("login") {
            LoginScreen(navController)
        }
        composable("youtube_login") {
            YouTubeLoginScreen(navController)
        }
    composable("contributor/{username}") { backStackEntry ->
        val username = backStackEntry.arguments?.getString("username") ?: return@composable
        ContributorProfileScreen(navController, username)
    }
    dialog(
        route = "always_on_display",
        dialogProperties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        AlwaysOnDisplayScreen(navController)
    }
}






