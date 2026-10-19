package com.darkxvenom.airbeats.utils

import android.content.Context
import com.darkxvenom.airbeats.extensions.toMediaItem
import com.darkxvenom.airbeats.innertube.models.WatchEndpoint
import com.darkxvenom.airbeats.playback.PlayerConnection
import com.darkxvenom.airbeats.playback.queues.YouTubeQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

enum class ListenTogetherConnectionMode {
    ONLINE,
    LAN
}

object ListenTogetherSync {
    private const val SYNC_MS = 280L
    private const val CONTROLLER_PUBLISH_MS = 600L
    private const val SEEK_TOLERANCE_MS = 1000L
    private const val ONLINE_SEEK_TOLERANCE_MS = 1800L
    private const val APPLY_GRACE_MS = 2500L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var syncJob: Job? = null
    private var appContext: Context? = null
    private var playerConnection: PlayerConnection? = null
    private var lastAppliedVersion = 0L
    private var lastControllerPublishAt = 0L
    private var suppressLocalSyncUntil = 0L
    private var lastHandledActionId: String? = null
    private var loadingSongId: String? = null

    private val _session = MutableStateFlow<ListenTogetherSession?>(null)
    val session = _session.asStateFlow()

    private val _isHost = MutableStateFlow(false)
    val isHost = _isHost.asStateFlow()

    private val _connectionMode = MutableStateFlow(ListenTogetherConnectionMode.ONLINE)
    val connectionMode = _connectionMode.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _displayName = MutableStateFlow(ListenTogetherStore.defaultName())
    val displayName = _displayName.asStateFlow()

    private var lanServer: LanTogetherServer? = null
    var lanHostAddress: String? = null
        private set
    val lanHost: String? get() = lanHostAddress

    fun start(
        context: Context,
        connection: PlayerConnection,
    ) {
        appContext = context.applicationContext
        playerConnection = connection
        val saved = ListenTogetherStore.load(context.applicationContext)
        if (saved != null && _session.value == null) {
            _displayName.value = saved.displayName
            _isHost.value = saved.isHost
            _connectionMode.value = if (saved.isLan) ListenTogetherConnectionMode.LAN else ListenTogetherConnectionMode.ONLINE
            lanHostAddress = saved.hostAddress
            scope.launch {
                runCatching {
                    if (saved.isLan) {
                        if (saved.isHost) {
                            val localIp = LanTogetherServer.getLocalIpAddress(context) ?: "127.0.0.1"
                            val port = saved.code.substringAfterLast(":").toIntOrNull() ?: 8765
                            startLanHost(port)
                            return@launch
                        } else {
                            val host = saved.hostAddress ?: saved.code
                            LanTogetherClient.getSession(host, saved.participantId)
                        }
                    } else {
                        ListenTogetherClient.getSession(saved.code).copy(participantId = saved.participantId)
                    }
                }.onSuccess {
                    _session.value = it
                    lastAppliedVersion = it.stateVersion
                    restartSync()
                }.onFailure {
                    ListenTogetherStore.clear(context.applicationContext)
                    _message.value = it.message
                }
            }
        } else if (_session.value != null) {
            restartSync()
        }
    }

    fun setDisplayName(name: String) {
        _displayName.value = name.ifBlank { ListenTogetherStore.defaultName() }
    }

    fun setConnectionMode(mode: ListenTogetherConnectionMode) {
        _connectionMode.value = mode
    }

    fun adoptSession(
        context: Context,
        session: ListenTogetherSession,
        displayName: String,
        isHost: Boolean,
        isLan: Boolean = false,
        hostAddress: String? = null,
    ) {
        _displayName.value = displayName.ifBlank { ListenTogetherStore.defaultName() }
        _session.value = session
        _isHost.value = isHost
        _connectionMode.value = if (isLan) ListenTogetherConnectionMode.LAN else ListenTogetherConnectionMode.ONLINE
        lanHostAddress = hostAddress
        appContext = context.applicationContext
        lastAppliedVersion = session.stateVersion
        loadingSongId = null
        ListenTogetherStore.save(
            context = context.applicationContext,
            session = session,
            displayName = _displayName.value,
            isHost = isHost,
            isLan = isLan,
            hostAddress = hostAddress
        )
        restartSync()
    }

