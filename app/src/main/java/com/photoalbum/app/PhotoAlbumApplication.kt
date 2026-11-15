package com.photoalbum.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.Coil
import coil.decode.VideoFrameDecoder
import com.photoalbum.app.media.PlaybackManager

class PhotoAlbumApplication : Application() {
    lateinit var playback: PlaybackManager
        private set

    override fun onCreate() {
        super.onCreate()
        createChannels()
        setupImageLoader()
        playback = PlaybackManager(this)
    }

    private fun setupImageLoader() {
        val loader = ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
        Coil.setImageLoader(loader)
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MUSIC,
                "Music playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Now playing controls"
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val CHANNEL_MUSIC = "music_playback"
    }
}
