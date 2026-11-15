package com.photoalbum.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.photoalbum.app.data.MediaSearchFilter
import com.photoalbum.app.data.SearchHit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.photoalbum.app.PhotoAlbumApplication
import com.photoalbum.app.data.AlbumItem
import com.photoalbum.app.data.AlbumItemType
import com.photoalbum.app.data.IncomingShare
import com.photoalbum.app.data.NameConflictAction
import com.photoalbum.app.data.NameConflictPrompt
import com.photoalbum.app.media.collectAudioItems
import com.photoalbum.app.media.collectImageItems
import com.photoalbum.app.media.toQueueTrack
import com.photoalbum.app.ui.player.MemoryReelScreen
import com.photoalbum.app.ui.player.MiniPlayerBar
import com.photoalbum.app.ui.player.MusicPlayerSheet
import com.photoalbum.app.ui.player.VideoPlayerScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumApp(viewModel: AlbumViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playback = remember {
        (context.applicationContext as PhotoAlbumApplication).playback
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var categoryName by remember { mutableStateOf("") }
    var showMusicSheet by remember { mutableStateOf(false) }
    var showMemoryReel by remember { mutableStateOf(false) }
    var videoItem by remember { mutableStateOf<AlbumItem?>(null) }

    fun toast(msg: String) {
        scope.launch { snackbar.showSnackbar(msg) }
    }

    fun playFolderMusic(shuffle: Boolean = false) {
        val audio = collectAudioItems(state.items)
        if (audio.isEmpty()) {
            toast("No music files in this category")
            return
        }
        playback.playTracks(
            tracks = audio.map { it.toQueueTrack() },
            playlistTitle = "Folder · ${state.currentFolder?.title ?: "Albums"}",
            shuffle = shuffle
        )
        toast("Playing ${audio.size} track(s)")
    }

    fun startMemoryReel() {
        val photos = collectImageItems(state.items)
        val audio = collectAudioItems(state.items)
        if (photos.isEmpty()) {
            toast("Add photos to this category for Memory Reel")
            return
        }
        playback.startMemoryReel(
            photos = photos,
            audio = audio,
            folderName = state.currentFolder?.title ?: "Albums"
        )
        state.currentFolder?.let { viewModel.markReelSaved(it.uri, it.title) }
        showMemoryReel = true
    }

    val pickTree = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
            }
            viewModel.onAlbumRootPicked(uri)
        }
    }

    val galleryDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onGalleryDeleteResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(state.pendingGalleryDelete) {
        val pending = state.pendingGalleryDelete
        if (pending.isEmpty()) return@LaunchedEffect
        val uris = viewModel.consumePendingGalleryDelete()
        val request = viewModel.createGalleryDeleteRequest(uris)
        if (request != null) {
            galleryDeleteLauncher.launch(
                IntentSenderRequest.Builder(request.intentSender).build()
            )
        } else {
            viewModel.onGalleryDeleteResult(false)
        }
    }

    LaunchedEffect(state.advancedBusyMessage) {
        val msg = state.advancedBusyMessage ?: return@LaunchedEffect
        when {
            msg == "PLAY_ALL" -> {
                val tracks = viewModel.consumePlayAllTracks()
                if (tracks.isNotEmpty()) {
                    playback.playTracks(
                        tracks = tracks.map { it.toQueueTrack() },
                        playlistTitle = "Play all · ${state.currentFolder?.title ?: "Album"}",
                        shuffle = false
                    )
                    showMusicSheet = true
                }
                viewModel.clearAdvancedBusy()
            }
            msg.startsWith("RESUME_REEL:") -> {
                val payload = msg.removePrefix("RESUME_REEL:")
                val sep = payload.indexOf('|')
                viewModel.clearAdvancedBusy()
                if (sep > 0) {
                    val folderUri = Uri.parse(payload.substring(0, sep))
                    val title = payload.substring(sep + 1)
                    toast("Resuming Memory Reel: $title")
                    viewModel.openFolder(AlbumItem(folderUri, title, null, AlbumItemType.FOLDER))
                    scope.launch {
                        kotlinx.coroutines.delay(600)
                        startMemoryReel()
                    }
                }
            }
        }
    }

    LaunchedEffect(state.crossfade) {
        playback.setCrossfadeEnabled(state.crossfade)
    }
    LaunchedEffect(state.nightMode) {
        playback.setNightCapsule(state.nightMode)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    BackHandler(enabled = state.viewingPhoto != null || state.searchOpen || videoItem != null || showMemoryReel || state.selectionMode || state.breadcrumbs.size > 1) {
        when {
            state.viewingPhoto != null -> viewModel.closePhoto()
            state.searchOpen -> viewModel.closeSearch()
            videoItem != null -> videoItem = null
            showMemoryReel -> {
                playback.stopMemoryReel()
                showMemoryReel = false
            }
            state.selectionMode -> viewModel.clearSelection()
            else -> viewModel.goUp()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            state.searchOpen -> "Search media"
                            state.selectionMode -> "${state.selected.size} selected"
                            else -> state.currentFolder?.title ?: "Photo Album"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (state.searchOpen || state.selectionMode || state.breadcrumbs.size > 1) {
                        IconButton(
                            onClick = {
                                when {
                                    state.searchOpen -> viewModel.closeSearch()
                                    state.selectionMode -> viewModel.clearSelection()
                                    else -> viewModel.goUp()
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (state.selectionMode) {
                        IconButton(onClick = viewModel::selectAll) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(onClick = viewModel::copySelected) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                        IconButton(onClick = viewModel::cutSelected) {
                            Icon(Icons.Default.ContentCut, contentDescription = "Cut")
                        }
                        IconButton(
                            onClick = {
                                val uris = viewModel.selectedShareUris()
                                val mimes = state.items
                                    .filter { it.uri in state.selected }
                                    .map { it.mimeType }
                                val ok = ShareHelper.shareToWhatsApp(context, uris, mimes)
                                if (!ok) {
                                    ShareHelper.shareItems(context, uris, mimes)
                                }
                                viewModel.clearSelection()
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = viewModel::deleteSelected) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    } else {
                        if (state.clipboard != null) {
                            IconButton(onClick = viewModel::pasteClipboard) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                            }
                        }
                        if (state.rootUri != null) {
                            IconButton(onClick = { viewModel.openSearch(MediaSearchFilter.MUSIC) }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Hide vault from Gallery") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.hideVaultFromGallery()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Storage, contentDescription = null)
                                },
                                enabled = state.rootUri != null
                            )
                            DropdownMenuItem(
                                text = { Text("Choose album location") },
                                onClick = {
                                    menuExpanded = false
                                    pickTree.launch(null)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Storage, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("New category") },
                                onClick = {
                                    menuExpanded = false
                                    categoryName = ""
                                    viewModel.setShowCreateDialog(true)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                                },
                                enabled = state.rootUri != null
                            )
                            DropdownMenuItem(
                                text = { Text("Scan text in folder (OCR)") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.scanOcrInCurrentFolder()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Description, contentDescription = null)
                                },
                                enabled = state.rootUri != null && !state.ocrScanning
                            )
                            DropdownMenuItem(
                                text = { Text("Search music / video") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.openSearch(MediaSearchFilter.MUSIC)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                enabled = state.rootUri != null
                            )
                            DropdownMenuItem(
                                text = { Text("Play all music (nested)") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.preparePlayAllMusic()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.LibraryMusic, contentDescription = null)
                                },
                                enabled = state.rootUri != null
                            )
                            DropdownMenuItem(
                                text = { Text("Storage map") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.openStorageMap()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Storage, contentDescription = null)
                                },
                                enabled = state.rootUri != null
                            )
                            DropdownMenuItem(
                                text = { Text("Find duplicates") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.openDuplicates()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                },
                                enabled = state.rootUri != null
                            )
                            DropdownMenuItem(
                                text = { Text("Scan faces / People album") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.scanFaces()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                },
                                enabled = state.rootUri != null
                            )
                            DropdownMenuItem(
                                text = { Text("Open People album") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.openFacesAlbum()
                                },
                                enabled = state.rootUri != null
                            )
                            DropdownMenuItem(
                                text = { Text("Lock this category (PIN)") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.lockCurrentFolder()
                                },
                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null)
                                },
                                enabled = state.rootUri != null
                            )
                            DropdownMenuItem(
                                text = { Text("Folder note / Trip capsule") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.setShowFolderNoteEditor(true)
                                },
                                enabled = state.rootUri != null
                            )
                            DropdownMenuItem(
                                text = { Text("Resume last Memory Reel") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.resumeLastMemoryReelRequest()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Vault settings") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.setShowVaultSettings(true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Play folder music") },
                                onClick = {
                                    menuExpanded = false
                                    playFolderMusic(shuffle = false)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.LibraryMusic, contentDescription = null)
                                },
                                enabled = state.rootUri != null
                            )
                            DropdownMenuItem(
                                text = { Text("Shuffle folder music") },
                                onClick = {
                                    menuExpanded = false
                                    playFolderMusic(shuffle = true)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.MusicNote, contentDescription = null)
                                },
                                enabled = state.rootUri != null
                            )
                            DropdownMenuItem(
                                text = { Text("Memory Reel (photos + music)") },
                                onClick = {
                                    menuExpanded = false
                                    startMemoryReel()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                },
                                enabled = state.rootUri != null
                            )
                            DropdownMenuItem(
                                text = { Text("Share selected via…") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.selectAll()
                                },
                                enabled = state.items.any { it.type != AlbumItemType.FOLDER }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        },
        floatingActionButton = {
            if (state.rootUri != null && !state.selectionMode && !state.searchOpen) {
                FloatingActionButton(
                    onClick = {
                        categoryName = ""
                        viewModel.setShowCreateDialog(true)
                    }
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "New category")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        if (state.nightMode) {
                            listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                            )
                        } else {
                            listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        }
                    )
                )
                .padding(padding)
        ) {
            when {
                state.rootUri == null -> SetupScreen(onChooseLocation = { pickTree.launch(null) })
                state.searchOpen -> SearchPanel(
                    state = state,
                    onQuery = viewModel::setSearchQuery,
                    onFilter = viewModel::setSearchFilter,
                    onWholeAlbum = viewModel::setSearchWholeAlbum,
                    onClose = viewModel::closeSearch,
                    onOpenHit = { hit ->
                        val item = hit.item
                        when (item.type) {
                            AlbumItemType.FOLDER -> {
                                viewModel.closeSearch()
                                viewModel.openFolder(item)
                            }
                            AlbumItemType.IMAGE -> {
                                viewModel.closeSearch()
                                viewModel.openPhoto(item)
                            }
                            AlbumItemType.AUDIO -> {
                                val audioHits = state.searchHits
                                    .map { it.item }
                                    .filter { it.type == AlbumItemType.AUDIO }
                                playback.playAlbumItems(
                                    items = if (audioHits.isNotEmpty()) audioHits else listOf(item),
                                    start = item,
                                    playlistTitle = "Search results"
                                )
                                showMusicSheet = true
                            }
                            AlbumItemType.VIDEO -> {
                                playback.player.pause()
                                videoItem = item
                            }
                            else -> Unit
                        }
                    }
                )
                else -> FolderContent(
                    state = state,
                    onOpenFolder = viewModel::openFolder,
                    onToggle = viewModel::toggleSelection,
                    onBreadcrumb = viewModel::navigateToBreadcrumb,
                    onOpenMedia = { item ->
                        when (item.type) {
                            AlbumItemType.IMAGE -> viewModel.openPhoto(item)
                            AlbumItemType.VIDEO -> {
                                playback.player.pause()
                                videoItem = item
                            }
                            AlbumItemType.AUDIO -> {
                                playback.playAlbumItems(
                                    items = state.items,
                                    start = item,
                                    playlistTitle = state.currentFolder?.title ?: "Music"
                                )
                                showMusicSheet = true
                            }
                            else -> Unit
                        }
                    },
                    onShareWhatsApp = { item ->
                        ShareHelper.shareToWhatsApp(
                            context,
                            listOf(item.uri),
                            listOf(item.mimeType)
                        ) || ShareHelper.shareItems(
                            context,
                            listOf(item.uri),
                            listOf(item.mimeType)
                        )
                    },
                    onShareAny = { item ->
                        ShareHelper.shareItems(
                            context,
                            listOf(item.uri),
                            listOf(item.mimeType)
                        )
                    }
                )
            }

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            MiniPlayerBar(
                playback = playback,
                onExpand = { showMusicSheet = true },
                onOpenReel = { showMemoryReel = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            )
        }
    }

    if (showMusicSheet) {
        MusicPlayerSheet(
            playback = playback,
            onDismiss = { showMusicSheet = false }
        )
    }

    if (showMemoryReel) {
        MemoryReelScreen(
            playback = playback,
            onClose = { showMemoryReel = false }
        )
    }

    videoItem?.let { item ->
        VideoPlayerScreen(
            item = item,
            onClose = { videoItem = null }
        )
    }

    state.viewingPhoto?.let { photo ->
        PhotoViewerScreen(
            photo = photo,
            link = state.songLinks[photo.uri.toString()],
            playback = playback,
            onClose = viewModel::closePhoto,
            onLinkSong = { viewModel.beginLinkSong(photo) },
            onUnlinkSong = { viewModel.unlinkSong(photo.uri) }
        )
    }

    state.linkSongForPhoto?.let { photo ->
        LinkSongDialog(
            photoName = photo.name,
            tracks = state.songPickerTracks,
            onPick = viewModel::confirmLinkSong,
            onDismiss = viewModel::cancelLinkSong
        )
    }

    if (state.ocrScanning) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Scanning text (OCR)") },
            text = { Text(state.ocrProgress ?: "Working…") },
            confirmButton = {}
        )
    }

    if (state.faceScanning || state.advancedBusyMessage?.startsWith("Faces") == true) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Scanning faces") },
            text = { Text(state.advancedBusyMessage ?: "Working…") },
            confirmButton = {}
        )
    }

    if (state.showVaultSettings) {
        VaultSettingsDialog(
            preferInbox = state.preferInbox,
            crossfade = state.crossfade,
            nightMode = state.nightMode,
            hasPin = state.hasPin,
            onPreferInbox = viewModel::setPreferInbox,
            onCrossfade = {
                viewModel.setCrossfadePref(it)
                playback.setCrossfadeEnabled(it)
            },
            onNightMode = {
                viewModel.setNightModePref(it)
                playback.setNightCapsule(it)
            },
            onSetPin = {
                viewModel.setShowVaultSettings(false)
                viewModel.setShowPinSetup(true)
            },
            onClearPin = viewModel::clearPin,
            onDismiss = { viewModel.setShowVaultSettings(false) }
        )
    }

    if (state.showPinSetup) {
        PinDialog(
            title = "Set vault PIN",
            onConfirm = viewModel::setupPin,
            onDismiss = { viewModel.setShowPinSetup(false) }
        )
    }

    if (state.pinUnlockFolderUri != null) {
        PinDialog(
            title = "Unlock private category",
            onConfirm = viewModel::unlockFolderWithPin,
            onDismiss = viewModel::dismissPinUnlock
        )
    }

    if (state.showFolderNoteEditor) {
        val folder = state.currentFolder
        FolderNoteDialog(
            existing = folder?.let { state.folderNotes[it.uri.toString()] },
            folderTitle = folder?.title ?: "",
            onSave = viewModel::saveFolderNote,
            onDismiss = { viewModel.setShowFolderNoteEditor(false) }
        )
    }

    if (state.showStorage) {
        StorageHealthScreen(
            stats = state.storageStats,
            onBack = viewModel::closeStorage
        )
    }

    if (state.showDuplicates) {
        DuplicatesScreen(
            groups = state.duplicateGroups,
            onBack = viewModel::closeDuplicates,
            onDelete = viewModel::deleteDuplicate
        )
    }

    if (state.showFaces) {
        FacesScreen(
            faces = state.faceHits,
            onBack = viewModel::closeFaces,
            onOpen = { hit ->
                viewModel.closeFaces()
                viewModel.openPhoto(hit.item)
            }
        )
    }

    // Night Capsule uses dimmed background gradient above (no blocking overlay).

    if (state.showCreateDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowCreateDialog(false) },
            title = { Text("New category") },
            text = {
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    singleLine = true,
                    label = { Text("Name (e.g. Family / Ali)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.createCategory(categoryName) }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowCreateDialog(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (state.conflictPrompt != null) {
        ConflictDialog(
            prompt = state.conflictPrompt!!,
            onSkip = { viewModel.resolveConflict(NameConflictAction.SKIP) },
            onReplace = { viewModel.resolveConflict(NameConflictAction.REPLACE) },
            onKeepBoth = { viewModel.resolveConflict(NameConflictAction.KEEP_BOTH) },
            onCancel = viewModel::dismissConflict
        )
    }

    if (state.showImportPicker && state.incomingShare != null) {
        var newCategoryName by remember { mutableStateOf("") }
        ImportCategoryDialog(
            share = state.incomingShare!!,
            hasAlbum = state.rootUri != null,
            folders = state.importFolders,
            currentFolderTitle = state.importFolder?.title ?: "Albums",
            breadcrumbs = state.importBreadcrumbs,
            atAlbumRoot = state.rootUri != null && state.importFolder?.uri == state.rootUri,
            showCreateCategory = state.showImportCreateCategory,
            newCategoryName = newCategoryName,
            onNewCategoryNameChange = { newCategoryName = it },
            onChooseLocationFirst = {
                viewModel.cancelImport()
                pickTree.launch(null)
            },
            onOpenFolder = viewModel::openImportFolder,
            onBreadcrumb = viewModel::navigateImportBreadcrumb,
            onShowCreateCategory = {
                newCategoryName = ""
                viewModel.setShowImportCreateCategory(true)
            },
            onDismissCreateCategory = { viewModel.setShowImportCreateCategory(false) },
            onCreateCategory = { viewModel.createImportCategory(newCategoryName) },
            onSaveCopyInto = viewModel::importCopyInto,
            onSaveCutInto = viewModel::importCutInto,
            onCopyHere = viewModel::importCopyToSelectedCategory,
            onCutHere = viewModel::importCutToSelectedCategory,
            onCancel = viewModel::cancelImport
        )
    }
}

@Composable
private fun SearchPanel(
    state: AlbumUiState,
    onQuery: (String) -> Unit,
    onFilter: (MediaSearchFilter) -> Unit,
    onWholeAlbum: (Boolean) -> Unit,
    onClose: () -> Unit,
    onOpenHit: (SearchHit) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Auto search music, video, files…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MediaSearchFilter.entries.toList()) { filter ->
                FilterChip(
                    selected = state.searchFilter == filter,
                    onClick = { onFilter(filter) },
                    label = { Text(filter.label()) }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Search entire album",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.searchWholeAlbum,
                onCheckedChange = onWholeAlbum
            )
        }
        Text(
            text = when {
                state.searchLoading -> "Searching…"
                state.searchQuery.isBlank() &&
                    (state.searchFilter == MediaSearchFilter.MUSIC ||
                        state.searchFilter == MediaSearchFilter.VIDEO) ->
                    "Showing all ${state.searchFilter.label().lowercase()} files (${state.searchHits.size})"
                state.searchQuery.isNotBlank() ->
                    "${state.searchHits.size} result(s) · includes OCR text matches"
                else -> "${state.searchHits.size} result(s)"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        if (state.searchLoading && state.searchHits.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.searchHits.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No matches. Try Music/Video chip or another word.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(state.searchHits, key = { it.item.uri.toString() + it.folderPath }) { hit ->
                    SearchHitRow(hit = hit, onClick = { onOpenHit(hit) })
                }
            }
        }
    }
}

