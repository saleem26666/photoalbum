package com.photoalbum.app.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.photoalbum.app.data.AlbumItem
import com.photoalbum.app.data.AlbumItemType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * App-wide music / memory-reel controller (videos use a separate player screen).
 */
class PlaybackManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private var sleepJob: Job? = null
    private var reelJob: Job? = null
    private var crossfadeJob: Job? = null
    private var orderedQueue: List<QueueTrack> = emptyList()

    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        setHandleAudioBecomingNoisy(true)
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) ensureService()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncIndexFromPlayer()
                if (_state.value.crossfadeEnabled && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    softFadeIn()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                publishProgress()
            }
        })
        startProgressLoop()
        startCrossfadeWatch()
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        _state.update { it.copy(crossfadeEnabled = enabled) }
    }

    fun setNightCapsule(enabled: Boolean) {
        _state.update { it.copy(nightCapsule = enabled) }
        if (enabled && _state.value.sleepTimerMinutesLeft == null) {
            setSleepTimer(45)
        }
    }

    private fun startCrossfadeWatch() {
        crossfadeJob?.cancel()
        crossfadeJob = scope.launch {
            while (isActive) {
                delay(400)
                val s = _state.value
                if (!s.crossfadeEnabled || !s.isPlaying) continue
                val remaining = s.durationMs - s.positionMs
                if (s.durationMs > 5000 && remaining in 1..2500 && player.hasNextMediaItem()) {
                    // Soft volume dip near end, then next track fades in via transition listener
                    val vol = (remaining / 2500f).coerceIn(0.15f, 1f)
                    player.volume = vol
                } else if (player.volume < 0.99f && remaining > 3000) {
                    player.volume = (player.volume + 0.08f).coerceAtMost(1f)
                }
            }
        }
    }

    private fun softFadeIn() {
        scope.launch {
            player.volume = 0.2f
            repeat(8) {
                delay(80)
                player.volume = (player.volume + 0.1f).coerceAtMost(1f)
            }
            player.volume = 1f
        }
    }

    fun playTracks(
        tracks: List<QueueTrack>,
        startIndex: Int = 0,
        playlistTitle: String = "Now Playing",
        shuffle: Boolean = _state.value.shuffle
    ) {
        if (tracks.isEmpty()) return
        stopMemoryReelVisualOnly()
        orderedQueue = tracks
        val playList = if (shuffle) tracks.shuffled() else tracks
        val start = startIndex.coerceIn(0, playList.lastIndex)
        player.stop()
        player.clearMediaItems()
        player.addMediaItems(playList.map { it.toMediaItem() })
        player.shuffleModeEnabled = false // we shuffle the list ourselves for stable titles
        player.repeatMode = exoRepeat(_state.value.repeatMode)
        player.seekTo(start, 0L)
        player.prepare()
        player.play()
        _state.update {
            it.copy(
                queue = playList,
                index = start,
                playlistTitle = playlistTitle,
                isPlaying = true,
                shuffle = shuffle,
                memoryReelActive = false,
                memoryReelPhotos = emptyList(),
                memoryReelPhotoIndex = 0
            )
        }
        ensureService()
    }

    fun playAlbumItems(
        items: List<AlbumItem>,
        start: AlbumItem? = null,
        playlistTitle: String
    ) {
        val audio = items.filter { it.type == AlbumItemType.AUDIO }.map { it.toQueueTrack() }
        if (audio.isEmpty()) return
        val index = start?.let { s -> audio.indexOfFirst { it.uri == s.uri }.takeIf { it >= 0 } } ?: 0
        playTracks(audio, index, playlistTitle)
    }

    fun playFolderMix(audio: List<AlbumItem>, folderName: String) {
        playAlbumItems(audio, playlistTitle = "Folder · $folderName")
    }

    /** Unique: photos slideshow + background music from the same category. */
    fun startMemoryReel(
        photos: List<AlbumItem>,
        audio: List<AlbumItem>,
        folderName: String,
        photoSeconds: Long = 5L
    ) {
        if (photos.isEmpty()) return
        val photoUris = photos.map { it.uri }
        if (audio.isNotEmpty()) {
            playTracks(
                audio.map { it.toQueueTrack() },
                playlistTitle = "Memory Reel · $folderName"
            )
        } else {
            player.pause()
            _state.update {
                it.copy(
                    playlistTitle = "Memory Reel · $folderName",
                    isPlaying = false,
                    queue = emptyList(),
                    index = 0
                )
            }
        }
        _state.update {
            it.copy(
                memoryReelActive = true,
                memoryReelPhotos = photoUris,
                memoryReelPhotoIndex = 0
            )
        }
        reelJob?.cancel()
        reelJob = scope.launch {
            var i = 0
            while (isActive && _state.value.memoryReelActive && photoUris.isNotEmpty()) {
                _state.update { it.copy(memoryReelPhotoIndex = i % photoUris.size) }
                delay(photoSeconds * 1000)
                i++
            }
        }
        ensureService()
    }

    fun stopMemoryReel() {
        reelJob?.cancel()
        reelJob = null
        _state.update {
            it.copy(
                memoryReelActive = false,
                memoryReelPhotos = emptyList(),
                memoryReelPhotoIndex = 0
            )
        }
    }

    private fun stopMemoryReelVisualOnly() {
        reelJob?.cancel()
        reelJob = null
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else {
            player.play()
            ensureService()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        publishProgress()
    }

    fun seekBy(deltaMs: Long) {
        seekTo(player.currentPosition + deltaMs)
    }

    fun next() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
        else if (_state.value.repeatMode == RepeatMode.ALL && player.mediaItemCount > 0) {
            player.seekTo(0, 0L)
        }
        syncIndexFromPlayer()
    }

    fun previous() {
        if (player.currentPosition > 3000) {
            player.seekTo(0)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0)
        }
        syncIndexFromPlayer()
    }

    fun toggleShuffle() {
        val enable = !_state.value.shuffle
        val currentUri = _state.value.current?.uri
        val base = if (orderedQueue.isNotEmpty()) orderedQueue else _state.value.queue
        if (base.isEmpty()) {
            _state.update { it.copy(shuffle = enable) }
            return
        }
        val newList = if (enable) base.shuffled() else base
        val newIndex = currentUri?.let { uri ->
            newList.indexOfFirst { it.uri == uri }.takeIf { it >= 0 }
        } ?: 0
        val pos = player.currentPosition
        player.setMediaItems(newList.map { it.toMediaItem() }, newIndex, pos)
        player.prepare()
        if (_state.value.isPlaying) player.play()
        _state.update { it.copy(shuffle = enable, queue = newList, index = newIndex) }
    }

    fun cycleRepeat() {
        val next = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        player.repeatMode = exoRepeat(next)
        _state.update { it.copy(repeatMode = next) }
    }

    fun setSpeed(speed: Float) {
        val s = speed.coerceIn(0.5f, 2f)
        player.setPlaybackSpeed(s)
        _state.update { it.copy(speed = s) }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes == null || minutes <= 0) {
            _state.update { it.copy(sleepTimerMinutesLeft = null) }
            return
        }
        _state.update { it.copy(sleepTimerMinutesLeft = minutes) }
        sleepJob = scope.launch {
            var left = minutes
            while (isActive && left > 0) {
                delay(60_000)
                left--
                _state.update { it.copy(sleepTimerMinutesLeft = left) }
            }
            if (isActive) {
                // Soft stop for night listening
                player.pause()
                _state.update { it.copy(sleepTimerMinutesLeft = null, isPlaying = false) }
            }
        }
    }

    fun playFromQueue(index: Int) {
        if (index !in _state.value.queue.indices) return
        player.seekTo(index, 0L)
        player.play()
        _state.update { it.copy(index = index, isPlaying = true) }
        ensureService()
    }

    fun stopAndClear() {
        stopMemoryReel()
        setSleepTimer(null)
        player.stop()
        player.clearMediaItems()
        orderedQueue = emptyList()
        _state.value = PlaybackState()
    }

    fun release() {
        progressJob?.cancel()
        sleepJob?.cancel()
        reelJob?.cancel()
        crossfadeJob?.cancel()
        player.release()
    }

    private fun ensureService() {
        val intent = Intent(context, MusicPlayerService::class.java)
        context.startForegroundService(intent)
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                publishProgress()
                delay(500)
            }
        }
    }

    private fun publishProgress() {
        val duration = max(0L, player.duration.takeIf { it > 0 } ?: 0L)
        _state.update {
            it.copy(
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                isPlaying = player.isPlaying,
                index = player.currentMediaItemIndex.coerceAtLeast(0)
            )
        }
    }

    private fun syncIndexFromPlayer() {
        val idx = player.currentMediaItemIndex.coerceAtLeast(0)
        _state.update { it.copy(index = idx) }
        publishProgress()
    }

    private fun exoRepeat(mode: RepeatMode): Int = when (mode) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }

    private fun QueueTrack.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setUri(uri)
            .setMediaId(uri.toString())
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )
            .build()
}

fun collectAudioItems(items: List<AlbumItem>): List<AlbumItem> =
    items.filter { it.type == AlbumItemType.AUDIO }

fun collectImageItems(items: List<AlbumItem>): List<AlbumItem> =
    items.filter { it.type == AlbumItemType.IMAGE }
