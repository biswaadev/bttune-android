package com.bt.bttune.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.bt.bttune.LocalPlayerAwareWindowInsets
import com.bt.bttune.LocalPlayerConnection
import com.bt.bttune.R
import com.bt.bttune.constants.HistorySource
import com.bt.bttune.constants.InnerTubeCookieKey
import com.bt.bttune.db.entities.EventWithSong
import com.bt.bttune.extensions.metadata
import com.bt.bttune.extensions.toMediaItem
import com.bt.bttune.extensions.togglePlayPause
import com.bt.bttune.innertube.utils.parseCookieString
import com.bt.bttune.models.toMediaMetadata
import com.bt.bttune.playback.queues.ListQueue
import com.bt.bttune.playback.queues.YouTubeQueue
import com.bt.bttune.ui.component.ChipsRow
import com.bt.bttune.ui.component.HideOnScrollFAB
import com.bt.bttune.ui.component.LocalMenuState
import com.bt.bttune.ui.component.NavigationTitle
import com.bt.bttune.ui.component.ScreenAdaptiveBackground
import com.bt.bttune.ui.component.SongListItem
import com.bt.bttune.ui.component.YouTubeListItem
import com.bt.bttune.ui.menu.SelectionMediaMetadataMenu
import com.bt.bttune.ui.menu.SongMenu
import com.bt.bttune.ui.menu.YouTubeSongMenu
import com.bt.bttune.ui.utils.backToMain
import com.bt.bttune.utils.rememberPreference
import com.bt.bttune.viewmodels.DateAgo
import com.bt.bttune.viewmodels.HistoryCategory
import com.bt.bttune.viewmodels.HistoryViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    var selection by remember { mutableStateOf(false) }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }

    var showClearTodayDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    val historySource by viewModel.historySource.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val events by viewModel.events.collectAsState()
    val historyPage by viewModel.historyPage

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (selection) {
        BackHandler {
            selection = false
        }
    }

    fun dateAgoToString(dateAgo: DateAgo): String {
        return when (dateAgo) {
            DateAgo.Today -> context.getString(R.string.today)
            DateAgo.Yesterday -> context.getString(R.string.yesterday)
            DateAgo.ThisWeek -> context.getString(R.string.this_week)
            DateAgo.LastWeek -> context.getString(R.string.last_week)
            is DateAgo.Other -> dateAgo.date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
        }
    }

    class WrappedHistoryItem(val item: EventWithSong) {
        var isSelected by mutableStateOf(false)
    }

    val filteredEvents = remember(events, query, selectedCategory) {
        val base = when (selectedCategory) {
            HistoryCategory.ALL, HistoryCategory.SONGS -> events
            HistoryCategory.ALBUMS -> events.mapValues { (_, songList) ->
                songList.filter { it.song.album != null }.distinctBy { it.song.album?.id }
            }.filterValues { it.isNotEmpty() }
            HistoryCategory.ARTISTS -> events.mapValues { (_, songList) ->
                songList.filter { it.song.artists.isNotEmpty() }.distinctBy { it.song.artists.firstOrNull()?.id }
            }.filterValues { it.isNotEmpty() }
        }

        if (query.text.isEmpty()) {
            base
        } else {
            base.mapValues { (_, songs) ->
                songs.filter { event ->
                    event.song.song.title.contains(query.text, ignoreCase = true) ||
                            event.song.artists.any { it.name.contains(query.text, ignoreCase = true) } ||
                            (event.song.album?.title?.contains(query.text, ignoreCase = true) == true)
                }
            }.filterValues { it.isNotEmpty() }
        }
    }

    val filteredRemoteContent = remember(historyPage, query) {
        if (query.text.isEmpty()) {
            historyPage?.sections
        } else {
            historyPage?.sections?.map { section ->
                section.copy(
                    songs = section.songs.filter { song ->
                        song.title.contains(query.text, ignoreCase = true) ||
                                song.artists.any { it.name.contains(query.text, ignoreCase = true) }
                    }
                )
            }?.filter { it.songs.isNotEmpty() }
        }
    }

    val wrappedItemsMap = remember(filteredEvents) {
        filteredEvents.mapValues { (_, events) ->
            events.map { WrappedHistoryItem(it) }.toMutableStateList()
        }
    }

    val allWrappedItems = remember(wrappedItemsMap) {
        wrappedItemsMap.values.flatten()
    }

    val lazyListState = rememberLazyListState()

    // Confirm Clear Today Dialog
    if (showClearTodayDialog) {
        AlertDialog(
            onDismissRequest = { showClearTodayDialog = false },
            title = { Text("Clear Today's History?", fontWeight = FontWeight.Bold) },
            text = { Text("This will remove listening events from today. Your total play statistics will remain preserved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearToday()
                        showClearTodayDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearTodayDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirm Clear All Dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All History?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to clear your entire listening history? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearAllDialog = false
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Adaptive background matching StatsScreen: blurred song thumbnail when playing, Library mesh when idle
        ScreenAdaptiveBackground(
            artworkUrl = mediaMetadata?.thumbnailUrl
        )

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                .asPaddingValues(),
            modifier = Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        ) {
            // Source Selector (Local vs Remote) when logged in
            if (isLoggedIn) {
                item(key = "history_source_chips") {
                    ChipsRow(
                        chips = listOf(
                            HistorySource.LOCAL to stringResource(R.string.local_history),
                            HistorySource.REMOTE to stringResource(R.string.remote_history),
                        ),
                        currentValue = historySource,
                        onValueUpdate = {
                            viewModel.historySource.value = it
                            if (it == HistorySource.REMOTE) {
                                viewModel.fetchRemoteHistory()
                            }
                        }
                    )
                }
            }

            // Category Filter Chips (All, Songs, Albums, Artists)
            if (historySource == HistorySource.LOCAL) {
                item(key = "history_category_chips") {
                    ChipsRow(
                        chips = listOf(
                            HistoryCategory.ALL to "All",
                            HistoryCategory.SONGS to "Songs",
                            HistoryCategory.ALBUMS to "Albums",
                            HistoryCategory.ARTISTS to "Artists",
                        ),
                        currentValue = selectedCategory,
                        onValueUpdate = { viewModel.setCategory(it) }
                    )
                }
            }

            // Remote Content Handling
            if (historySource == HistorySource.REMOTE && isLoggedIn) {
                filteredRemoteContent?.forEach { section ->
                    stickyHeader {
                        NavigationTitle(
                            title = section.title,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    items(
                        items = section.songs,
                        key = { "${section.title}_${it.id}_${section.songs.indexOf(it)}" }
                    ) { song ->
                        YouTubeListItem(
                            item = song,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        menuState.show {
                                            YouTubeSongMenu(
                                                song = song,
                                                navController = navController,
                                                onDismiss = menuState::dismiss
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_vert),
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (song.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                YouTubeQueue.radio(song.toMediaMetadata())
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        menuState.show {
                                            YouTubeSongMenu(
                                                song = song,
                                                navController = navController,
                                                onDismiss = menuState::dismiss
                                            )
                                        }
                                    }
                                )
                                .animateItem()
                        )
                    }
                }
            } else {
                // Local Grouped History
                filteredEvents.forEach { (dateAgo, _) ->
                    stickyHeader {
                        NavigationTitle(
                            title = dateAgoToString(dateAgo),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    val currentDateWrappedItems = wrappedItemsMap[dateAgo] ?: emptyList()

                    itemsIndexed(
                        items = currentDateWrappedItems,
                        key = { index, wrappedItem -> "${dateAgo}_${wrappedItem.item.event.id}_$index" }
                    ) { index, wrappedItem ->
                        val event = wrappedItem.item

                        SongListItem(
                            song = event.song,
                            albumIndex = null,
                            showLikedIcon = true,
                            showInLibraryIcon = false,
                            showDownloadIcon = true,
                            isSelected = wrappedItem.isSelected && selection,
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        menuState.show {
                                            SongMenu(
                                                originalSong = event.song,
                                                event = event.event,
                                                navController = navController,
                                                onDismiss = menuState::dismiss
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_vert),
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (!selection) {
                                            if (event.song.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = dateAgoToString(dateAgo),
                                                        items = currentDateWrappedItems.map { it.item.song.toMediaItem() },
                                                        startIndex = index
                                                    )
                                                )
                                            }
                                        } else {
                                            wrappedItem.isSelected = !wrappedItem.isSelected
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (!selection) {
                                            selection = true
                                            allWrappedItems.forEach { it.isSelected = false }
                                            wrappedItem.isSelected = true
                                        }
                                    }
                                )
                                .animateItem()
                        )
                    }
                }
            }
        }

        // Shuffle FAB
        HideOnScrollFAB(
            visible = if (historySource == HistorySource.REMOTE) {
                filteredRemoteContent?.any { it.songs.isNotEmpty() } == true
            } else {
                allWrappedItems.isNotEmpty()
            },
            lazyListState = lazyListState,
            icon = R.drawable.shuffle,
            onClick = {
                if (historySource == HistorySource.REMOTE && historyPage != null) {
                    val songs = filteredRemoteContent?.flatMap { it.songs } ?: emptyList()
                    if (songs.isNotEmpty()) {
                        playerConnection.playQueue(
                            ListQueue(
                                title = context.getString(R.string.history),
                                items = songs.map { it.toMediaItem() }.shuffled()
                            )
                        )
                    }
                } else {
                    playerConnection.playQueue(
                        ListQueue(
                            title = context.getString(R.string.history),
                            items = allWrappedItems.map { it.item.song.toMediaItem() }.shuffled()
                        )
                    )
                }
            }
        )

        // TopAppBar identical in styling to StatsScreen
        TopAppBar(
            title = {
                if (selection) {
                    val count = allWrappedItems.count { it.isSelected }
                    Text(
                        text = pluralStringResource(R.plurals.n_song, count, count),
                        style = MaterialTheme.typography.titleLarge
                    )
                } else if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                } else {
                    Text(stringResource(R.string.history))
                }
            },
            navigationIcon = {
                com.bt.bttune.ui.component.IconButton(
                    onClick = {
                        when {
                            isSearching -> {
                                isSearching = false
                                query = TextFieldValue()
                            }
                            selection -> {
                                selection = false
                            }
                            else -> {
                                navController.navigateUp()
                            }
                        }
                    },
                    onLongClick = {
                        if (!isSearching && !selection) {
                            navController.backToMain()
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            if (selection) R.drawable.close else R.drawable.arrow_back
                        ),
                        contentDescription = null
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            ),
            actions = {
                if (selection) {
                    val count = allWrappedItems.count { it.isSelected }
                    IconButton(
                        onClick = {
                            if (count == allWrappedItems.size) {
                                allWrappedItems.forEach { it.isSelected = false }
                            } else {
                                allWrappedItems.forEach { it.isSelected = true }
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(
                                if (count == allWrappedItems.size) R.drawable.deselect else R.drawable.select_all
                            ),
                            contentDescription = null
                        )
                    }
                    IconButton(
                        onClick = {
                            menuState.show {
                                SelectionMediaMetadataMenu(
                                    songSelection = allWrappedItems
                                        .filter { it.isSelected }
                                        .map { it.item.song.toMediaItem().metadata!! },
                                    onDismiss = menuState::dismiss,
                                    clearAction = { selection = false },
                                    currentItems = emptyList()
                                )
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null
                        )
                    }
                } else if (!isSearching) {
                    IconButton(
                        onClick = { isSearching = true }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = "Search"
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showTopMenu = true }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("View Stats") },
                                onClick = {
                                    showTopMenu = false
                                    navController.navigate("stats")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Today's History") },
                                onClick = {
                                    showTopMenu = false
                                    showClearTodayDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear All History", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showTopMenu = false
                                    showClearAllDialog = true
                                }
                            )
                        }
                    }
                }
            }
        )
    }
}
