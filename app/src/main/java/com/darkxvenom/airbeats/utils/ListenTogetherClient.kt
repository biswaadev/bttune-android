package com.darkxvenom.airbeats.utils

import androidx.media3.common.MediaItem
import com.darkxvenom.airbeats.extensions.toMediaItem
import com.darkxvenom.airbeats.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ListenTogetherClient {
    val BACKEND_URL: String
        get() = RemoteConfigManager.listenTogetherUrl

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val httpClient =
        OkHttpClient
            .Builder()
            .callTimeout(4, TimeUnit.SECONDS)
            .build()

    suspend fun createSession(
        displayName: String,
        state: ListenTogetherPlaybackState,
    ): ListenTogetherSession =
        post(
            "$BACKEND_URL/sessions",
            JSONObject()
                .put("name", displayName)
                .put("state", state.toJson())
                .put("settings", state.settings.toJson())
        ) { body ->
            ListenTogetherSession.fromJson(body)
        }

    suspend fun joinSession(
        code: String,
        displayName: String,
    ): ListenTogetherSession =
        post(
            "$BACKEND_URL/sessions/${code.trim().uppercase()}/join",
            JSONObject().put("name", displayName)
        ) { body ->
            ListenTogetherSession.fromJson(body)
        }

    suspend fun getSession(code: String): ListenTogetherSession =
        get("$BACKEND_URL/sessions/${code.trim().uppercase()}") { body ->
            ListenTogetherSession.fromJson(body)
        }

    suspend fun updateState(
        code: String,
        participantId: String,
        state: ListenTogetherPlaybackState,
    ): ListenTogetherSession =
        post(
            "$BACKEND_URL/sessions/${code.trim().uppercase()}/state",
            JSONObject()
                .put("participantId", participantId)
                .put("state", state.toJson())
                .put("settings", state.settings.toJson())
        ) { body ->
            ListenTogetherSession.fromJson(body)
        }

    suspend fun leaveSession(
        code: String,
        participantId: String,
    ) {
        post(
            "$BACKEND_URL/sessions/${code.trim().uppercase()}/leave",
            JSONObject().put("participantId", participantId)
        ) {}
    }

    private suspend fun <T> get(
        url: String,
        parser: (JSONObject) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(errorMessage(text, response.code))
                parser(JSONObject(text))
            }
        }

    private suspend fun <T> post(
        url: String,
        body: JSONObject,
        parser: (JSONObject) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .build()
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(errorMessage(text, response.code))
                parser(JSONObject(text))
            }
        }

    private fun errorMessage(
        text: String,
        code: Int,
    ): String =
        runCatching {
            JSONObject(text).optString("error")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Listen Together request failed ($code)"
}

data class ListenTogetherSettings(
    val allowGuestsToControlPlayback: Boolean = false,
    val allowGuestsToAddTracks: Boolean = true,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("allowGuestsToControlPlayback", allowGuestsToControlPlayback)
            .put("allowGuestsToAddTracks", allowGuestsToAddTracks)

    companion object {
        fun fromJson(json: JSONObject?): ListenTogetherSettings {
            if (json == null) return ListenTogetherSettings()
            return ListenTogetherSettings(
                allowGuestsToControlPlayback = json.optBoolean("allowGuestsToControlPlayback", false),
                allowGuestsToAddTracks = json.optBoolean("allowGuestsToAddTracks", true),
            )
        }
    }
}

