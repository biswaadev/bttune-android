package com.darkxvenom.airbeats.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.darkxvenom.airbeats.R
import com.darkxvenom.airbeats.extensions.toMediaItem
import com.darkxvenom.airbeats.jiosaavn.JioSaavnApi
import com.darkxvenom.airbeats.playback.MusicService
import com.darkxvenom.airbeats.playback.PlayerConnection
import com.darkxvenom.airbeats.playback.queues.ListQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Quick Settings Tile to start playing music in shuffle mode in the background
 * without opening the application window.
 */
class ShuffleTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeConnection: ServiceConnection? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        activeConnection?.let {
            runCatching { unbindService(it) }
            activeConnection = null
        }
    }

    private fun updateTileState() {
        qsTile?.let { tile ->
            val isPlaying = PlayerConnection.instance?.isPlaying?.value == true ||
                    MusicService.instance?.player?.isPlaying == true

            tile.label = getString(R.string.shuffle)
            if (isPlaying) {
                tile.state = Tile.STATE_ACTIVE
                val currentTitle = PlayerConnection.instance?.mediaMetadata?.value?.title
                    ?: MusicService.instance?.player?.currentMediaItem?.mediaMetadata?.title?.toString()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = currentTitle?.ifBlank { null } ?: getString(R.string.shuffle_playing)
                }
            } else {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.shuffle_tap_to_play)
                }
            }
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val action = Runnable {
            handleShuffle()
        }

        if (isLocked) {
            unlockAndRun(action)
        } else {
            action.run()
        }
    }

    private fun handleShuffle() {
        val existingService = MusicService.instance
        if (existingService != null) {
            playOrShuffleOnService(existingService, onFinished = {})
            return
        }

        // Start service as foreground service first so Android does not kill it
        val intent = Intent(this, MusicService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error starting MusicService from ShuffleTileService")
        }

        // Safely unbind existing connection if any
        activeConnection?.let {
            runCatching { unbindService(it) }
            activeConnection = null
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = (binder as? MusicService.MusicBinder)?.service
                if (service != null) {
                    playOrShuffleOnService(service) {
                        mainHandler.postDelayed({
                            runCatching {
                                if (activeConnection == this) {
                                    unbindService(this)
                                    activeConnection = null
                                }
                            }
                        }, 2500L)
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                activeConnection = null
            }
        }

        activeConnection = connection
        try {
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Timber.e(e, "Failed to bind to MusicService for shuffle tile")
        }
    }

    private fun playOrShuffleOnService(service: MusicService, onFinished: () -> Unit) {
        val player = service.player

        if (player.mediaItemCount > 0) {
            player.shuffleModeEnabled = true
            if (!player.isPlaying) {
                player.play()
            } else {
                player.seekToNextMediaItem()
            }
            updateTileState()
            onFinished()
        } else {
            // Queue is empty: load recent songs, library, liked songs, or trending music!
            serviceScope.launch {
                try {
                    val dbSongs = runCatching { service.database.recentSongs(100).firstOrNull() }.getOrNull()?.takeIf { it.isNotEmpty() }
                        ?: runCatching { service.database.allSongs().firstOrNull() }.getOrNull()?.takeIf { it.isNotEmpty() }
                        ?: runCatching { service.database.likedSongsByRowIdAsc().firstOrNull() }.getOrNull()?.takeIf { it.isNotEmpty() }
                        ?: runCatching { service.database.songsByPlayTimeAsc().firstOrNull() }.getOrNull()?.takeIf { it.isNotEmpty() }

                    if (!dbSongs.isNullOrEmpty()) {
                        val shuffled = dbSongs.shuffled().map { it.toMediaItem() }
                        withContext(Dispatchers.Main) {
                            service.playQueue(ListQueue(title = getString(R.string.shuffle), items = shuffled))
                            service.player.shuffleModeEnabled = true
                            service.player.play()
                            updateTileState()
                            onFinished()
                        }
                    } else {
                        // Brand new install fallback: fetch trending songs online
                        val trendingSongs = runCatching { JioSaavnApi.getTrendingSongs().getOrNull() }.getOrNull()
                        if (!trendingSongs.isNullOrEmpty()) {
                            val shuffledMediaItems = trendingSongs.shuffled().map { it.toMediaItem() }
                            withContext(Dispatchers.Main) {
                                service.playQueue(ListQueue(title = getString(R.string.shuffle), items = shuffledMediaItems))
                                service.player.shuffleModeEnabled = true
                                service.player.play()
                                updateTileState()
                                onFinished()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                updateTileState()
                                onFinished()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error loading songs for shuffle tile")
                    withContext(Dispatchers.Main) {
                        onFinished()
                    }
                }
            }
        }
    }
}