    fun createSession() {
        val context = appContext ?: return
        val connection = playerConnection ?: return
        val metadata = connection.mediaMetadata.value ?: run {
            _message.value = "Play a song first"
            return
        }

        scope.launch {
            runCatching {
                ListenTogetherClient.createSession(
                    displayName = _displayName.value,
                    state =
                        ListenTogetherPlaybackState(
                            songId = metadata.id,
                            title = metadata.title,
                            artists = metadata.artists.map { it.name },
                            thumbnailUrl = metadata.thumbnailUrl,
                            positionMs = connection.player.currentPosition,
                            isPlaying = connection.player.playWhenReady,
                            durationMs = connection.player.duration.takeIf { it > 0 } ?: (metadata.duration.toLong() * 1000L).coerceAtLeast(0L),
                        )
                )
            }.onSuccess {
                _session.value = it
                _isHost.value = true
                _connectionMode.value = ListenTogetherConnectionMode.ONLINE
                lastAppliedVersion = it.stateVersion
                ListenTogetherStore.save(context, it, _displayName.value, true, isLan = false)
                _message.value = "Session created"
                restartSync()
            }.onFailure {
                _message.value = it.message
            }
        }
    }

    fun joinSession(code: String) {
        val context = appContext ?: return
        scope.launch {
            runCatching {
                ListenTogetherClient.joinSession(code, _displayName.value)
            }.onSuccess {
                _session.value = it
                _isHost.value = false
                _connectionMode.value = ListenTogetherConnectionMode.ONLINE
                lastAppliedVersion = 0L
                ListenTogetherStore.save(context, it, _displayName.value, false, isLan = false)
                _message.value = "Joined session"
                restartSync()
            }.onFailure {
                _message.value = it.message
            }
        }
    }

    fun startLanHost(port: Int = 8765) {
        val context = appContext ?: return
        val connection = playerConnection ?: return
        val metadata = connection.mediaMetadata.value ?: run {
            _message.value = "Play a song first"
            return
        }

        val localIp = LanTogetherServer.getLocalIpAddress(context)
        if (localIp.isNullOrBlank()) {
            _message.value = "Connect to Wi-Fi or enable Hotspot first"
            return
        }

        scope.launch {
            try {
                lanServer?.stop()
                val server = LanTogetherServer(
                    context = context,
                    hostIp = localIp,
                    port = port,
                    hostDisplayName = _displayName.value,
                    audioStreamResolver = { songId ->
                        playerConnection?.service?.resolveAudioStreamForCast(songId)
                    }
                )
                server.playbackState = ListenTogetherPlaybackState(
                    songId = metadata.id,
                    title = metadata.title,
                    artists = metadata.artists.map { it.name },
                    thumbnailUrl = metadata.thumbnailUrl,
                    positionMs = connection.player.currentPosition,
                    isPlaying = connection.player.playWhenReady,
                    durationMs = connection.player.duration.takeIf { it > 0 } ?: (metadata.duration.toLong() * 1000L).coerceAtLeast(0L),
                )
                server.onPlaybackCommand = { action, positionMs, extraData ->
                    scope.launch(Dispatchers.Main) {
                        val player = connection.player
                        when (action) {
                            "play", "resume" -> {
                                if (!player.playWhenReady) {
                                    player.play()
                                }
                            }
                            "pause" -> {
                                if (player.playWhenReady) {
                                    player.pause()
                                }
                            }
                            "toggle_play_pause" -> {
                                connection.togglePlayPause()
                            }
                            "seek" -> {
                                if (positionMs != null && positionMs >= 0) {
                                    player.seekTo(positionMs)
                                }
                            }
                            "next", "skip_next" -> {
                                connection.seekToNext()
                            }
                            "previous", "skip_previous" -> {
                                connection.seekToPrevious()
                            }
                            "add_track" -> {
                                extraData?.let { json ->
                                    val track = ListenTogetherPlaybackState.fromJson(json)
                                    connection.addToQueue(track.toMediaMetadata().toMediaItem())
                                }
                            }
                            "play_next" -> {
                                extraData?.let { json ->
                                    val track = ListenTogetherPlaybackState.fromJson(json)
                                    connection.playNext(track.toMediaMetadata().toMediaItem())
                                }
                            }
                        }
                    }
                }
                server.start(30000, true)
                lanServer = server
                lanHostAddress = "$localIp:$port"
                _connectionMode.value = ListenTogetherConnectionMode.LAN

                val initialSession = server.buildSessionSnapshot("host")
                _session.value = initialSession
                _isHost.value = true
                lastAppliedVersion = initialSession.stateVersion

                ListenTogetherStore.save(
                    context = context,
                    session = initialSession,
                    displayName = _displayName.value,
                    isHost = true,
                    isLan = true,
                    hostAddress = "$localIp:$port"
                )
                _message.value = "LAN host started on $localIp:$port"
                restartSync()
            } catch (e: Exception) {
                _message.value = "Failed to start LAN host: ${e.message}"
            }
        }
    }

