package com.darkxvenom.airbeats.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.darkxvenom.airbeats.R
import com.darkxvenom.airbeats.extensions.toMediaItem
import com.darkxvenom.airbeats.playback.MusicService
import com.darkxvenom.airbeats.playback.PlayerConnection
import com.darkxvenom.airbeats.playback.queues.ListQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Quick Settings Tile to start playing music in shuffle mode in the background
 * without opening the application window.
 */
class ShuffleTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }

    private fun updateTileState() {
        qsTile?.let { tile ->
            val isPlaying = PlayerConnection.instance?.isPlaying?.value == true ||
                    MusicService.instance?.player?.isPlaying == true

            tile.label = getString(R.string.shuffle)
            if (isPlaying) {
                tile.state = Tile.STATE_ACTIVE
                val currentTitle = PlayerConnection.instance?.currentMetadata?.value?.title
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
            playOrShuffleOnService(existingService)
            return
        }

        // Service not yet running in memory - bind and start playback in background
        val intent = Intent(this, MusicService::class.java)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = (binder as? MusicService.MusicBinder)?.service
                if (service != null) {
                    playOrShuffleOnService(service)
                }
                try {
                    unbindService(this)
                } catch (e: Exception) {
                    Timber.w(e, "Error unbinding from MusicService in ShuffleTileService")
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }

        try {
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Timber.e(e, "Failed to bind to MusicService for shuffle tile")
        }
    }

    private fun playOrShuffleOnService(service: MusicService) {
        val player = service.player

        if (player.mediaItemCount > 0) {
            player.shuffleModeEnabled = true
            if (!player.isPlaying) {
                player.play()
            } else {
                player.seekToNextMediaItem()
            }
            updateTileState()
        } else {
            // Queue is empty: load library or liked songs, shuffle them, and play!
            service.scope.launch(Dispatchers.IO) {
                try {
                    val songs = service.database.songsByPlayTimeAsc().firstOrNull()
                        ?: service.database.likedSongsByRowIdAsc().firstOrNull()
                        ?: emptyList()

                    if (songs.isNotEmpty()) {
                        val shuffled = songs.shuffled().map { it.toMediaItem() }
                        withContext(Dispatchers.Main) {
                            service.playQueue(ListQueue(title = getString(R.string.shuffle), items = shuffled))
                            service.player.shuffleModeEnabled = true
                            service.player.play()
                            updateTileState()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            updateTileState()
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error loading songs for shuffle tile")
                }
            }
        }
    }
}
