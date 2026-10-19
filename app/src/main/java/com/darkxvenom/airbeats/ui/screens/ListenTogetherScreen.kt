package com.darkxvenom.airbeats.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.darkxvenom.airbeats.LocalPlayerAwareWindowInsets
import com.darkxvenom.airbeats.LocalPlayerConnection
import com.darkxvenom.airbeats.R
import com.darkxvenom.airbeats.ui.component.ScreenAdaptiveBackground
import com.darkxvenom.airbeats.ui.component.SettingsTopAppBar
import com.darkxvenom.airbeats.ui.component.TextFieldDialog
import com.darkxvenom.airbeats.utils.DiscoveredLanHost
import com.darkxvenom.airbeats.utils.LanTogetherClient
import com.darkxvenom.airbeats.utils.LanTogetherServer
import com.darkxvenom.airbeats.utils.ListenTogetherClient
import com.darkxvenom.airbeats.utils.ListenTogetherConnectionMode
import com.darkxvenom.airbeats.utils.ListenTogetherParticipant
import com.darkxvenom.airbeats.utils.ListenTogetherPlaybackState
import com.darkxvenom.airbeats.utils.ListenTogetherSession
import com.darkxvenom.airbeats.utils.ListenTogetherStore
import com.darkxvenom.airbeats.utils.ListenTogetherSync
import kotlinx.coroutines.launch

private object MusicTogetherSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 16.dp
    val md = 24.dp
    val lg = 32.dp
}