    fun joinLanSession(hostAddress: String) {
        val context = appContext ?: return
        scope.launch {
            runCatching {
                val cleanAddress = LanTogetherClient.normalizeHostAddress(hostAddress)
                val newSession = LanTogetherClient.joinSession(cleanAddress, _displayName.value)
                cleanAddress to newSession
            }.onSuccess { (cleanAddress, newSession) ->
                lanHostAddress = cleanAddress
                _connectionMode.value = ListenTogetherConnectionMode.LAN
                _session.value = newSession
                _isHost.value = false
                lastAppliedVersion = 0L
                ListenTogetherStore.save(
                    context = context,
                    session = newSession,
                    displayName = _displayName.value,
                    isHost = false,
                    isLan = true,
                    hostAddress = cleanAddress
                )
                _message.value = "Joined LAN session"
                restartSync()
            }.onFailure {
                _message.value = it.message ?: "Failed to connect to LAN host"
            }
        }
    }

    fun leaveSession() {
        val context = appContext
        val activeSession = _session.value
        val isLan = _connectionMode.value == ListenTogetherConnectionMode.LAN
        val hostAddr = lanHostAddress
        scope.launch {
            if (activeSession != null) {
                if (isLan) {
                    if (_isHost.value) {
                        runCatching { lanServer?.stop() }
                        lanServer = null
                    } else if (!hostAddr.isNullOrBlank()) {
                        runCatching { LanTogetherClient.leaveSession(hostAddr, activeSession.participantId) }
                    }
                } else {
                    runCatching {
                        ListenTogetherClient.leaveSession(activeSession.code, activeSession.participantId)
                    }
                }
            }
            lanServer?.stop()
            lanServer = null
            lanHostAddress = null
            loadingSongId = null
            playerConnection?.player?.let { player ->
                if (player.playbackParameters.speed != 1.0f) {
                    player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
                }
            }
            if (context != null) ListenTogetherStore.clear(context)
            _session.value = null
            _isHost.value = false
            _message.value = "Left session"
            syncJob?.cancel()
        }
    }

    private fun restartSync() {
        syncJob?.cancel()
        val connection = playerConnection ?: return
        syncJob =
            scope.launch {
                while (true) {
                    val activeSession = _session.value ?: break
                    syncOnce(connection, activeSession)
                    delay(SYNC_MS)
                }
            }
    }

    private suspend fun syncOnce(
        connection: PlayerConnection,
        session: ListenTogetherSession,
    ) {
        if (_connectionMode.value == ListenTogetherConnectionMode.LAN) {
            syncOnceLan(connection, session)
        } else {
            syncOnceOnline(connection, session)
        }
    }

    private fun syncOnceLan(
        connection: PlayerConnection,
        session: ListenTogetherSession,
    ) {
        val server = lanServer
        if (_isHost.value && server != null) {
            val metadata = connection.mediaMetadata.value
            if (metadata != null) {
                val current = server.playbackState
                val isPlaying = connection.player.playWhenReady
                val pos = connection.player.currentPosition
                val timeSinceAction = System.currentTimeMillis() - server.lastActionTime
                val isActionPending = timeSinceAction < 2000L && current?.isPlaying != isPlaying

                val isTrackChange = current == null || current.songId != metadata.id
                val isPlayStateChange = current != null && current.isPlaying != isPlaying
                val expectedPos = if (current != null && current.isPlaying) {
                    current.positionMs + (System.currentTimeMillis() - current.updatedAt)
                } else {
                    current?.positionMs ?: 0L
                }
                val isHardSeek = current != null && kotlin.math.abs(pos - expectedPos) > 3000L

                val needsUpdate = !isActionPending && (isTrackChange || isPlayStateChange || isHardSeek ||
                    kotlin.math.abs(pos - (current?.positionMs ?: 0L)) > 1000L)

                if (needsUpdate) {
                    server.playbackState = ListenTogetherPlaybackState(
                        songId = metadata.id,
                        title = metadata.title,
                        artists = metadata.artists.map { it.name },
                        thumbnailUrl = metadata.thumbnailUrl,
                        positionMs = pos,
                        isPlaying = isPlaying,
                        durationMs = connection.player.duration.takeIf { it > 0 } ?: (metadata.duration.toLong() * 1000L).coerceAtLeast(0L),
                        updatedAt = System.currentTimeMillis(),
                        settings = server.settings,
                    )
                    if (isTrackChange || isPlayStateChange || isHardSeek) {
                        server.stateVersion++
                    }
                }
            }
            _session.value = server.buildSessionSnapshot("host")
            return
        }

        val host = lanHostAddress ?: return
        scope.launch {
            runCatching {
                LanTogetherClient.getSession(host, session.participantId)
            }.onSuccess { remoteSession ->
                val keptSession = remoteSession.copy(participantId = session.participantId)
                _session.value = keptSession
                val remoteState = remoteSession.state ?: return@onSuccess
                val now = System.currentTimeMillis()

                if (remoteSession.stateVersion > lastAppliedVersion) {
                    applyRemoteState(connection, remoteSession)
                    lastAppliedVersion = remoteSession.stateVersion
                    return@onSuccess
                }

                if (now > suppressLocalSyncUntil) {
                    softCorrectPosition(connection, remoteSession)
                }
            }.onFailure {
                _message.value = it.message
            }
        }
    }

