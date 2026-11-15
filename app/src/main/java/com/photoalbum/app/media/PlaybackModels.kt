package com.photoalbum.app.media

import android.net.Uri
import com.photoalbum.app.data.AlbumItem

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

data class QueueTrack(
    val uri: Uri,
    val title: String,
    val mimeType: String? = null
)

data class PlaybackState(
    val queue: List<QueueTrack> = emptyList(),
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val speed: Float = 1f,
    val sleepTimerMinutesLeft: Int? = null,
    val playlistTitle: String = "",
    val memoryReelActive: Boolean = false,
    val memoryReelPhotos: List<Uri> = emptyList(),
    val memoryReelPhotoIndex: Int = 0,
    val crossfadeEnabled: Boolean = true,
    val nightCapsule: Boolean = false
) {
    val current: QueueTrack? get() = queue.getOrNull(index)
    val hasQueue: Boolean get() = queue.isNotEmpty()
}

fun AlbumItem.toQueueTrack() = QueueTrack(
    uri = uri,
    title = name,
    mimeType = mimeType
)
