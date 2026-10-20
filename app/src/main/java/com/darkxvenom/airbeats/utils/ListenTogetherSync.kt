package com.darkxvenom.airbeats.utils

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

enum class ListenTogetherConnectionMode {
    ONLINE,
    LAN
}

object ListenTogetherSync {
    private const val TAG = "ListenTogetherSync"
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
    private var lastSongLoadAttempt = 0L

    private val _sharedQueue = MutableStateFlow<List<ListenTogetherQueueItem>>(emptyList())
    val sharedQueue = _sharedQueue.asStateFlow()

    private val _session = MutableStateFlow<ListenTogetherSession?>(null)
    val session = _session.asStateFlow()

    private val _isHost = MutableStateFlow(false)
    val isHost = _isHost.asStateFlow()

    private val _connectionMode = MutableStateFlow(ListenTogetherConnectionMode.ONLINE)
    val connectionMode = _connectionMode.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun setDisplayMessage(msg: String?) {
        if (msg.isNullOrBlank()) {
            _message.value = null
            return
        }
        val lower = msg.lowercase()
        val isTechnicalNoise = lower.contains("cancelled") ||
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
                lower.contains("failed to connect") ||
                lower.contains("connection refused") ||
                lower.contains("unable to resolve host") ||
                lower.contains("end of input")
        if (!isTechnicalNoise) {
            _message.value = msg
        }
    }

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
        val defName = ListenTogetherStore.defaultName(context.applicationContext)
        _displayName.value = defName
        val saved = ListenTogetherStore.load(context.applicationContext)
        if (saved != null && _session.value == null) {
            _displayName.value = saved.displayName.ifBlank { defName }
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
                    setDisplayMessage(it.message)
                }
            }
        } else if (_session.value != null) {
            restartSync()
        }
    }

    fun setDisplayName(name: String) {
        _displayName.value = name.ifBlank { ListenTogetherStore.defaultName(appContext) }
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
        _displayName.value = displayName.ifBlank { ListenTogetherStore.defaultName(context) }
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
                setDisplayMessage("Session created")
                restartSync()
            }.onFailure {
                setDisplayMessage(it.message)
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
                setDisplayMessage("Joined session")
                restartSync()
            }.onFailure {
                setDisplayMessage(it.message)
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
                server.queueProvider = {
                    runCatching {
                        runBlocking(Dispatchers.Main) {
                            val player = connection.player
                            val currentQueue = mutableListOf<ListenTogetherQueueItem>()
                            val list = mutableListOf<JSONObject>()
                            if (!player.currentTimeline.isEmpty) {
                                val count = player.mediaItemCount
                                val currentIndex = player.currentMediaItemIndex
                                for (i in 0 until count) {
                                    val item = player.getMediaItemAt(i)
                                    val meta = item.mediaMetadata
                                    val t = meta.title?.toString() ?: "Unknown Song"
                                    val a = meta.artist?.toString() ?: ""
                                    val thumb = meta.artworkUri?.toString()
                                    currentQueue.add(ListenTogetherQueueItem(songId = item.mediaId, title = t, artist = a, thumbnailUrl = thumb))
                                    list.add(JSONObject().apply {
                                        put("index", i)
                                        put("id", item.mediaId)
                                        put("title", t)
                                        put("artist", a)
                                        put("thumbnailUrl", thumb ?: "")
                                        put("isCurrent", i == currentIndex)
                                    })
                                }
                            }
                            _sharedQueue.value = currentQueue
                            list
                        }
                    }.getOrElse { emptyList() }
                }
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
                            "play_index", "jump_to" -> {
                                val targetIndex = extraData?.optInt("index", -1) ?: -1
                                val targetSongId = extraData?.optString("songId")
                                if (targetIndex >= 0 && targetIndex < player.mediaItemCount) {
                                    player.seekToDefaultPosition(targetIndex)
                                    player.play()
                                } else if (!targetSongId.isNullOrBlank()) {
                                    for (i in 0 until player.mediaItemCount) {
                                        if (player.getMediaItemAt(i).mediaId == targetSongId) {
                                            player.seekToDefaultPosition(i)
                                            player.play()
                                            break
                                        }
                                    }
                                }
                            }
                            "play_song" -> {
                                val targetSongId = extraData?.optString("songId")
                                var found = false
                                if (!targetSongId.isNullOrBlank()) {
                                    for (i in 0 until player.mediaItemCount) {
                                        if (player.getMediaItemAt(i).mediaId == targetSongId) {
                                            player.seekToDefaultPosition(i)
                                            player.play()
                                            found = true
                                            break
                                        }
                                    }
                                }
                                if (!found && extraData != null) {
                                    runCatching {
                                        val track = ListenTogetherPlaybackState.fromJson(extraData)
                                        val meta = track.toMediaMetadata()
                                        connection.playQueue(com.darkxvenom.airbeats.playback.queues.ListQueue(items = listOf(meta.toMediaItem()), startIndex = 0))
                                    }
                                }
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
                            "remove_track", "remove_song" -> {
                                val targetIndex = extraData?.optInt("index", -1) ?: -1
                                val targetSongId = extraData?.optString("songId")
                                if (targetIndex >= 0 && targetIndex < player.mediaItemCount) {
                                    player.removeMediaItem(targetIndex)
                                } else if (!targetSongId.isNullOrBlank()) {
                                    connection.removeSongFromQueue(targetSongId)
                                }
                            }
                            "move_track" -> {
                                val from = extraData?.optInt("from", -1) ?: -1
                                val to = extraData?.optInt("to", -1) ?: -1
                                if (from >= 0 && to >= 0 && from < player.mediaItemCount && to < player.mediaItemCount) {
                                    player.moveMediaItem(from, to)
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
                setDisplayMessage("LAN host started on $localIp:$port")
                restartSync()
            } catch (e: Exception) {
                setDisplayMessage("Failed to start LAN host: ${e.message}")
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
                setDisplayMessage("Joined LAN session")
                restartSync()
            }.onFailure {
                setDisplayMessage(it.message ?: "Failed to connect to LAN host")
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
            _message.value = null
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
                    val player = connection.player
                    val currentQueue = mutableListOf<ListenTogetherQueueItem>()
                    if (!player.currentTimeline.isEmpty) {
                        val count = player.mediaItemCount
                        val currentIndex = player.currentMediaItemIndex
                        val start = (currentIndex + 1).coerceAtLeast(0)
                        for (i in start until count) {
                            val item = player.getMediaItemAt(i)
                            val meta = item.mediaMetadata
                            val t = meta.title?.toString() ?: "Unknown Song"
                            val a = meta.artist?.toString() ?: ""
                            val thumb = meta.artworkUri?.toString()
                            currentQueue.add(ListenTogetherQueueItem(songId = item.mediaId, title = t, artist = a, thumbnailUrl = thumb))
                        }
                    }
                    _sharedQueue.value = currentQueue

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
                        queue = currentQueue,
                        totalQueueSize = player.mediaItemCount,
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
                val remoteQueue = remoteSession.state?.queue?.takeIf { it.isNotEmpty() } ?: remoteSession.queue
                val keptSession = remoteSession.copy(
                    participantId = session.participantId,
                    queue = remoteQueue
                )
                _session.value = keptSession
                if (remoteQueue.isNotEmpty() || _sharedQueue.value.isNotEmpty()) {
                    _sharedQueue.value = remoteQueue
                }
                val remoteState = remoteSession.state ?: return@onSuccess
                val now = System.currentTimeMillis()

                val currentSongId = connection.player.currentMediaItem?.mediaId
                val isWrongSong = remoteState.songId.isNotBlank() && currentSongId != remoteState.songId
                val isNewVersion = remoteSession.stateVersion > lastAppliedVersion

                if (now > suppressLocalSyncUntil) {
                    if (isWrongSong || isNewVersion) {
                        applyRemoteState(connection, remoteSession)
                        lastAppliedVersion = remoteSession.stateVersion
                        return@onSuccess
                    }
                    softCorrectPosition(connection, remoteSession)
                } else if (isNewVersion) {
                    applyRemoteState(connection, remoteSession)
                    lastAppliedVersion = remoteSession.stateVersion
                    return@onSuccess
                }
            }.onFailure {
                setDisplayMessage(it.message)
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
            val preservedSettings = _session.value?.settings ?: session.settings
            val resolvedSettings = if (_isHost.value) {
                preservedSettings
            } else {
                remoteSession.settings
            }
            val remoteQueue = remoteSession.state?.queue?.takeIf { it.isNotEmpty() }
                ?: remoteSession.queue
            val keptSession = remoteSession.copy(
                participantId = session.participantId,
                settings = resolvedSettings,
                state = remoteSession.state?.copy(settings = resolvedSettings, queue = remoteQueue) ?: remoteSession.state,
                queue = remoteQueue,
            )
            _session.value = keptSession
            if (remoteQueue.isNotEmpty() || _sharedQueue.value.isNotEmpty()) {
                _sharedQueue.value = remoteQueue
            }
            val remoteState = remoteSession.state ?: return@onSuccess
            val now = System.currentTimeMillis()

            if (_isHost.value) {
                val cmd = remoteState.actionCommand
                val actionId = remoteState.actionId
                if (!cmd.isNullOrBlank() && actionId != null && actionId != lastHandledActionId) {
                    lastHandledActionId = actionId
                    val isControlCmd = cmd in listOf("play", "pause", "toggle_play_pause", "seek", "next", "skip_next", "previous", "skip_previous", "play_song", "play_index", "jump_to")
                    val isQueueCmd = cmd in listOf("add_track", "play_next", "remove_track", "remove_song", "move_track")
                    val allowed = if (isControlCmd) {
                        keptSession.settings.allowGuestsToControlPlayback || keptSession.settings.allowGuestsToAddTracks
                    } else if (isQueueCmd) {
                        keptSession.settings.allowGuestsToAddTracks || keptSession.settings.allowGuestsToControlPlayback
                    } else false

                    if (allowed) {
                        withContext(Dispatchers.Main) {
                            val player = connection.player
                            val json = remoteState.actionPayload?.let { runCatching { JSONObject(it) }.getOrNull() }
                            when (cmd) {
                                "play" -> player.play()
                                "pause" -> player.pause()
                                "toggle_play_pause" -> connection.togglePlayPause()
                                "seek" -> if (remoteState.positionMs >= 0) player.seekTo(remoteState.positionMs)
                                "next", "skip_next" -> connection.seekToNext()
                                "previous", "skip_previous" -> connection.seekToPrevious()
                                "play_index", "jump_to" -> {
                                    val targetIndex = json?.optInt("index", -1) ?: -1
                                    val targetSongId = json?.optString("songId")
                                    if (targetIndex >= 0 && targetIndex < player.mediaItemCount) {
                                        player.seekToDefaultPosition(targetIndex)
                                        player.play()
                                    } else if (!targetSongId.isNullOrBlank()) {
                                        for (i in 0 until player.mediaItemCount) {
                                            if (player.getMediaItemAt(i).mediaId == targetSongId) {
                                                player.seekToDefaultPosition(i)
                                                player.play()
                                                break
                                            }
                                        }
                                    }
                                }
                                "play_song" -> {
                                    val targetSongId = json?.optString("songId")?.ifBlank { json.optString("id") }
                                    var found = false
                                    if (!targetSongId.isNullOrBlank()) {
                                        for (i in 0 until player.mediaItemCount) {
                                            if (player.getMediaItemAt(i).mediaId == targetSongId) {
                                                player.seekToDefaultPosition(i)
                                                player.play()
                                                found = true
                                                break
                                            }
                                        }
                                    }
                                    if (!found && json != null) {
                                        val title = json.optString("title").ifBlank { "Queued Track" }
                                        val artist = json.optString("artist").ifBlank { json.optString("artists") }
                                        val meta = com.darkxvenom.airbeats.models.MediaMetadata(
                                            id = targetSongId ?: "",
                                            title = title,
                                            artists = if (artist.isNotBlank()) listOf(com.darkxvenom.airbeats.models.MediaMetadata.Artist(id = null, name = artist)) else emptyList(),
                                            duration = -1,
                                            thumbnailUrl = targetSongId?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" }
                                        )
                                        connection.playQueue(com.darkxvenom.airbeats.playback.queues.ListQueue(items = listOf(meta.toMediaItem()), startIndex = 0))
                                    }
                                }
                                "add_track" -> {
                                    if (json != null) {
                                        val songId = json.optString("songId").ifBlank { json.optString("id") }
                                        val title = json.optString("title").ifBlank { "Queued Track" }
                                        val artist = json.optString("artist").ifBlank { json.optString("artists") }
                                        val meta = com.darkxvenom.airbeats.models.MediaMetadata(
                                            id = songId,
                                            title = title,
                                            artists = if (artist.isNotBlank()) listOf(com.darkxvenom.airbeats.models.MediaMetadata.Artist(id = null, name = artist)) else emptyList(),
                                            duration = -1,
                                            thumbnailUrl = if (songId.isNotBlank()) "https://i.ytimg.com/vi/$songId/mqdefault.jpg" else null
                                        )
                                        connection.addToQueue(meta.toMediaItem())
                                    }
                                }
                                "play_next" -> {
                                    if (json != null) {
                                        val songId = json.optString("songId").ifBlank { json.optString("id") }
                                        val title = json.optString("title").ifBlank { "Queued Track" }
                                        val artist = json.optString("artist").ifBlank { json.optString("artists") }
                                        val meta = com.darkxvenom.airbeats.models.MediaMetadata(
                                            id = songId,
                                            title = title,
                                            artists = if (artist.isNotBlank()) listOf(com.darkxvenom.airbeats.models.MediaMetadata.Artist(id = null, name = artist)) else emptyList(),
                                            duration = -1,
                                            thumbnailUrl = if (songId.isNotBlank()) "https://i.ytimg.com/vi/$songId/mqdefault.jpg" else null
                                        )
                                        connection.playNext(meta.toMediaItem())
                                    }
                                }
                                "remove_track", "remove_song" -> {
                                    val targetIndex = json?.optInt("index", -1) ?: -1
                                    val targetSongId = json?.optString("songId")
                                    if (targetIndex >= 0 && targetIndex < player.mediaItemCount) {
                                        player.removeMediaItem(targetIndex)
                                    } else if (!targetSongId.isNullOrBlank()) {
                                        connection.removeSongFromQueue(targetSongId)
                                    }
                                }
                                "move_track" -> {
                                    val from = json?.optInt("from", -1) ?: -1
                                    val to = json?.optInt("to", -1) ?: -1
                                    if (from >= 0 && to >= 0 && from < player.mediaItemCount && to < player.mediaItemCount) {
                                        player.moveMediaItem(from, to)
                                    }
                                }
                            }
                        }
                        publishLocalStateOnline(connection, keptSession)
                        return@onSuccess
                    }
                }

                if (now - lastControllerPublishAt >= CONTROLLER_PUBLISH_MS) {
                    publishLocalStateOnline(connection, keptSession)
                    lastControllerPublishAt = now
                }
                return@onSuccess
            }

            val currentSongId = connection.player.currentMediaItem?.mediaId
            val isWrongSong = remoteState.songId.isNotBlank() && currentSongId != remoteState.songId
            val isNewVersion = remoteSession.stateVersion > lastAppliedVersion

            if (now > suppressLocalSyncUntil) {
                if (isWrongSong || isNewVersion) {
                    applyRemoteState(connection, remoteSession)
                    lastAppliedVersion = remoteSession.stateVersion
                    return@onSuccess
                }
                softCorrectPosition(connection, remoteSession)
            } else if (isNewVersion) {
                applyRemoteState(connection, remoteSession)
                lastAppliedVersion = remoteSession.stateVersion
                return@onSuccess
            }
        }.onFailure {
            Log.w(TAG, "syncOnceOnline failed: ${it.message}")
        }
    }

    private suspend fun publishLocalStateOnline(
        connection: PlayerConnection,
        session: ListenTogetherSession,
    ) {
        val metadata = connection.mediaMetadata.value ?: return
        val player = connection.player
        val upcomingQueue = mutableListOf<ListenTogetherQueueItem>()
        if (!player.currentTimeline.isEmpty) {
            val count = player.mediaItemCount
            val currentIndex = player.currentMediaItemIndex
            val start = (currentIndex + 1).coerceAtLeast(0)
            val end = minOf(count, start + 30)
            for (i in start until end) {
                val item = player.getMediaItemAt(i)
                val meta = item.mediaMetadata
                val t = meta.title?.toString() ?: "Unknown Song"
                val a = meta.artist?.toString() ?: ""
                val thumb = meta.artworkUri?.toString()
                upcomingQueue.add(ListenTogetherQueueItem(songId = item.mediaId, title = t, artist = a, thumbnailUrl = thumb))
            }
        }
        _sharedQueue.value = upcomingQueue

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
                        actionCommand = null,
                        actionPayload = null,
                        actionId = null,
                        queue = upcomingQueue,
                        totalQueueSize = player.mediaItemCount,
                    )
            )
        }.onSuccess { updatedSession ->
            val preservedSettings = _session.value?.settings ?: session.settings
            _session.value = updatedSession.copy(
                participantId = session.participantId,
                settings = preservedSettings,
                state = updatedSession.state?.copy(settings = preservedSettings, queue = upcomingQueue) ?: updatedSession.state,
                queue = upcomingQueue,
            )
            lastAppliedVersion = updatedSession.stateVersion
            lastControllerPublishAt = System.currentTimeMillis()
        }.onFailure {
            Log.w(TAG, "publishLocalStateOnline failed: ${it.message}")
        }
    }

    private suspend fun applyRemoteState(
        connection: PlayerConnection,
        remoteSession: ListenTogetherSession,
    ) {
        val remoteState = remoteSession.state ?: return
        val remoteQueue = remoteSession.state?.queue?.takeIf { it.isNotEmpty() }
            ?: remoteSession.queue
        if (remoteQueue.isNotEmpty() || _sharedQueue.value.isNotEmpty()) {
            _sharedQueue.value = remoteQueue
        }

        val player = connection.player
        val currentSongId = player.currentMediaItem?.mediaId

        if (remoteState.songId.isNotBlank()) {
            if (currentSongId != remoteState.songId) {
                if (loadingSongId != remoteState.songId) {
                    loadingSongId = remoteState.songId
                    suppressLocalSyncUntil = System.currentTimeMillis() + APPLY_GRACE_MS

                    withContext(Dispatchers.Main) {
                        var foundIndex = -1
                        for (i in 0 until player.mediaItemCount) {
                            if (player.getMediaItemAt(i).mediaId == remoteState.songId) {
                                foundIndex = i
                                break
                            }
                        }

                        val targetPos = expectedPosition(remoteSession)
                        if (foundIndex >= 0) {
                            player.seekTo(foundIndex, targetPos)
                            if (remoteState.isPlaying) {
                                player.play()
                            } else {
                                player.pause()
                            }
                        } else {
                            val targetMetadata = remoteState.toMediaMetadata()
                            val upcomingMediaItems = remoteQueue.map { it.toMediaItem() }
                            val fullQueueItems = listOf(targetMetadata.toMediaItem()) + upcomingMediaItems
                            connection.playQueue(
                                com.darkxvenom.airbeats.playback.queues.ListQueue(
                                    title = "Listen Together",
                                    items = fullQueueItems,
                                    startIndex = 0,
                                    position = targetPos
                                )
                            )
                            if (remoteState.isPlaying) {
                                player.play()
                            } else {
                                player.pause()
                            }
                        }
                    }
                }
                return
            } else {
                loadingSongId = null
                withContext(Dispatchers.Main) {
                    val currentIndex = player.currentMediaItemIndex
                    if (currentIndex >= 0) {
                        val existingUpcomingIds = mutableListOf<String>()
                        for (i in (currentIndex + 1) until player.mediaItemCount) {
                            existingUpcomingIds.add(player.getMediaItemAt(i).mediaId)
                        }
                        val remoteUpcomingIds = remoteQueue.map { it.songId }
                        if (existingUpcomingIds != remoteUpcomingIds) {
                            val from = currentIndex + 1
                            val to = player.mediaItemCount
                            if (to > from) {
                                player.removeMediaItems(from, to)
                            }
                            if (remoteQueue.isNotEmpty()) {
                                player.addMediaItems(from, remoteQueue.map { it.toMediaItem() })
                            }
                        }
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            if (player.playbackState == androidx.media3.common.Player.STATE_READY) {
                val target = expectedPosition(remoteSession)
                val isLan = _connectionMode.value == ListenTogetherConnectionMode.LAN
                val hardSeekThreshold = if (isLan) 3500L else 5000L
                val diff = player.currentPosition - target
                val absDiff = kotlin.math.abs(diff)

                if (absDiff > hardSeekThreshold) {
                    player.seekTo(target)
                    if (player.playbackParameters.speed != 1.0f) {
                        player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
                    }
                } else if (absDiff > (if (isLan) 800L else 1200L)) {
                    val speed = if (diff < 0) 1.04f else 0.96f
                    if (player.playbackParameters.speed != speed) {
                        player.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
                    }
                } else {
                    if (player.playbackParameters.speed != 1.0f) {
                        player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
                    }
                }
            }

            if (remoteState.isPlaying && !player.playWhenReady) {
                player.play()
            } else if (!remoteState.isPlaying && player.playWhenReady) {
                player.pause()
            }
        }
    }

    private fun softCorrectPosition(
        connection: PlayerConnection,
        remoteSession: ListenTogetherSession,
    ) {
        val player = connection.player
        val remoteState = remoteSession.state ?: return
        val currentSongId = player.currentMediaItem?.mediaId
        if (currentSongId != remoteState.songId) {
            return
        }

        val remoteQueue = remoteSession.state?.queue?.takeIf { it.isNotEmpty() } ?: remoteSession.queue
        if (remoteQueue.isNotEmpty() || _sharedQueue.value.isNotEmpty()) {
            _sharedQueue.value = remoteQueue
        }

        if (player.playbackState != androidx.media3.common.Player.STATE_READY) {
            return
        }

        val target = expectedPosition(remoteSession)
        val isLan = _connectionMode.value == ListenTogetherConnectionMode.LAN
        val hardSeekThreshold = if (isLan) 3500L else 5000L
        val diff = player.currentPosition - target
        val absDiff = kotlin.math.abs(diff)

        if (absDiff > hardSeekThreshold) {
            suppressLocalSyncUntil = System.currentTimeMillis() + APPLY_GRACE_MS
            player.seekTo(target)
            if (player.playbackParameters.speed != 1.0f) {
                player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
            }
        } else if (absDiff > (if (isLan) 800L else 1200L)) {
            val speed = if (diff < 0) 1.04f else 0.96f
            if (player.playbackParameters.speed != speed) {
                player.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
            }
        } else {
            if (player.playbackParameters.speed != 1.0f) {
                player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
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
        val currentSession = _session.value ?: return
        val updatedState = currentSession.state?.copy(settings = newSettings)
        _session.value = currentSession.copy(
            settings = newSettings,
            state = updatedState
        )

        scope.launch {
            if (_connectionMode.value == ListenTogetherConnectionMode.LAN) {
                val server = lanServer ?: return@launch
                server.settings = newSettings
                server.stateVersion++
                _session.value = server.buildSessionSnapshot("host")
            } else {
                val session = _session.value ?: return@launch
                val connection = playerConnection
                val metadata = connection?.mediaMetadata?.value
                val stateToSend = if (metadata != null && connection != null) {
                    ListenTogetherPlaybackState(
                        songId = metadata.id,
                        title = metadata.title,
                        artists = metadata.artists.map { it.name },
                        thumbnailUrl = metadata.thumbnailUrl,
                        positionMs = connection.player.currentPosition,
                        isPlaying = connection.player.playWhenReady,
                        durationMs = connection.player.duration.takeIf { it > 0 } ?: (metadata.duration.toLong() * 1000L).coerceAtLeast(0L),
                        settings = newSettings,
                    )
                } else {
                    session.state?.copy(settings = newSettings) ?: ListenTogetherPlaybackState(settings = newSettings)
                }

                runCatching {
                    ListenTogetherClient.updateState(
                        code = session.code,
                        participantId = session.participantId,
                        state = stateToSend
                    )
                }.onSuccess { updated ->
                    _session.value = updated.copy(
                        participantId = session.participantId,
                        settings = newSettings,
                        state = updated.state?.copy(settings = newSettings) ?: updated.state
                    )
                }.onFailure {
                    Log.w(TAG, "updateSettings online failed: ${it.message}")
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

        if (action in listOf("play", "pause", "toggle_play_pause", "seek", "next", "skip_next", "previous", "skip_previous", "play_song", "play_index", "jump_to")) {
            if (!_isHost.value && !settings.allowGuestsToControlPlayback && !settings.allowGuestsToAddTracks) {
                setDisplayMessage("Playback controls are disabled by the host")
                return false
            }
        }
        if (action in listOf("add_track", "play_next", "remove_track", "remove_song", "move_track")) {
            if (!_isHost.value && !settings.allowGuestsToAddTracks && !settings.allowGuestsToControlPlayback) {
                setDisplayMessage("Queue modifications are disabled by the host")
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

        suppressLocalSyncUntil = System.currentTimeMillis() + 650L

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
                    val currentState = activeSession.state ?: ListenTogetherPlaybackState(settings = activeSession.settings)
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
                        settings = activeSession.settings,
                        actionCommand = action,
                        actionPayload = extraData?.toString(),
                        actionId = UUID.randomUUID().toString().take(8),
                        queue = _sharedQueue.value,
                        totalQueueSize = playerConnection?.player?.mediaItemCount ?: 0,
                    )
                    ListenTogetherClient.updateState(
                        code = activeSession.code,
                        participantId = activeSession.participantId,
                        state = newState,
                    )
                }
            }.onSuccess { updatedSession ->
                val preservedSettings = activeSession.settings
                _session.value = updatedSession.copy(
                    participantId = activeSession.participantId,
                    settings = preservedSettings,
                    state = updatedSession.state?.copy(settings = preservedSettings) ?: updatedSession.state
                )
                lastAppliedVersion = updatedSession.stateVersion
                suppressLocalSyncUntil = System.currentTimeMillis() + 650L
                playerConnection?.let { conn ->
                    scope.launch {
                        delay(100L)
                        syncOnce(conn, _session.value ?: updatedSession)
                    }
                }
            }.onFailure { e ->
                setDisplayMessage(e.message ?: "Failed to send action")
            }
        }
        return true
    }

    fun notifyDisallowedAction(notice: String) {
        setDisplayMessage(notice)
    }
}