data class ListenTogetherQueueItem(
    val songId: String,
    val title: String,
    val artist: String = "",
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0L,
) {
    fun toMediaMetadata() =
        MediaMetadata(
            id = songId,
            title = title,
            artists = if (artist.isNotBlank()) listOf(MediaMetadata.Artist(id = null, name = artist)) else emptyList(),
            duration = if (durationMs > 0) (durationMs / 1000).toInt() else -1,
            thumbnailUrl = thumbnailUrl ?: if (songId.isNotBlank()) "https://i.ytimg.com/vi/$songId/mqdefault.jpg" else null,
        )

    fun toMediaItem(): MediaItem = toMediaMetadata().toMediaItem()

    fun toJson(): JSONObject =
        JSONObject().apply {
            put("id", songId)
            put("title", title)
            put("artist", artist)
            put("thumbnailUrl", thumbnailUrl.orEmpty())
            put("durationMs", durationMs)
        }

    companion object {
        fun fromJson(json: JSONObject): ListenTogetherQueueItem =
            ListenTogetherQueueItem(
                songId = json.optString("id").ifBlank { json.optString("songId") },
                title = json.optString("title").ifBlank { "Unknown Song" },
                artist = json.optString("artist").ifBlank { json.optString("artists") },
                thumbnailUrl = json.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                durationMs = json.optLong("durationMs", 0L),
            )
    }
}

data class ListenTogetherSession(
    val code: String,
    val participantId: String,
    val joinUrl: String,
    val participants: Int,
    val participantList: List<ListenTogetherParticipant>,
    val hostName: String,
    val controllerId: String,
    val controllerName: String,
    val stateVersion: Long,
    val serverNow: Long,
    val state: ListenTogetherPlaybackState?,
    val settings: ListenTogetherSettings = ListenTogetherSettings(),
    val queue: List<ListenTogetherQueueItem> = emptyList(),
) {
    fun toJson(): JSONObject {
        val queueArr = JSONArray()
        queue.forEach { queueArr.put(it.toJson()) }

        return JSONObject()
            .put("code", code)
            .put("participantId", participantId)
            .put("joinUrl", joinUrl)
            .put("participants", participants)
            .put("participantList", JSONArray().apply { participantList.forEach { put(it.toJson()) } })
            .put("hostName", hostName)
            .put("controllerId", controllerId)
            .put("controllerName", controllerName)
            .put("stateVersion", stateVersion)
            .put("serverNow", serverNow)
            .put("state", state?.toJson())
            .put("settings", settings.toJson())
            .put("queue", queueArr)
    }

    companion object {
        fun fromJson(json: JSONObject): ListenTogetherSession {
            val parsedState = json.optJSONObject("state")?.let(ListenTogetherPlaybackState::fromJson)
            val parsedSettings =
                parsedState?.settings
                    ?: json.optJSONObject("settings")?.let(ListenTogetherSettings::fromJson)
                    ?: json.optJSONObject("state")?.optJSONObject("settings")?.let(ListenTogetherSettings::fromJson)
                    ?: ListenTogetherSettings()

            val parsedQueue = parsedState?.queue?.takeIf { it.isNotEmpty() }
                ?: json.optJSONArray("queue")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optJSONObject(index)?.let(ListenTogetherQueueItem::fromJson)
                    }
                }.orEmpty()

            return ListenTogetherSession(
                code = json.optString("code"),
                participantId = json.optString("participantId"),
                joinUrl = json.optString("joinUrl"),
                participants = json.optInt("participants", 1),
                participantList =
                    json.optJSONArray("participantList")?.let { array ->
                        (0 until array.length()).mapNotNull { index ->
                            array.optJSONObject(index)?.let(ListenTogetherParticipant::fromJson)
                        }
                    }.orEmpty(),
                hostName = json.optString("hostName").ifBlank { "AirBeats listener" },
                controllerId = json.optString("controllerId"),
                controllerName = json.optString("controllerName").ifBlank { "AirBeats listener" },
                stateVersion = json.optLong("stateVersion", 0L),
                serverNow = json.optLong("serverNow", System.currentTimeMillis()),
                state = parsedState,
                settings = parsedSettings,
                queue = parsedQueue,
            )
        }
    }
}

data class ListenTogetherParticipant(
    val id: String,
    val name: String,
    val isHost: Boolean,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("isHost", isHost)

    companion object {
        fun fromJson(json: JSONObject) =
            ListenTogetherParticipant(
                id = json.optString("id"),
                name = json.optString("name").ifBlank { "AirBeats listener" },
                isHost = json.optBoolean("isHost"),
            )
    }
}

