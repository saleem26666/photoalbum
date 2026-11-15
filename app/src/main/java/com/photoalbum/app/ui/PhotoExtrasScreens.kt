package com.photoalbum.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.photoalbum.app.data.AlbumItem
import com.photoalbum.app.data.PhotoSongLink
import com.photoalbum.app.media.PlaybackManager
import com.photoalbum.app.media.QueueTrack

@Composable
fun PhotoViewerScreen(
    photo: AlbumItem,
    link: PhotoSongLink?,
    playback: PlaybackManager,
    onClose: () -> Unit,
    onLinkSong: () -> Unit,
    onUnlinkSong: () -> Unit
) {
    val playState by playback.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)

    // Soft-play linked song when opening photo
    LaunchedEffect(photo.uri, link?.songUri) {
        if (link != null) {
            playback.playTracks(
                tracks = listOf(
                    QueueTrack(uri = link.songUri, title = link.songTitle)
                ),
                playlistTitle = "Photo song · ${photo.name}"
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Keep music playing on exit if user wants ambient; pause only if it was photo-song only
            // Leave playback as-is so mini player can continue.
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(16.dp)
        ) {
            Text(
                photo.name,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            if (link != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Linked song", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
                        Text(link.songTitle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { playback.togglePlayPause() }) {
                        Icon(
                            if (playState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onUnlinkSong) {
                        Icon(Icons.Default.LinkOff, contentDescription = "Unlink", tint = Color.White)
                    }
                }
            } else {
                TextButton(onClick = onLinkSong) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Link a song to this photo", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun LinkSongDialog(
    photoName: String,
    tracks: List<AlbumItem>,
    onPick: (AlbumItem) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link song") },
        text = {
            Column {
                Text(
                    "Choose music for \"$photoName\". Opening the photo will play it.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                if (tracks.isEmpty()) {
                    Text(
                        "No music in album yet. Copy/share songs into a category first.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(280.dp)) {
                        items(tracks, key = { it.uri.toString() }) { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onPick(track) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    track.name,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