private fun isTechnicalNoise(msg: String?): Boolean {
    if (msg.isNullOrBlank()) return true
    val lower = msg.lowercase()
    return lower.contains("cancelled") ||
           lower.contains("timeout") ||
           lower.contains("timed out") ||
           lower.contains("left session") ||
           lower.contains("socket") ||
           lower.contains("broken pipe") ||
           lower.contains("reset by peer") ||
           lower.contains("eofexception") ||
           lower.contains("standalonecoroutine") ||
           lower.contains("jobcancellationexception") ||
           lower.contains("b2 was") ||
           lower.contains("connection refused") ||
           lower.contains("failed to connect") ||
           lower.contains("unable to resolve host") ||
           lower.contains("end of input")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val playerConnection = LocalPlayerConnection.current
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsState(initial = null)
        ?: remember { mutableStateOf(null) }
    val isPlaying by playerConnection?.isPlaying?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }
    val playbackState by playerConnection?.playbackState?.collectAsState(initial = Player.STATE_IDLE)
        ?: remember { mutableStateOf(Player.STATE_IDLE) }
    val currentPosition by playerConnection?.currentPosition?.collectAsState(initial = 0L)
        ?: remember { mutableLongStateOf(0L) }
    val shuffleModeEnabled by playerConnection?.shuffleModeEnabled?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }
    val repeatMode by playerConnection?.repeatMode?.collectAsState(initial = Player.REPEAT_MODE_OFF)
        ?: remember { mutableStateOf(Player.REPEAT_MODE_OFF) }
    val queueWindows by playerConnection?.queueWindows?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val scope = rememberCoroutineScope()

    var hostModeOnline by remember { mutableStateOf(true) }
    var joinModeOnline by remember { mutableStateOf(true) }
    var displayName by remember { mutableStateOf(ListenTogetherStore.defaultName()) }
    var joinCode by remember { mutableStateOf("") }
    var lanHostInput by remember { mutableStateOf("") }
    var lanPortInput by remember { mutableStateOf("8765") }
    var hostAllowGuestsToControlPlayback by remember { mutableStateOf(false) }
    var hostAllowGuestsToAddTracks by remember { mutableStateOf(true) }

    var session by remember { mutableStateOf<ListenTogetherSession?>(null) }
    var isHost by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isScanningLan by remember { mutableStateOf(false) }
    var discoveredHosts by remember { mutableStateOf<List<DiscoveredLanHost>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var expandedSection by rememberSaveable { mutableStateOf<String?>(null) }

    var showDisplayNameDialog by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var kickTarget by remember { mutableStateOf<ListenTogetherParticipant?>(null) }

    val syncedSession by ListenTogetherSync.session.collectAsState()
    val syncedIsHost by ListenTogetherSync.isHost.collectAsState()
    val currentConnectionMode by ListenTogetherSync.connectionMode.collectAsState()
    val syncMessage by ListenTogetherSync.message.collectAsState()

    var localIp by remember { mutableStateOf<String?>(null) }

    val sessionCreatedMessage = stringResource(R.string.session_created)
    val joinedSessionMessage = stringResource(R.string.joined_session)
    val leftSessionMessage = stringResource(R.string.left_session)

    LaunchedEffect(Unit) {
        localIp = LanTogetherServer.getLocalIpAddress(context)
        val savedSession = ListenTogetherStore.load(context) ?: return@LaunchedEffect
        displayName = savedSession.displayName
        isHost = savedSession.isHost
        if (savedSession.isLan) {
            hostModeOnline = false
            joinModeOnline = false
            lanHostInput = savedSession.hostAddress ?: savedSession.code
        } else {
            hostModeOnline = true
            joinModeOnline = true
            joinCode = savedSession.code
        }
    }

    LaunchedEffect(syncedSession, syncedIsHost) {
        if (syncedSession != null) {
            session = syncedSession
            isHost = syncedIsHost
            hostAllowGuestsToControlPlayback = syncedSession?.settings?.allowGuestsToControlPlayback ?: false
            hostAllowGuestsToAddTracks = syncedSession?.settings?.allowGuestsToAddTracks ?: true
        } else {
            session = null
            isHost = false
        }
    }

    LaunchedEffect(currentConnectionMode) {
        if (session != null) {
            val isOnline = currentConnectionMode == ListenTogetherConnectionMode.ONLINE
            hostModeOnline = isOnline
            joinModeOnline = isOnline
        }
    }

    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            if (!isTechnicalNoise(it)) {
                message = it
            }
        }
    }

    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            delay(3500)
            message = null
        }
    }

    // Dialogs
    if (showDisplayNameDialog) {
        TextFieldDialog(
            title = { Text(text = stringResource(R.string.together_display_name)) },
            initialTextFieldValue = TextFieldValue(displayName),
            placeholder = { Text(text = stringResource(R.string.together_display_name_placeholder)) },
            isInputValid = { it.trim().isNotBlank() },
            onDone = {
                displayName = it.trim()
                ListenTogetherSync.setDisplayName(displayName)
            },
            onDismiss = { showDisplayNameDialog = false },
        )
    }

    if (showPortDialog) {
        TextFieldDialog(
            title = { Text(text = stringResource(R.string.together_port)) },
            initialTextFieldValue = TextFieldValue(lanPortInput),
            placeholder = { Text(text = stringResource(R.string.together_port_placeholder)) },
            isInputValid = { it.trim().toIntOrNull() in 1..65535 },
            onDone = { lanPortInput = it.trim() },
            onDismiss = { showPortDialog = false },
        )
    }

    if (showJoinDialog) {
        TextFieldDialog(
            title = { Text(text = stringResource(R.string.join_session)) },
            initialTextFieldValue = TextFieldValue(if (joinModeOnline) joinCode else lanHostInput),
            placeholder = { Text(text = stringResource(if (joinModeOnline) R.string.together_join_hint else R.string.together_host_ip_hint)) },
            isInputValid = { it.trim().isNotBlank() },
            onDone = {
                val trimmed = it.trim()
                if (joinModeOnline) {
                    joinCode = trimmed.uppercase()
                } else {
                    lanHostInput = trimmed
                }
            },
            onDismiss = { showJoinDialog = false },
        )
    }

    kickTarget?.let { participant ->
        AlertDialog(
            onDismissRequest = { kickTarget = null },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(text = stringResource(R.string.together_kick)) },
            text = {
                Text(
                    text = stringResource(R.string.together_kick_confirm, participant.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        ListenTogetherSync.kickParticipant(participant.id)
                        kickTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(text = stringResource(R.string.together_kick))
                }
            },
            dismissButton = {
                TextButton(onClick = { kickTarget = null }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScreenAdaptiveBackground(
            artworkUrl = mediaMetadata?.thumbnailUrl ?: session?.state?.thumbnailUrl
        )

        val scrollState = rememberScrollState()

        LaunchedEffect(expandedSection) {
            if (expandedSection == "join") {
                delay(200)
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent,
            topBar = {
                SettingsTopAppBar(
                    title = stringResource(R.string.listen_together),
                    navController = navController,
                    scrollBehavior = scrollBehavior
                )
            }
        ) { paddingValues ->
            Column(
                verticalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
                    .verticalScroll(scrollState)
                    .padding(horizontal = MusicTogetherSpacing.sm, vertical = MusicTogetherSpacing.xs)
            ) {
                val activeSession = syncedSession ?: session
                val isLan = currentConnectionMode == ListenTogetherConnectionMode.LAN
                val controlsAllowed = isHost || syncedIsHost || (activeSession?.settings?.allowGuestsToControlPlayback == true)

                // 1. Status Card
                StatusCard(
                    session = activeSession,
                    isHost = isHost || syncedIsHost,
                    isLan = isLan,
                    errorMessage = message,
                    onCopy = { code ->
                        clipboardManager.setText(AnnotatedString(code))
                        message = context.getString(R.string.session_code_copied)
                    },
                    onShare = { code ->
                        val shareText = if (isLan) "http://$code" else code
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                },
                                null
                            )
                        )
                    },
                    onLeave = {
                        ListenTogetherSync.leaveSession()
                        session = null
                        isHost = false
                        message = null
                    }
                )

                // 2. Playback Card
                PlaybackCard(
                    songTitle = mediaMetadata?.title ?: activeSession?.state?.title,
                    subtitle = mediaMetadata?.artists?.joinToString { it.name } ?: activeSession?.state?.artists?.joinToString(),
                    thumbnailUrl = mediaMetadata?.thumbnailUrl ?: activeSession?.state?.thumbnailUrl,
                    isPlaying = isPlaying,
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    queueSize = queueWindows.size,
                    shuffleEnabled = shuffleModeEnabled,
                    repeatMode = repeatMode,
                    sessionActive = activeSession != null,
                    controlsEnabled = controlsAllowed,
                    onPrevious = {
                        if (isHost || syncedIsHost) {
                            playerConnection?.seekToPrevious()
                        } else {
                            if (controlsAllowed) {
                                ListenTogetherSync.sendGuestAction("skip_previous")
                            } else {
                                message = context.getString(R.string.together_controls_disabled_notice)
                            }
                        }
                    },
                    onPlayPause = {
                        if (isHost || syncedIsHost) {
                            playerConnection?.togglePlayPause()
                        } else {
                            if (controlsAllowed) {
                                ListenTogetherSync.sendGuestAction("toggle_play_pause")
                            } else {
                                message = context.getString(R.string.together_controls_disabled_notice)
                            }
                        }
                    },
                    onNext = {
                        if (isHost || syncedIsHost) {
                            playerConnection?.seekToNext()
                        } else {
                            if (controlsAllowed) {
                                ListenTogetherSync.sendGuestAction("skip_next")
                            } else {
                                message = context.getString(R.string.together_controls_disabled_notice)
                            }
                        }
                    }
                )

                // 3. Active Session Permissions Card
                activeSession?.let { currentRoomSession ->
                    RoomPermissionsCard(
                        session = currentRoomSession,
                        isHost = isHost || syncedIsHost,
                        onUpdateSettings = { allowPlayback, allowTracks ->
                            ListenTogetherSync.updateSettings(allowPlayback, allowTracks)
                        }
                    )
                }

                // 4. Host & Join Cards (when not in a session)
                if (activeSession == null) {
                    HostControlsCard(
                        onlineMode = hostModeOnline,
                        onModeChange = { hostModeOnline = it },
                        displayName = displayName,
                        onDisplayNameClick = { showDisplayNameDialog = true },
                        port = lanPortInput,
                        onPortClick = { showPortDialog = true },
                        allowGuestsToAddTracks = hostAllowGuestsToAddTracks,
                        onAllowGuestsToAddTracksChange = { hostAllowGuestsToAddTracks = it },
                        allowGuestsToControlPlayback = hostAllowGuestsToControlPlayback,
                        onAllowGuestsToControlPlaybackChange = { hostAllowGuestsToControlPlayback = it },
                        isLoading = isLoading,
                        canStart = mediaMetadata != null && !isLoading,
                        isExpanded = expandedSection == "host",
                        onToggleExpand = {
                            expandedSection = if (expandedSection == "host") null else "host"
                        },
                        onStart = {
                            val metadata = mediaMetadata ?: return@HostControlsCard
                            isLoading = true
                            scope.launch {
                                try {
                                    if (hostModeOnline) {
                                        val newSession = ListenTogetherClient.createSession(
                                            displayName = displayName,
                                            state = ListenTogetherPlaybackState(
                                                songId = metadata.id,
                                                title = metadata.title,
                                                artists = metadata.artists.map { it.name },
                                                thumbnailUrl = metadata.thumbnailUrl,
                                                positionMs = currentPosition,
                                                isPlaying = isPlaying,
                                                durationMs = playerConnection?.player?.duration?.takeIf { it > 0 } ?: (metadata.duration.toLong() * 1000L).coerceAtLeast(0L),
                                                settings = com.darkxvenom.airbeats.utils.ListenTogetherSettings(
                                                    allowGuestsToControlPlayback = hostAllowGuestsToControlPlayback,
                                                    allowGuestsToAddTracks = hostAllowGuestsToAddTracks,
                                                )
                                            )
                                        )
                                        session = newSession
                                        isHost = true
                                        ListenTogetherSync.adoptSession(
                                            context = context,
                                            session = newSession,
                                            displayName = displayName,
                                            isHost = true,
                                            isLan = false
                                        )
                                        ListenTogetherSync.updateSettings(
                                            hostAllowGuestsToControlPlayback,
                                            hostAllowGuestsToAddTracks
                                        )
                                        message = sessionCreatedMessage
                                    } else {
                                        val port = lanPortInput.toIntOrNull() ?: 8765
                                        ListenTogetherSync.setDisplayName(displayName)
                                        ListenTogetherSync.startLanHost(port)
                                        ListenTogetherSync.updateSettings(
                                            hostAllowGuestsToControlPlayback,
                                            hostAllowGuestsToAddTracks
                                        )
                                        message = "LAN host started on port $port"
                                    }
                                } catch (e: Exception) {
                                    val err = e.message
                                    if (!isTechnicalNoise(err)) {
                                        message = err
                                    }
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    )

                    JoinControlsCard(
                        onlineMode = joinModeOnline,
                        onModeChange = { joinModeOnline = it },
                        inputValue = if (joinModeOnline) joinCode else lanHostInput,
                        onInputClick = { showJoinDialog = true },
                        onDirectInputChange = {
                            if (joinModeOnline) {
                                joinCode = it.uppercase()
                            } else {
                                lanHostInput = it
                            }
                        },
                        isLoading = isLoading,
                        isScanningLan = isScanningLan,
                        discoveredHosts = discoveredHosts,
                        isExpanded = expandedSection == "join",
                        onToggleExpand = {
                            expandedSection = if (expandedSection == "join") null else "join"
                        },
                        onScanLan = {
                            isScanningLan = true
                            scope.launch {
                                try {
                                    val ip = localIp ?: LanTogetherServer.getLocalIpAddress(context)
                                    if (ip.isNullOrBlank()) {
                                        message = "Connect to Wi-Fi first"
                                        return@launch
                                    }
                                    message = context.getString(R.string.together_searching_lan)
                                    val found = LanTogetherClient.scanLocalNetwork(localIp = ip, port = 8765, context = context)
                                    discoveredHosts = found
                                    if (found.isNotEmpty()) {
                                        lanHostInput = found.first().address
                                        message = "Found ${found.size} host(s) on Wi-Fi"
                                    } else {
                                        message = context.getString(R.string.together_no_hosts_found)
                                    }
                                } catch (e: Exception) {
                                    val err = e.message
                                    if (!isTechnicalNoise(err)) {
                                        message = err
                                    }
                                } finally {
                                    isScanningLan = false
                                }
                            }
                        },
                        onSelectHost = { host ->
                            lanHostInput = host.address
                            isLoading = true
                            ListenTogetherSync.setDisplayName(displayName)
                            ListenTogetherSync.joinLanSession(host.address)
                            isLoading = false
                        },
                        onJoin = {
                            isLoading = true
                            scope.launch {
                                try {
                                    if (joinModeOnline) {
                                        val joined = ListenTogetherClient.joinSession(joinCode, displayName)
                                        session = joined
                                        isHost = false
                                        ListenTogetherSync.adoptSession(
                                            context = context,
                                            session = joined,
                                            displayName = displayName,
                                            isHost = false,
                                            isLan = false
                                        )
                                        message = joinedSessionMessage
                                    } else {
                                        ListenTogetherSync.setDisplayName(displayName)
                                        ListenTogetherSync.joinLanSession(lanHostInput)
                                    }
                                } catch (e: Exception) {
                                    val err = e.message
                                    if (!isTechnicalNoise(err)) {
                                        message = err
                                    }
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    )
                }

                // 5. LAN Web Player Button
                if (isLan && activeSession != null) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://${activeSession.code}"))
                                context.startActivity(browserIntent)
                            }.onFailure {
                                message = "Could not open browser: ${it.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.globe_search),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(MusicTogetherSpacing.xs))
                        Text("Open Web Player (http://${activeSession.code})")
                    }
                }

                // 6. Participants Card
                activeSession?.let { currentRoomSession ->
                    ParticipantsCard(
                        participants = currentRoomSession.participantList,
                        isHost = isHost || syncedIsHost,
                        onKick = { participant -> kickTarget = participant }
                    )
                }

                Spacer(modifier = Modifier.height(140.dp))
            }
        }
    }
}

@Composable
private fun StatusCard(
    session: ListenTogetherSession?,
    isHost: Boolean,
    isLan: Boolean,
    errorMessage: String?,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onLeave: () -> Unit,
) {
    val isActive = session != null
    val displayError = errorMessage?.takeIf { !isTechnicalNoise(it) }
    val accent = when {
        displayError != null && !isActive -> MaterialTheme.colorScheme.error
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(MusicTogetherSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
            ) {
                AccentIcon(iconResId = R.drawable.fire, accent = accent)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.together_status),
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                    )
                    Text(
                        text = when {
                            session != null && isHost -> stringResource(R.string.together_hosting)
                            session != null -> stringResource(R.string.together_connected)
                            else -> stringResource(R.string.together_idle)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (isActive) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(900),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50).copy(alpha = pulseAlpha)),
                    )
                }
                if (isActive) {
                    FilledTonalButton(
                        onClick = onLeave,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.leave),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(MusicTogetherSpacing.xs))
                        Text(text = stringResource(R.string.leave))
                    }
                }
            }

            if (displayError != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = displayError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(MusicTogetherSpacing.sm),
                    )
                }
            }

            if (session != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(
                        modifier = Modifier.padding(MusicTogetherSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.xs),
                    ) {
                        Text(
                            text = stringResource(if (isLan) R.string.together_host_ip else R.string.session_code),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = session.code,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.xs)) {
                            FilledTonalButton(
                                onClick = { onCopy(session.code) },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.copy),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(MusicTogetherSpacing.xs))
                                Text(text = stringResource(if (isLan) R.string.copy_link else R.string.copy_session_code))
                            }
                            TextButton(
                                onClick = { onShare(session.code) },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.share),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(MusicTogetherSpacing.xs))
                                Text(text = stringResource(R.string.share))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackCard(
    songTitle: String?,
    subtitle: String?,
    thumbnailUrl: String?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    queueSize: Int,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    sessionActive: Boolean,
    controlsEnabled: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(MusicTogetherSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
            ) {
                AccentIcon(iconResId = R.drawable.music_note, accent = MaterialTheme.colorScheme.tertiary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.together_current_playback),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = songTitle ?: stringResource(R.string.together_playback_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(54.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    if (!thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                painter = painterResource(R.drawable.music_note),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Info Pills Rows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.xs),
            ) {
                InfoPill(
                    text = when {
                        isBuffering -> stringResource(R.string.together_playback_buffering)
                        isPlaying -> stringResource(R.string.together_playback_playing)
                        else -> stringResource(R.string.together_playback_paused)
                    }
                )
                InfoPill(text = stringResource(R.string.together_queue_count, queueSize))
                InfoPill(
                    text = if (shuffleEnabled) {
                        stringResource(R.string.together_shuffle_on)
                    } else {
                        stringResource(R.string.together_shuffle_off)
                    }
                )
                val repeatModeText = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> "One"
                    Player.REPEAT_MODE_ALL -> "All"
                    else -> "Off"
                }
                InfoPill(text = stringResource(R.string.together_repeat_mode, repeatModeText))
            }

            // Interactive Playback Controls
            if (sessionActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPrevious,
                        enabled = controlsEnabled,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_previous),
                            contentDescription = "Previous Track",
                            tint = if (controlsEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Surface(
                        onClick = onPlayPause,
                        enabled = controlsEnabled,
                        shape = CircleShape,
                        color = if (controlsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = if (controlsEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    IconButton(
                        onClick = onNext,
                        enabled = controlsEnabled,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_next),
                            contentDescription = "Next Track",
                            tint = if (controlsEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HostControlsCard(
    onlineMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    displayName: String,
    onDisplayNameClick: () -> Unit,
    port: String,
    onPortClick: () -> Unit,
    allowGuestsToAddTracks: Boolean,
    onAllowGuestsToAddTracksChange: (Boolean) -> Unit,
    allowGuestsToControlPlayback: Boolean,
    onAllowGuestsToControlPlaybackChange: (Boolean) -> Unit,
    isLoading: Boolean,
    canStart: Boolean,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onStart: () -> Unit,
) {
    SectionCard(
        iconResId = R.drawable.fire,
        titleResId = R.string.together_host_section,
        subtitle = displayName,
        accent = MaterialTheme.colorScheme.primary,
        isCollapsible = true,
        isExpanded = isExpanded,
        onHeaderClick = onToggleExpand,
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !onlineMode,
                onClick = { onModeChange(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_wifi),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
            ) {
                Text(text = stringResource(R.string.together_lan))
            }
            SegmentedButton(
                selected = onlineMode,
                onClick = { onModeChange(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.globe_search),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
            ) {
                Text(text = stringResource(R.string.together_online))
            }
        }

        SettingsRow(
            iconResId = R.drawable.person,
            titleResId = R.string.together_display_name,
            subtitle = displayName,
            onClick = onDisplayNameClick,
        )

        if (!onlineMode) {
            SettingsRow(
                iconResId = R.drawable.link,
                titleResId = R.string.together_port,
                subtitle = port,
                onClick = onPortClick,
            )
        }

        ToggleRow(
            iconResId = R.drawable.playlist_add,
            titleResId = R.string.together_allow_guest_tracks,
            checked = allowGuestsToAddTracks,
            onCheckedChange = onAllowGuestsToAddTracksChange,
        )

        ToggleRow(
            iconResId = R.drawable.play,
            titleResId = R.string.together_allow_guest_playback,
            checked = allowGuestsToControlPlayback,
            onCheckedChange = onAllowGuestsToControlPlaybackChange,
        )

        Button(
            enabled = canStart,
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(MusicTogetherSpacing.xs))
                Text(text = stringResource(R.string.connecting))
            } else {
                Icon(
                    painter = painterResource(R.drawable.fire),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(MusicTogetherSpacing.xs))
                Text(text = stringResource(R.string.start_session))
            }
        }
    }
}

@Composable
private fun JoinControlsCard(
    onlineMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    inputValue: String,
    onInputClick: () -> Unit,
    onDirectInputChange: (String) -> Unit,
    isLoading: Boolean,
    isScanningLan: Boolean,
    discoveredHosts: List<DiscoveredLanHost>,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onScanLan: () -> Unit,
    onSelectHost: (DiscoveredLanHost) -> Unit,
    onJoin: () -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    SectionCard(
        iconResId = R.drawable.multi_user,
        titleResId = R.string.together_join_section,
        subtitleResId = R.string.join_session,
        accent = MaterialTheme.colorScheme.tertiary,
        isCollapsible = true,
        isExpanded = isExpanded,
        onHeaderClick = onToggleExpand,
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !onlineMode,
                onClick = { onModeChange(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_wifi),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
            ) {
                Text(text = stringResource(R.string.together_lan))
            }
            SegmentedButton(
                selected = onlineMode,
                onClick = { onModeChange(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.globe_search),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
            ) {
                Text(text = stringResource(R.string.together_online))
            }
        }

        OutlinedTextField(
            value = inputValue,
            onValueChange = onDirectInputChange,
            label = { Text(stringResource(if (onlineMode) R.string.session_code else R.string.together_host_ip)) },
            placeholder = { Text(stringResource(if (onlineMode) R.string.together_join_hint else R.string.together_host_ip_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusEvent { focusState ->
                    if (focusState.isFocused) {
                        scope.launch {
                            delay(250)
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                }
        )

        if (!onlineMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.xs)) {
                OutlinedButton(
                    enabled = !isScanningLan,
                    onClick = onScanLan,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isScanningLan) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_wifi),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(MusicTogetherSpacing.xs))
                        Text(stringResource(R.string.together_scan_lan))
                    }
                }
            }

            AnimatedVisibility(visible = discoveredHosts.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "AVAILABLE HOSTS (${discoveredHosts.size})",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    discoveredHosts.forEach { host ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_wifi),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = host.hostName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${host.address} • ${host.participants} in room",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Button(
                                    onClick = { onSelectHost(host) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.join))
                                }
                            }
                        }
                    }
                }
            }
        }

        FilledTonalButton(
            enabled = !isLoading && inputValue.isNotBlank(),
            onClick = onJoin,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(MusicTogetherSpacing.xs))
                Text(text = stringResource(R.string.connecting))
            } else {
                Icon(
                    painter = painterResource(R.drawable.join),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(MusicTogetherSpacing.xs))
                Text(text = stringResource(R.string.join_session))
            }
        }
    }
}

