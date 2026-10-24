package com.bt.bttune.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bt.bttune.innertube.YouTube
import com.bt.bttune.constants.statToPeriod
import com.bt.bttune.db.MusicDatabase
import com.bt.bttune.ui.component.AvatarPreferenceManager
import com.bt.bttune.ui.component.AvatarSelection
import com.bt.bttune.ui.component.NamePreferenceManager
import com.bt.bttune.ui.screens.OptionStats
import com.bt.bttune.utils.BTTUNEStatsCloudClient
import com.bt.bttune.utils.BTTUNEStatsCloudSync
import com.bt.bttune.utils.GlobalStatsBoard
import com.bt.bttune.utils.LocalStatsUpload
import com.bt.bttune.utils.dataStore
import com.bt.bttune.utils.reportException
import timber.log.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import java.util.Locale
import com.bt.bttune.data.repository.NowPlayingTrack
import com.bt.bttune.data.repository.ScrobbleRepository
import com.bt.bttune.db.entities.EventWithSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import com.bt.bttune.ui.component.BTTUNERank
import javax.inject.Inject

data class GlobalStatsUiState(
    val isLoading: Boolean = true,
    val board: GlobalStatsBoard = GlobalStatsBoard(),
    val error: String? = null,
    val currentUserId: String = "",
    val currentUserName: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel
@Inject
constructor(
    val database: MusicDatabase,
    @ApplicationContext private val context: Context,
    private val namePreferenceManager: NamePreferenceManager,
    private val scrobbleRepository: ScrobbleRepository,
) : ViewModel() {
    val selectedOption = MutableStateFlow(OptionStats.CONTINUOUS)
    val indexChips = MutableStateFlow(0)
    val globalStats = MutableStateFlow(GlobalStatsUiState())

    val nowPlayingTrack: StateFlow<NowPlayingTrack?> = scrobbleRepository.nowPlaying

    val recentEvents: StateFlow<List<EventWithSong>> = database.events()
        .map { it.take(25) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalListenHours: Flow<Double> = database.mostPlayedSongsStats(0L, limit = -1, toTimeStamp = Long.MAX_VALUE)
        .map { songs ->
            val totalMs = songs.sumOf { it.timeListened?.toLong() ?: 0L }
            totalMs.toDouble() / (3600.0 * 1000.0)
        }

    val currentRank: Flow<BTTUNERank?> = totalListenHours.map { hours ->
        if (hours >= 1.0) BTTUNERank.fromHours(hours.toInt()) else null
    }

    private val cloudClient = BTTUNEStatsCloudClient()
    private val statsPreferences =
        context.getSharedPreferences("bttune_global_stats", Context.MODE_PRIVATE)

    val mostPlayedSongsStats =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database
                    .mostPlayedSongsStats(
                        fromTimeStamp = statToPeriod(selection, t),
                        limit = -1,
                        toTimeStamp =
                            if (selection == OptionStats.CONTINUOUS || t == 0) {
                                Long.MAX_VALUE
                            } else {
                                statToPeriod(selection, t - 1)
                            },
                    )
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostPlayedSongs =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database
                    .mostPlayedSongs(
                        fromTimeStamp = statToPeriod(selection, t),
                        limit = -1,
                        toTimeStamp =
                            if (selection == OptionStats.CONTINUOUS || t == 0) {
                                Long.MAX_VALUE
                            } else {
                                statToPeriod(selection, t - 1)
                            },
                    )
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostPlayedArtists =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database
                    .mostPlayedArtists(
                        statToPeriod(selection, t),
                        limit = -1,
                        toTimeStamp =
                            if (selection == OptionStats.CONTINUOUS || t == 0) {
                                Long.MAX_VALUE
                            } else {
                                statToPeriod(selection, t - 1)
                            },
                    ).map { artists ->
                        artists.filter { it.artist.isYouTubeArtist || it.artist.isLocalArtist || it.artist.isScrobbleArtist }
                    }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostPlayedAlbums =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database.mostPlayedAlbums(
                    statToPeriod(selection, t),
                    limit = -1,
                    toTimeStamp =
                        if (selection == OptionStats.CONTINUOUS || t == 0) {
                            Long.MAX_VALUE
                        } else {
                            statToPeriod(selection, t - 1)
                        },
                )
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val firstEvent =
        database
            .firstEvent()
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    init {
        viewModelScope.launch {
            syncAndLoadGlobalStats()
        }
        viewModelScope.launch {
            mostPlayedArtists.collect { artists ->
                artists
                    .map { it.artist }
                    .filter {
                        it.isYouTubeArtist && (it.thumbnailUrl == null || Duration.between(
                            it.lastUpdateTime,
                            LocalDateTime.now()
                        ) > Duration.ofDays(10))
                    }.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
        viewModelScope.launch {
            mostPlayedAlbums.collect { albums ->
                albums
                    .filter {
                        (it.album.id.startsWith("MPREb_") || it.album.id.startsWith("OLAK5uy_")) && it.album.songCount == 0
                    }.forEach { album ->
                        YouTube
                            .album(album.id)
                            .onSuccess { albumPage ->
                                database.query {
                                    update(album.album, albumPage, album.artists)
                                }
                            }.onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.query {
                                        delete(album.album)
                                    }
                                }
                            }
                    }
            }
        }
    }

    fun markWeeklyPopupSeen() {
        statsPreferences.edit().putString(KEY_LAST_WEEKLY_POPUP, currentWeekKey()).apply()
    }

    fun shouldShowWeeklyPopup(): Boolean =
        statsPreferences.getString(KEY_LAST_WEEKLY_POPUP, "") != currentWeekKey()

    fun refreshGlobalStats() {
        viewModelScope.launch {
            syncAndLoadGlobalStats(forceUpload = false)
        }
    }

    private suspend fun syncAndLoadGlobalStats(forceUpload: Boolean = false) {
        val currentName = runCatching { namePreferenceManager.userName.first().trim() }.getOrDefault("")
        globalStats.value = globalStats.value.copy(isLoading = true, error = null, currentUserName = currentName)
        val userId = BTTUNEStatsCloudSync.resolveStableUserId(context, namePreferenceManager, statsPreferences)

        var mustUpdateGlobalStats = forceUpload || shouldUploadToday()

        // Read leaderboard first to check if cloud has recorded higher or different listen time than local DB
        val boardResult = cloudClient.readBoard()
        val initialBoard = boardResult.getOrNull()
        if (initialBoard != null) {
            val userNumber = BTTUNEStatsCloudSync.getUserNumber(context)
            val matchedUser = initialBoard.users.firstOrNull { it.id == userId }
                ?: (if (currentName.isNotBlank() && !currentName.equals("BTTUNE User", ignoreCase = true))
                    initialBoard.users.firstOrNull { it.name.trim().equals(currentName, ignoreCase = true) }
                else null)
                ?: (if (!userNumber.isNullOrBlank())
                    initialBoard.users.firstOrNull { it.user == userNumber }
                else null)

            // If global stats is greater than the app's local stats (e.g. user restored an older backup),
            // update global stats according to the app so the app is the authoritative source
            if (matchedUser != null) {
                val currentLocalTime = buildUpload(userId)?.totalListenMs ?: 0L
                if (matchedUser.totalListenMs > currentLocalTime) {
                    mustUpdateGlobalStats = true
                }
            }
        }

        if (mustUpdateGlobalStats) {
            buildUpload(userId)?.let { upload ->
                cloudClient
                    .uploadDaily(upload)
                    .onSuccess { board ->
                        statsPreferences.edit().putString(KEY_LAST_UPLOAD_DAY, LocalDate.now().toString()).apply()
                        val userNumber = BTTUNEStatsCloudSync.getUserNumber(context)
                        val updatedUserNumber = board.userNumber ?: board.users.firstOrNull { it.id == userId }?.user
                        if (!updatedUserNumber.isNullOrBlank()) {
                            BTTUNEStatsCloudSync.persistUserNumber(context, updatedUserNumber)
                        }
                        globalStats.value =
                            GlobalStatsUiState(
                                isLoading = false,
                                board = board,
                                currentUserId = userId,
                                currentUserName = currentName,
                                error = null,
                            )
                        return
                    }.onFailure { uploadError ->
                        Timber.d("StatsViewModel: Daily upload error/throttled: ${uploadError.message}")
                    }
            }
        }

        if (initialBoard != null) {
            globalStats.value =
                GlobalStatsUiState(
                    isLoading = false,
                    board = initialBoard,
                    currentUserId = userId,
                    currentUserName = currentName,
                )
        } else {
            val error = boardResult.exceptionOrNull()
            globalStats.value =
                globalStats.value.copy(
                    isLoading = false,
                    error = error?.message,
                    currentUserId = userId,
                    currentUserName = currentName,
                )
        }
    }

    private suspend fun buildUpload(userId: String): LocalStatsUpload? {
        val isNameSet = namePreferenceManager.isNameSet.first()
        val settings = context.dataStore.data.first()
        val settingsEmail = settings[com.bt.bttune.constants.AccountEmailKey]?.trim()?.takeIf { it.isNotBlank() }
        val managerEmail = namePreferenceManager.accountEmail.first().trim().takeIf { it.isNotBlank() }
        val effectiveEmail = (managerEmail ?: settingsEmail)?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

        val settingsName = settings[com.bt.bttune.constants.AccountNameKey]?.trim()?.takeIf { it.isNotBlank() }
        val managerName = namePreferenceManager.userName.first().trim().takeIf { it.isNotBlank() }
        val effectiveName = (managerName ?: settingsName ?: effectiveEmail?.substringBefore("@"))?.ifBlank { null }
            ?: (android.os.Build.MODEL ?: "BTTUNE User")

        val isSignedIn = !effectiveEmail.isNullOrBlank() || settings[com.bt.bttune.constants.InnerTubeCookieKey]?.isNotBlank() == true || isNameSet

        // Automatically sync email and username to namePreferenceManager if missing
        if (!effectiveEmail.isNullOrBlank() && managerEmail.isNullOrBlank()) {
            runCatching { namePreferenceManager.saveAccountEmail(effectiveEmail) }
        }
        if (managerName.isNullOrBlank() && !effectiveName.isBlank() && effectiveName != "BTTUNE User") {
            runCatching { namePreferenceManager.saveUserName(effectiveName) }
        }

        if (!isSignedIn && !isNameSet) return null

        val now = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
        val weekStart =
            LocalDate
                .now()
                .with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        val allSongs = database.mostPlayedSongsStats(0L, limit = -1, toTimeStamp = Long.MAX_VALUE).first()
        val weekSongs = database.mostPlayedSongsStats(weekStart, limit = -1, toTimeStamp = Long.MAX_VALUE).first()
        val totalListenMs = allSongs.sumOf { it.timeListened?.toLong() ?: 0L }
        val weeklyListenMs = weekSongs.sumOf { it.timeListened?.toLong() ?: 0L }
        val name = effectiveName
        val email = effectiveEmail
        val profileUrl =
            when (val avatar = AvatarPreferenceManager(context).getAvatarSelection.first()) {
                is AvatarSelection.DiceBear -> avatar.url
                is AvatarSelection.Custom -> avatar.cloudUrl ?: avatar.uri
                else -> null
            }
        var fcmToken: String? = null
        for (i in 1..3) {
            fcmToken = try {
                suspendCancellableCoroutine<String?> { continuation ->
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            continuation.resume(task.result)
                        } else {
                            continuation.resume(null)
                        }
                    }
                }
            } catch (e: Exception) {
                null
            }
            if (fcmToken != null) break
            kotlinx.coroutines.delay(1000L * i)
        }
        if (fcmToken == null) {
            fcmToken = "n/v"
        }

        return LocalStatsUpload(
            userId = userId,
            user = BTTUNEStatsCloudSync.getUserNumber(context),
            name = name,
            profileUrl = profileUrl,
            email = email,
            totalListenMs = totalListenMs,
            weeklyListenMs = weeklyListenMs,
            fcmToken = fcmToken,
        )
    }

    private fun shouldUploadToday(): Boolean = true

    private fun currentWeekKey(): String {
        val date = LocalDate.now()
        val fields = WeekFields.of(Locale.getDefault())
        return "${date.get(fields.weekBasedYear())}-${date.get(fields.weekOfWeekBasedYear())}"
    }

    private companion object {
        const val KEY_USER_ID = "global_stats_user_id"
        const val KEY_LAST_UPLOAD_DAY = "last_global_stats_upload_day"
        const val KEY_LAST_WEEKLY_POPUP = "last_weekly_global_popup"
    }
}