    private suspend fun syncOnceOnline(
        connection: PlayerConnection,
        session: ListenTogetherSession,
    ) {
        runCatching {
            ListenTogetherClient.getSession(session.code)
        }.onSuccess { remoteSession ->
            val keptSession = remoteSession.copy(participantId = session.participantId)
            _session.value = keptSession
            val remoteState = remoteSession.state ?: return@onSuccess
            val now = System.currentTimeMillis()

            if (_isHost.value) {
                val cmd = remoteState.actionCommand
                val actionId = remoteState.actionId
                if (!cmd.isNullOrBlank() && actionId != null && actionId != lastHandledActionId) {
                    lastHandledActionId = actionId
                    val allowed = if (cmd in listOf("play", "pause", "toggle_play_pause", "seek", "next", "skip_next", "previous", "skip_previous")) {
                        remoteSession.settings.allowGuestsToControlPlayback
                    } else if (cmd in listOf("add_track", "play_next")) {
                        remoteSession.settings.allowGuestsToAddTracks
                    } else false

                    if (allowed) {
                        when (cmd) {
                            "play" -> connection.player.play()
                            "pause" -> connection.player.pause()
                            "toggle_play_pause" -> connection.togglePlayPause()
                            "seek" -> if (remoteState.positionMs >= 0) connection.player.seekTo(remoteState.positionMs)
                            "next", "skip_next" -> connection.seekToNext()
                            "previous", "skip_previous" -> connection.seekToPrevious()
                            "add_track" -> {
                                remoteState.actionPayload?.let { payload ->
                                    runCatching {
                                        val json = JSONObject(payload)
                                        val track = ListenTogetherPlaybackState.fromJson(json)
                                        connection.addToQueue(track.toMediaMetadata().toMediaItem())
                                    }
                                }
                            }
                            "play_next" -> {
                                remoteState.actionPayload?.let { payload ->
                                    runCatching {
                                        val json = JSONObject(payload)
                                        val track = ListenTogetherPlaybackState.fromJson(json)
                                        connection.playNext(track.toMediaMetadata().toMediaItem())
                                    }
                                }
                            }
                        }
                    }
                }

                if (now - lastControllerPublishAt >= CONTROLLER_PUBLISH_MS) {
                    publishLocalStateOnline(connection, keptSession)
                    lastControllerPublishAt = now
                }
                return@onSuccess
            }

            if (remoteSession.stateVersion > lastAppliedVersion) {
                applyRemoteState(connection, remoteSession)
                lastAppliedVersion = remoteSession.stateVersion
                return@onSuccess
            }

            if (now > suppressLocalSyncUntil) {
                softCorrectPosition(connection, remoteSession)
            }
        }.onFailure {
            _message.value = it.message
        }
    }