@Composable
private fun SearchHitRow(hit: SearchHit, onClick: () -> Unit) {
    val item = hit.item
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when (item.type) {
                AlbumItemType.IMAGE -> AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                AlbumItemType.VIDEO -> VideoThumbnail(
                    uri = item.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    showBadge = false
                )
                AlbumItemType.AUDIO -> Icon(
                    Icons.Default.MusicNote,
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
                AlbumItemType.FOLDER -> Icon(
                    Icons.Default.Folder,
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
                AlbumItemType.PDF -> Icon(
                    Icons.Default.PictureAsPdf,
                    null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                else -> Icon(Icons.Default.Description, null)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(item.type.name.lowercase().replaceFirstChar { it.titlecase() })
                    if (hit.matchReason == "text inside file") append(" · found in text")
                    if (hit.folderPath.isNotBlank()) append(" · ${hit.folderPath}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.type == AlbumItemType.AUDIO || item.type == AlbumItemType.VIDEO) {
            Icon(
                Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SetupScreen(onChooseLocation: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Photo Album",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Pick a folder on your phone or SD card. Create categories, then copy, move, and share photos or documents — including to WhatsApp.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(28.dp))
        TextButton(onClick = onChooseLocation) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Choose album location")
        }
    }
}

@Composable
private fun FolderContent(
    state: AlbumUiState,
    onOpenFolder: (AlbumItem) -> Unit,
    onToggle: (Uri) -> Unit,
    onBreadcrumb: (Int) -> Unit,
    onOpenMedia: (AlbumItem) -> Unit,
    onShareWhatsApp: (AlbumItem) -> Unit,
    onShareAny: (AlbumItem) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        if (state.breadcrumbs.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(state.breadcrumbs) { index, crumb ->
                    Text(
                        text = if (index == state.breadcrumbs.lastIndex) crumb.title
                        else "${crumb.title} /",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (index == state.breadcrumbs.lastIndex)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onBreadcrumb(index) }
                    )
                }
            }
        }

        if (state.items.isEmpty() && !state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "This category is empty",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "Create a sub-category or paste files here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.items, key = { it.uri.toString() }) { item ->
                    AlbumItemCard(
                        item = item,
                        selected = item.uri in state.selected,
                        selectionMode = state.selectionMode,
                        hasSongLink = state.songLinks.containsKey(item.uri.toString()),
                        isLocked = item.uri.toString() in state.lockedFolders,
                        folderNoteKind = state.folderNotes[item.uri.toString()]?.kind,
                        onOpen = {
                            when (item.type) {
                                AlbumItemType.FOLDER -> onOpenFolder(item)
                                AlbumItemType.AUDIO,
                                AlbumItemType.VIDEO,
                                AlbumItemType.IMAGE -> onOpenMedia(item)
                                else -> Unit
                            }
                        },
                        onToggle = { onToggle(item.uri) },
                        onShareWhatsApp = { onShareWhatsApp(item) },
                        onShareAny = { onShareAny(item) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumItemCard(
    item: AlbumItem,
    selected: Boolean,
    selectionMode: Boolean,
    hasSongLink: Boolean = false,
    isLocked: Boolean = false,
    folderNoteKind: String? = null,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onShareAny: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
            )
            .combinedClickable(
                onClick = {
                    when {
                        selectionMode -> onToggle()
                        item.type == AlbumItemType.FOLDER ||
                            item.type == AlbumItemType.AUDIO ||
                            item.type == AlbumItemType.VIDEO ||
                            item.type == AlbumItemType.IMAGE -> onOpen()
                        else -> showMenu = true
                    }
                },
                onLongClick = onToggle
            )
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when (item.type) {
                AlbumItemType.FOLDER -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    if (isLocked) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.tertiary)
                                .padding(4.dp)
                                .size(14.dp)
                        )
                    }
                    if (folderNoteKind != null) {
                        Text(
                            folderNoteKind.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                AlbumItemType.IMAGE -> Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (hasSongLink) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = "Has linked song",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                                .padding(5.dp)
                                .size(16.dp)
                        )
                    }
                }
                AlbumItemType.VIDEO -> VideoThumbnail(
                    uri = item.uri,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    showBadge = true
                )
                AlbumItemType.AUDIO -> Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                AlbumItemType.PDF -> Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                else -> Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                )
            }

            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (item.type == AlbumItemType.IMAGE) {
                    DropdownMenuItem(
                        text = { Text("Open photo") },
                        onClick = {
                            showMenu = false
                            onOpen()
                        }
                    )
                }
                if (item.type == AlbumItemType.AUDIO || item.type == AlbumItemType.VIDEO) {
                    DropdownMenuItem(
                        text = { Text(if (item.type == AlbumItemType.AUDIO) "Play" else "Play video") },
                        onClick = {
                            showMenu = false
                            onOpen()
                        },
                        leadingIcon = {
                            Icon(
                                if (item.type == AlbumItemType.AUDIO) Icons.Default.MusicNote
                                else Icons.Default.Videocam,
                                contentDescription = null
                            )
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Share to WhatsApp") },
                    onClick = {
                        showMenu = false
                        onShareWhatsApp()
                    },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Share via…") },
                    onClick = {
                        showMenu = false
                        onShareAny()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Select") },
                    onClick = {
                        showMenu = false
                        onToggle()
                    }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ConflictDialog(
    prompt: NameConflictPrompt,
    onSkip: () -> Unit,
    onReplace: () -> Unit,
    onKeepBoth: () -> Unit,
    onCancel: () -> Unit
) {
    val preview = prompt.conflictNames.take(5).joinToString("\n")
    val more = (prompt.conflictNames.size - 5).coerceAtLeast(0)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Already exists") },
        text = {
            Column {
                Text(
                    "${prompt.conflictNames.size} file(s) already in \"${prompt.destinationTitle}\":",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Text(preview, style = MaterialTheme.typography.bodyMedium)
                if (more > 0) {
                    Text("…and $more more", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Skip = leave existing · Replace = overwrite · Keep both = add (1) copy",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Column {
                TextButton(onClick = onSkip) { Text("Skip existing") }
                TextButton(onClick = onReplace) { Text("Replace") }
                TextButton(onClick = onKeepBoth) { Text("Keep both (1)") }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}

@Composable
private fun ImportCategoryDialog(
    share: IncomingShare,
    hasAlbum: Boolean,
    folders: List<AlbumItem>,
    currentFolderTitle: String,
    breadcrumbs: List<FolderNav>,
    atAlbumRoot: Boolean,
    showCreateCategory: Boolean,
    newCategoryName: String,
    onNewCategoryNameChange: (String) -> Unit,
    onChooseLocationFirst: () -> Unit,
    onOpenFolder: (AlbumItem) -> Unit,
    onBreadcrumb: (Int) -> Unit,
    onShowCreateCategory: () -> Unit,
    onDismissCreateCategory: () -> Unit,
    onCreateCategory: () -> Unit,
    onSaveCopyInto: (AlbumItem) -> Unit,
    onSaveCutInto: (AlbumItem) -> Unit,
    onCopyHere: () -> Unit,
    onCutHere: () -> Unit,
    onCancel: () -> Unit
) {
    val count = share.count()

    if (!hasAlbum) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Save to Photo Album") },
            text = {
                Text("Choose your album location on SD card or phone storage first, then share again.")
            },
            confirmButton = {
                TextButton(onClick = onChooseLocationFirst) { Text("Choose location") }
            },
            dismissButton = {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        )
        return
    }

    if (showCreateCategory) {
        AlertDialog(
            onDismissRequest = onDismissCreateCategory,
            title = { Text("New category") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = onNewCategoryNameChange,
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = onCreateCategory) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = onDismissCreateCategory) { Text("Cancel") }
            }
        )
        return
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onCancel,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Where to save?",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
            Text(
                "$count file(s) from Gallery / Files. Tap a category → Save here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            if (breadcrumbs.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(breadcrumbs) { index, crumb ->
                        Text(
                            text = if (index == breadcrumbs.lastIndex) crumb.title
                            else "${crumb.title} /",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (index == breadcrumbs.lastIndex)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onBreadcrumb(index) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Text(
                text = if (atAlbumRoot) {
                    "Choose a category (not album root)"
                } else {
                    "Inside: $currentFolderTitle"
                },
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onShowCreateCategory) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New category here")
            }

            Spacer(Modifier.height(8.dp))

            if (folders.isEmpty()) {
                Text(
                    if (atAlbumRoot) {
                        "No categories yet. Create one (e.g. Family), then Save here."
                    } else {
                        "No sub-categories. You can Copy/Cut into \"$currentFolderTitle\"."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(folders, key = { it.uri.toString() }) { folder ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenFolder(folder) }
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    folder.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "Open",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onSaveCopyInto(folder) }) {
                                    Icon(Icons.Default.ContentCopy, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Save here")
                                }
                                TextButton(onClick = { onSaveCutInto(folder) }) {
                                    Icon(Icons.Default.ContentCut, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Cut here")
                                }
                            }
                        }
                    }
                }
            }

            if (!atAlbumRoot || folders.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCopyHere) {
                        Icon(Icons.Default.ContentCopy, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Copy into $currentFolderTitle")
                    }
                    TextButton(onClick = onCutHere) {
                        Icon(Icons.Default.ContentCut, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Cut into $currentFolderTitle")
                    }
                }
            }
        }
    }
}