data class ListenTogetherPlaybackState(
    val songId: String = "",
    val title: String = "",
    val artists: List<String> = emptyList(),
    val thumbnailUrl: String? = null,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
    val settings: ListenTogetherSettings = ListenTogetherSettings(),
    val actionCommand: String? = null,
    val actionPayload: String? = null,
    val actionId: String? = null,
    val queue: List<ListenTogetherQueueItem> = emptyList(),
    val totalQueueSize: Int = 0,
) {
    fun toMediaMetadata() =
        MediaMetadata(
            id = songId,
            title = title,
            artists = artists.map { MediaMetadata.Artist(id = null, name = it) },
            duration = if (durationMs > 0) (durationMs / 1000).toInt() else -1,
            thumbnailUrl = thumbnailUrl,
        )

    fun toJson(): JSONObject {
        val metaString = buildString {
            append(META_PREFIX)
            append("pb=").append(if (settings.allowGuestsToControlPlayback) "1" else "0")
            append("&tr=").append(if (settings.allowGuestsToAddTracks) "1" else "0")
            if (!actionCommand.isNullOrBlank()) {
                append("&cmd=").append(java.net.URLEncoder.encode(actionCommand, "UTF-8"))
            }
            if (!actionPayload.isNullOrBlank()) {
                append("&pl=").append(java.net.URLEncoder.encode(actionPayload, "UTF-8"))
            }
            if (!actionId.isNullOrBlank()) {
                append("&actId=").append(java.net.URLEncoder.encode(actionId, "UTF-8"))
            }
            if (durationMs > 0L) {
                append("&dur=").append(durationMs)
            }
            if (totalQueueSize > 0) {
                append("&qSize=").append(totalQueueSize)
            }
        }
        val cleanArtists = artists.filterNot { it.startsWith(META_PREFIX) || it.startsWith(QUEUE_PREFIX) }.take(1).toMutableList()
        val artistsList = mutableListOf<String>()
        cleanArtists.forEach { artistsList.add(it) }
        artistsList.add(metaString)

        // Tunnel shared queue through available slots in artists (worker allows up to 8 slots, 120 chars each)
        if (queue.isNotEmpty()) {
            val maxSlots = (8 - artistsList.size).coerceAtLeast(0)
            var currentSlotIndex = 0
            var currentBuffer = StringBuilder()

            for (item in queue) {
                if (currentSlotIndex >= maxSlots) break
                val safeTitle = item.title.replace(":", " ").replace(";", " ").trim().take(22)
                val entry = "${item.songId}:$safeTitle"
                val prefix = "$QUEUE_PREFIX$currentSlotIndex:"
                val testLength = prefix.length + (if (currentBuffer.isEmpty()) 0 else currentBuffer.length + 1) + entry.length
                if (testLength > 118) {
                    if (currentBuffer.isNotEmpty()) {
                        artistsList.add("$prefix$currentBuffer")
                        currentSlotIndex++
                        currentBuffer = StringBuilder()
                    }
                    if (currentSlotIndex >= maxSlots) break
                }
                if (currentBuffer.isNotEmpty()) currentBuffer.append(";")
                currentBuffer.append(entry)
            }
            if (currentBuffer.isNotEmpty() && currentSlotIndex < maxSlots) {
                artistsList.add("$QUEUE_PREFIX$currentSlotIndex:$currentBuffer")
            }
        }

        val queueArray = JSONArray()
        queue.forEach { queueArray.put(it.toJson()) }

        return JSONObject()
            .put("songId", songId)
            .put("title", title)
            .put("artists", JSONArray(artistsList))
            .put("thumbnailUrl", thumbnailUrl)
            .put("positionMs", positionMs)
            .put("isPlaying", isPlaying)
            .put("durationMs", durationMs)
            .put("updatedAt", updatedAt)
            .put("settings", settings.toJson())
            .put("actionCommand", actionCommand)
            .put("actionPayload", actionPayload)
            .put("actionId", actionId)
            .put("queue", queueArray)
            .put("totalQueueSize", totalQueueSize)
    }

    companion object {
        const val META_PREFIX = "__AB_META__:"
        const val QUEUE_PREFIX = "__AB_Q"

        fun fromJson(json: JSONObject): ListenTogetherPlaybackState {
            var parsedSettings: ListenTogetherSettings? =
                json.optJSONObject("settings")?.let(ListenTogetherSettings::fromJson)
            var parsedCommand: String? = json.optString("actionCommand").takeIf { it.isNotBlank() }
            var parsedPayload: String? = json.optString("actionPayload").takeIf { it.isNotBlank() }
            var parsedActionId: String? = json.optString("actionId").takeIf { it.isNotBlank() }
            var parsedDuration: Long = json.optLong("durationMs", 0L)
            var parsedQueueSize: Int = json.optInt("totalQueueSize", 0)

            val cleanArtists = mutableListOf<String>()
            val parsedQueueItems = mutableListOf<ListenTogetherQueueItem>()

            // 1. Direct JSON queue array (LAN mode or forward-compatible worker)
            val directQueueArray = json.optJSONArray("queue")
            val hasDirectQueue = (directQueueArray?.length() ?: 0) > 0
            directQueueArray?.let { array ->
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { obj ->
                        parsedQueueItems.add(ListenTogetherQueueItem.fromJson(obj))
                    }
                }
            }

            // 2. Parse artists array (metadata and tunneled queue)
            json.optJSONArray("artists")?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.optString(i)
                    if (item.startsWith(META_PREFIX)) {
                        val query = item.removePrefix(META_PREFIX)
                        val params = query.split("&").associate { param ->
                            val parts = param.split("=", limit = 2)
                            if (parts.size == 2) parts[0] to runCatching { java.net.URLDecoder.decode(parts[1], "UTF-8") }.getOrDefault(parts[1])
                            else parts[0] to ""
                        }
                        val pb = params["pb"]
                        val tr = params["tr"]
                        if (pb != null || tr != null) {
                            parsedSettings = ListenTogetherSettings(
                                allowGuestsToControlPlayback = pb == "1",
                                allowGuestsToAddTracks = tr != "0",
                            )
                        }
                        params["cmd"]?.takeIf { it.isNotBlank() }?.let { parsedCommand = it }
                        params["pl"]?.takeIf { it.isNotBlank() }?.let { parsedPayload = it }
                        params["actId"]?.takeIf { it.isNotBlank() }?.let { parsedActionId = it }
                        params["dur"]?.toLongOrNull()?.let { if (it > 0) parsedDuration = it }
                        params["qSize"]?.toIntOrNull()?.let { if (it > 0) parsedQueueSize = it }
                    } else if (item.startsWith(QUEUE_PREFIX)) {
                        if (!hasDirectQueue) {
                            val payload = item.substringAfter(":")
                            val entries = payload.split(";")
                            for (entry in entries) {
                                val colonIdx = entry.indexOf(':')
                                if (colonIdx > 0) {
                                    val id = entry.substring(0, colonIdx).trim()
                                    val t = entry.substring(colonIdx + 1).trim()
                                    if (id.isNotBlank()) {
                                        parsedQueueItems.add(
                                            ListenTogetherQueueItem(
                                                songId = id,
                                                title = t.ifBlank { "Queued track" },
                                                thumbnailUrl = "https://i.ytimg.com/vi/$id/mqdefault.jpg",
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } else if (item.isNotBlank()) {
                        cleanArtists.add(item)
                    }
                }
            }

            return ListenTogetherPlaybackState(
                songId = json.optString("songId"),
                title = json.optString("title"),
                artists = cleanArtists,
                thumbnailUrl = json.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                positionMs = json.optLong("positionMs"),
                isPlaying = json.optBoolean("isPlaying"),
                durationMs = parsedDuration,
                updatedAt = json.optLong("updatedAt"),
                settings = parsedSettings ?: ListenTogetherSettings(),
                actionCommand = parsedCommand,
                actionPayload = parsedPayload,
                actionId = parsedActionId,
                queue = parsedQueueItems,
                totalQueueSize = parsedQueueSize,
            )
        }
    }
}