    private suspend fun publishLocalStateOnline(
        connection: PlayerConnection,
        session: ListenTogetherSession,
    ) {
        val metadata = connection.mediaMetadata.value ?: return
        runCatching {
            ListenTogetherClient.updateState(
                code = session.code,
                participantId = session.participantId,
                state =
                    ListenTogetherPlaybackState(
                        songId = metadata.id,
                        title = metadata.title,
                        artists = metadata.artists.map { it.name },
                        thumbnailUrl = metadata.thumbnailUrl,
                        positionMs = connection.player.currentPosition,
                        isPlaying = connection.player.playWhenReady,
                        durationMs = connection.player.duration.takeIf { it > 0 } ?: (metadata.duration.toLong() * 1000L).coerceAtLeast(0L),
                        settings = session.settings,
                    )
            )
        }.onSuccess {
            _session.value = it.copy(participantId = session.participantId)
            lastAppliedVersion = it.stateVersion
            lastControllerPublishAt = System.currentTimeMillis()
        }.onFailure {
            _message.value = it.message
        }
    }

    private suspend fun applyRemoteState(
        connection: PlayerConnection,
        remoteSession: ListenTogetherSession,
    ) {
        val remoteState = remoteSession.state ?: return
        suppressLocalSyncUntil = System.currentTimeMillis() + APPLY_GRACE_MS

        val currentSongId = connection.player.currentMediaItem?.mediaId
        if (remoteState.songId.isNotBlank()) {
            if (currentSongId != remoteState.songId) {
                if (loadingSongId != remoteState.songId) {
                    loadingSongId = remoteState.songId
                    val metadata = remoteState.toMediaMetadata()
                    connection.playQueue(YouTubeQueue(WatchEndpoint(videoId = remoteState.songId), metadata))
                }
                return
            } else {
                loadingSongId = null
            }
        }

        if (connection.player.playbackState == androidx.media3.common.Player.STATE_READY) {
            val target = expectedPosition(remoteSession)
            val isLan = _connectionMode.value == ListenTogetherConnectionMode.LAN
            val hardSeekThreshold = if (isLan) 3500L else 5000L
            val diff = connection.player.currentPosition - target
            val absDiff = kotlin.math.abs(diff)

            if (absDiff > hardSeekThreshold) {
                connection.player.seekTo(target)
                if (connection.player.playbackParameters.speed != 1.0f) {
                    connection.player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
                }
            } else if (absDiff > (if (isLan) 800L else 1200L)) {
                val speed = if (diff < 0) 1.04f else 0.96f
                if (connection.player.playbackParameters.speed != speed) {
                    connection.player.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
                }
            } else {
                if (connection.player.playbackParameters.speed != 1.0f) {
                    connection.player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
                }
            }
        }

        if (remoteState.isPlaying && !connection.player.playWhenReady) {
            connection.player.play()
        } else if (!remoteState.isPlaying && connection.player.playWhenReady) {
            connection.player.pause()
        }
    }

    private fun softCorrectPosition(
        connection: PlayerConnection,
        remoteSession: ListenTogetherSession,
    ) {
        if (connection.player.playbackState != androidx.media3.common.Player.STATE_READY) {
            return
        }
        val remoteState = remoteSession.state ?: return
        val currentSongId = connection.player.currentMediaItem?.mediaId
        if (currentSongId != remoteState.songId) {
            return
        }

        val target = expectedPosition(remoteSession)
        val isLan = _connectionMode.value == ListenTogetherConnectionMode.LAN
        val hardSeekThreshold = if (isLan) 3500L else 5000L
        val diff = connection.player.currentPosition - target
        val absDiff = kotlin.math.abs(diff)

        if (absDiff > hardSeekThreshold) {
            suppressLocalSyncUntil = System.currentTimeMillis() + APPLY_GRACE_MS
            connection.player.seekTo(target)
            if (connection.player.playbackParameters.speed != 1.0f) {
                connection.player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
            }
        } else if (absDiff > (if (isLan) 800L else 1200L)) {
            val speed = if (diff < 0) 1.04f else 0.96f
            if (connection.player.playbackParameters.speed != speed) {
                connection.player.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
            }
        } else {
            if (connection.player.playbackParameters.speed != 1.0f) {
                connection.player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
            }
        }
    }

    private fun expectedPosition(session: ListenTogetherSession): Long {
        val state = session.state ?: return 0L
        val serverAge = (session.serverNow - state.updatedAt).coerceAtLeast(0)
        return if (state.isPlaying) {
            state.positionMs + serverAge
        } else {
            state.positionMs
        }.coerceAtLeast(0)
    }