@Composable
private fun RoomPermissionsCard(
    session: ListenTogetherSession,
    isHost: Boolean,
    onUpdateSettings: (allowPlayback: Boolean, allowTracks: Boolean) -> Unit,
) {
    SectionCard(
        iconResId = R.drawable.lock,
        titleResId = R.string.together_room_permissions,
        subtitle = if (isHost) "Host Controls" else "Host Permissions",
        accent = MaterialTheme.colorScheme.secondary,
    ) {
        if (isHost) {
            ToggleRow(
                iconResId = R.drawable.play,
                titleResId = R.string.together_allow_guest_playback,
                checked = session.settings.allowGuestsToControlPlayback,
                onCheckedChange = { checked ->
                    onUpdateSettings(checked, session.settings.allowGuestsToAddTracks)
                }
            )

            ToggleRow(
                iconResId = R.drawable.playlist_add,
                titleResId = R.string.together_allow_guest_tracks,
                checked = session.settings.allowGuestsToAddTracks,
                onCheckedChange = { checked ->
                    onUpdateSettings(session.settings.allowGuestsToControlPlayback, checked)
                }
            )
        } else {
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.together_allow_guest_playback),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                leadingContent = {
                    AccentIcon(iconResId = R.drawable.play, accent = MaterialTheme.colorScheme.secondary, size = 40.dp)
                },
                trailingContent = {
                    Surface(
                        color = if (session.settings.allowGuestsToControlPlayback)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (session.settings.allowGuestsToControlPlayback)
                                stringResource(R.string.together_permission_allowed)
                            else
                                stringResource(R.string.together_permission_host_only),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (session.settings.allowGuestsToControlPlayback)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.together_allow_guest_tracks),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                leadingContent = {
                    AccentIcon(iconResId = R.drawable.playlist_add, accent = MaterialTheme.colorScheme.secondary, size = 40.dp)
                },
                trailingContent = {
                    Surface(
                        color = if (session.settings.allowGuestsToAddTracks)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (session.settings.allowGuestsToAddTracks)
                                stringResource(R.string.together_permission_allowed)
                            else
                                stringResource(R.string.together_permission_host_only),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (session.settings.allowGuestsToAddTracks)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

@Composable
private fun ParticipantsCard(
    participants: List<ListenTogetherParticipant>,
    isHost: Boolean,
    onKick: (ListenTogetherParticipant) -> Unit,
) {
    SectionCard(
        iconResId = R.drawable.multi_user,
        titleResId = R.string.together_participants,
        subtitle = stringResource(R.string.together_connected_count, participants.size),
        accent = MaterialTheme.colorScheme.secondary,
    ) {
        if (participants.isEmpty()) {
            EmptyPanel(
                iconResId = R.drawable.person,
                titleResId = R.string.together_participants_empty,
                bodyResId = R.string.together_participants_empty_body,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                participants.forEachIndexed { index, participant ->
                    ParticipantRow(
                        participant = participant,
                        isHost = isHost,
                        onKick = { onKick(participant) }
                    )
                    if (index < participants.size - 1) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticipantRow(
    participant: ListenTogetherParticipant,
    isHost: Boolean,
    onKick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = participant.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(if (participant.isHost) R.string.together_role_host else R.string.together_role_guest),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            AccentIcon(
                iconResId = if (participant.isHost) R.drawable.fire else R.drawable.person,
                accent = if (participant.isHost) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                size = 40.dp,
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.xxs)
            ) {
                if (participant.isHost) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "HOST",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (isHost) {
                    IconButton(onClick = onKick) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.together_kick),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun SectionCard(
    @DrawableRes iconResId: Int,
    @StringRes titleResId: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    @StringRes subtitleResId: Int? = null,
    subtitle: String? = null,
    isCollapsible: Boolean = false,
    isExpanded: Boolean = true,
    onHeaderClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(MusicTogetherSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(if (isExpanded) MusicTogetherSpacing.sm else 0.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isCollapsible && onHeaderClick != null) {
                            Modifier
                                .clip(MaterialTheme.shapes.large)
                                .clickable(onClick = onHeaderClick)
                        } else Modifier
                    )
            ) {
                AccentIcon(iconResId = iconResId, accent = accent)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(titleResId),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val subtitleText = when {
                        subtitle != null -> subtitle
                        subtitleResId != null -> stringResource(subtitleResId)
                        else -> null
                    }
                    if (subtitleText != null) {
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (isCollapsible) {
                    IconButton(onClick = { onHeaderClick?.invoke() }) {
                        Icon(
                            painter = painterResource(
                                if (isExpanded) R.drawable.expand_less else R.drawable.expand_more
                            ),
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (!isCollapsible || isExpanded) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRow(
    @DrawableRes iconResId: Int,
    @StringRes titleResId: Int,
    subtitle: String,
    subtitleMaxLines: Int = 1,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(titleResId),
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                maxLines = subtitleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            AccentIcon(iconResId = iconResId, accent = MaterialTheme.colorScheme.primary, size = 40.dp)
        },
        trailingContent = {
            if (onClick != null) {
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ToggleRow(
    @DrawableRes iconResId: Int,
    @StringRes titleResId: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(titleResId),
                fontWeight = FontWeight.SemiBold,
            )
        },
        leadingContent = {
            AccentIcon(iconResId = iconResId, accent = MaterialTheme.colorScheme.primary, size = 40.dp)
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun AccentIcon(
    @DrawableRes iconResId: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

@Composable
private fun InfoPill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MusicTogetherSpacing.sm, vertical = MusicTogetherSpacing.xs),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyPanel(
    @DrawableRes iconResId: Int,
    @StringRes titleResId: Int,
    @StringRes bodyResId: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(MusicTogetherSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(MusicTogetherSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccentIcon(iconResId = iconResId, accent = MaterialTheme.colorScheme.onSurfaceVariant, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleResId),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(bodyResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