    fun updateSettings(
        allowGuestsToControlPlayback: Boolean,
        allowGuestsToAddTracks: Boolean,
    ) {
        if (!_isHost.value) return
        val newSettings = ListenTogetherSettings(
            allowGuestsToControlPlayback = allowGuestsToControlPlayback,
            allowGuestsToAddTracks = allowGuestsToAddTracks,
        )
        scope.launch {
            if (_connectionMode.value == ListenTogetherConnectionMode.LAN) {
                val server = lanServer ?: return@launch
                server.settings = newSettings
                server.stateVersion++
                _session.value = server.buildSessionSnapshot("host")
            } else {
                val session = _session.value ?: return@launch
                val connection = playerConnection ?: return@launch
                val metadata = connection.mediaMetadata.value ?: return@launch
                runCatching {
                    ListenTogetherClient.updateState(
                        code = session.code,
                        participantId = session.participantId,
                        state = ListenTogetherPlaybackState(
                            songId = metadata.id,
                            title = metadata.title,
                            artists = metadata.artists.map { it.name },
                            thumbnailUrl = metadata.thumbnailUrl,
                            positionMs = connection.player.currentPosition,
                            isPlaying = connection.player.playWhenReady,
                            durationMs = connection.player.duration.takeIf { it > 0 } ?: (metadata.duration.toLong() * 1000L).coerceAtLeast(0L),
                            settings = newSettings,
                        )
                    )
                }.onSuccess { updated ->
                    _session.value = updated.copy(participantId = session.participantId)
                }
            }
        }
    }

    fun kickParticipant(participantId: String) {
        if (!_isHost.value) return
        lanServer?.kickParticipant(participantId)
        val currentSession = _session.value
        if (currentSession != null) {
            val updatedList = currentSession.participantList.filter { it.id != participantId }
            _session.value = currentSession.copy(
                participants = updatedList.size,
                participantList = updatedList
            )
        }
    }

    fun sendGuestAction(
        action: String,
        positionMs: Long? = null,
        extraData: JSONObject? = null,
    ): Boolean {
        val activeSession = _session.value ?: return false
        val settings = activeSession.settings

        if (action in listOf("play", "pause", "toggle_play_pause", "seek", "next", "skip_next", "previous", "skip_previous")) {
            if (!_isHost.value && !settings.allowGuestsToControlPlayback) {
                _message.value = "Playback controls are disabled by the host"
                return false
            }
        }
        if (action in listOf("add_track", "play_next")) {
            if (!_isHost.value && !settings.allowGuestsToAddTracks) {
                _message.value = "Adding songs is disabled by the host"
                return false
            }
        }

        if (_isHost.value) {
            val connection = playerConnection ?: return false
            when (action) {
                "play" -> connection.player.play()
                "pause" -> connection.player.pause()
                "toggle_play_pause" -> connection.togglePlayPause()
                "seek" -> if (positionMs != null && positionMs >= 0) connection.player.seekTo(positionMs)
                "next", "skip_next" -> connection.seekToNext()
                "previous", "skip_previous" -> connection.seekToPrevious()
            }
            return true
        }

        scope.launch {
            runCatching {
                if (_connectionMode.value == ListenTogetherConnectionMode.LAN) {
                    val hostAddr = lanHostAddress ?: return@launch
                    LanTogetherClient.sendAction(
                        hostAddress = hostAddr,
                        participantId = activeSession.participantId,
                        action = action,
                        positionMs = positionMs,
                        extraData = extraData,
                    )
                } else {
                    val currentState = activeSession.state ?: return@launch
                    val targetPlaying = when (action) {
                        "play" -> true
                        "pause" -> false
                        "toggle_play_pause" -> !currentState.isPlaying
                        else -> currentState.isPlaying
                    }
                    val targetPosition = positionMs ?: currentState.positionMs
                    val newState = currentState.copy(
                        isPlaying = targetPlaying,
                        positionMs = targetPosition,
                        updatedAt = System.currentTimeMillis(),
                        actionCommand = action,
                        actionPayload = extraData?.toString(),
                        actionId = UUID.randomUUID().toString().take(8),
                    )
                    ListenTogetherClient.updateState(
                        code = activeSession.code,
                        participantId = activeSession.participantId,
                        state = newState,
                    )
                }
            }.onSuccess { updatedSession ->
                _session.value = updatedSession.copy(participantId = activeSession.participantId)
                lastAppliedVersion = updatedSession.stateVersion
                suppressLocalSyncUntil = System.currentTimeMillis() + APPLY_GRACE_MS
            }.onFailure { e ->
                _message.value = e.message ?: "Failed to send action"
            }
        }
        return true
    }

    fun notifyDisallowedAction(notice: String) {
        _message.value = notice
    }
}

