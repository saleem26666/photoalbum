package com.photoalbum.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photoalbum.app.data.AlbumClipboard
import com.photoalbum.app.data.AlbumItem
import com.photoalbum.app.data.AlbumItemType
import com.photoalbum.app.data.AlbumPreferences
import com.photoalbum.app.data.AlbumRepository
import com.photoalbum.app.data.ClipboardMode
import com.photoalbum.app.data.DuplicateGroup
import com.photoalbum.app.data.FaceIndexer
import com.photoalbum.app.data.FacePhotoHit
import com.photoalbum.app.data.FolderNote
import com.photoalbum.app.data.IncomingShare
import com.photoalbum.app.data.MediaSearchFilter
import com.photoalbum.app.data.NameConflictAction
import com.photoalbum.app.data.NameConflictPrompt
import com.photoalbum.app.data.OcrIndexer
import com.photoalbum.app.data.PendingTransferKind
import com.photoalbum.app.data.PhotoSongLink
import com.photoalbum.app.data.SearchHit
import com.photoalbum.app.data.StorageStat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FolderNav(
    val uri: Uri,
    val title: String
)

data class AlbumUiState(
    val rootUri: Uri? = null,
    val currentFolder: FolderNav? = null,
    val breadcrumbs: List<FolderNav> = emptyList(),
    val items: List<AlbumItem> = emptyList(),
    val selected: Set<Uri> = emptySet(),
    val clipboard: AlbumClipboard? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
    val showCreateDialog: Boolean = false,
    val showImportPicker: Boolean = false,
    val incomingShare: IncomingShare? = null,
    val importFolders: List<AlbumItem> = emptyList(),
    val importFolder: FolderNav? = null,
    val importBreadcrumbs: List<FolderNav> = emptyList(),
    val showImportCreateCategory: Boolean = false,
    val selectionMode: Boolean = false,
    val conflictPrompt: NameConflictPrompt? = null,
    val pendingGalleryDelete: List<Uri> = emptyList(),
    val searchOpen: Boolean = false,
    val searchQuery: String = "",
    val searchFilter: MediaSearchFilter = MediaSearchFilter.MUSIC,
    val searchWholeAlbum: Boolean = true,
    val searchHits: List<SearchHit> = emptyList(),
    val searchLoading: Boolean = false,
    val songLinks: Map<String, PhotoSongLink> = emptyMap(),
    val viewingPhoto: AlbumItem? = null,
    val linkSongForPhoto: AlbumItem? = null,
    val songPickerTracks: List<AlbumItem> = emptyList(),
    val ocrScanning: Boolean = false,
    val ocrProgress: String? = null,
    val lockedFolders: Set<String> = emptySet(),
    val unlockedSession: Set<String> = emptySet(),
    val preferInbox: Boolean = true,
    val crossfade: Boolean = true,
    val nightMode: Boolean = false,
    val hasPin: Boolean = false,
    val folderNotes: Map<String, FolderNote> = emptyMap(),
    val showVaultSettings: Boolean = false,
    val showPinSetup: Boolean = false,
    val pinUnlockFolderUri: Uri? = null,
    val showFolderNoteEditor: Boolean = false,
    val showStorage: Boolean = false,
    val storageStats: List<StorageStat> = emptyList(),
    val showDuplicates: Boolean = false,
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val showFaces: Boolean = false,
    val faceHits: List<FacePhotoHit> = emptyList(),
    val faceScanning: Boolean = false,
    val advancedBusyMessage: String? = null
)

class AlbumViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = AlbumPreferences(application)
    private val repository = AlbumRepository(application)
    private val ocrIndexer = OcrIndexer(application, prefs)
    private val faceIndexer = FaceIndexer(application, prefs)
    private var searchJob: Job? = null

    private val _state = MutableStateFlow(AlbumUiState())
    val state: StateFlow<AlbumUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.albumRootUri.collect { uri ->
                _state.update { it.copy(rootUri = uri) }
                if (uri != null && _state.value.currentFolder == null) {
                    openRoot(uri)
                } else if (uri == null) {
                    _state.update {
                        it.copy(
                            currentFolder = null,
                            breadcrumbs = emptyList(),
                            items = emptyList(),
                            selected = emptySet()
                        )
                    }
                }
                if (uri != null && _state.value.incomingShare != null) {
                    prepareImportBrowser()
                }
            }
        }
        viewModelScope.launch {
            prefs.songLinks.collect { links ->
                _state.update { it.copy(songLinks = links) }
            }
        }
        viewModelScope.launch {
            prefs.lockedFolders.collect { locked ->
                _state.update { it.copy(lockedFolders = locked) }
            }
        }
        viewModelScope.launch {
            prefs.preferInboxOnShare.collect { v ->
                _state.update { it.copy(preferInbox = v) }
            }
        }
        viewModelScope.launch {
            prefs.crossfadeEnabled.collect { v ->
                _state.update { it.copy(crossfade = v) }
            }
        }
        viewModelScope.launch {
            prefs.nightMode.collect { v ->
                _state.update { it.copy(nightMode = v) }
            }
        }
        viewModelScope.launch {
            prefs.folderNotes.collect { notes ->
                _state.update { it.copy(folderNotes = notes) }
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(hasPin = prefs.hasPin()) }
        }
    }

    fun onAlbumRootPicked(uri: Uri) {
        viewModelScope.launch {
            repository.takePersistablePermission(uri)
            prefs.setAlbumRootUri(uri)
            repository.hideAlbumTreeFromGallery(uri)
            openRoot(uri)
            _state.update {
                it.copy(
                    message = "Album location saved · hidden from Gallery (.nomedia)"
                )
            }
        }
    }

    /** Re-apply .nomedia on album + categories so Gallery stops mixing SD vault photos. */
    fun hideVaultFromGallery() {
        val root = _state.value.rootUri ?: return
        viewModelScope.launch {
            repository.hideAlbumTreeFromGallery(root)
            _state.update {
                it.copy(
                    message = "Vault hidden from Gallery. Restart Gallery / reboot if old thumbnails remain."
                )
            }
        }
    }

    fun openRoot(uri: Uri) {
        val nav = FolderNav(uri, "Albums")
        _state.update {
            it.copy(
                currentFolder = nav,
                breadcrumbs = listOf(nav),
                selected = emptySet(),
                selectionMode = false
            )
        }
        refresh()
    }

    fun openFolder(item: AlbumItem) {
        if (item.type != AlbumItemType.FOLDER) return
        val key = item.uri.toString()
        if (key in _state.value.lockedFolders && key !in _state.value.unlockedSession) {
            _state.update { it.copy(pinUnlockFolderUri = item.uri) }
            return
        }
        val nav = FolderNav(item.uri, item.name)
        _state.update {
            it.copy(
                currentFolder = nav,
                breadcrumbs = it.breadcrumbs + nav,
                selected = emptySet(),
                selectionMode = false
            )
        }
        refresh()
    }

    fun navigateToBreadcrumb(index: Int) {
        val crumbs = _state.value.breadcrumbs.take(index + 1)
        val target = crumbs.lastOrNull() ?: return
        _state.update {
            it.copy(
                breadcrumbs = crumbs,
                currentFolder = target,
                selected = emptySet(),
                selectionMode = false
            )
        }
        refresh()
    }

    fun goUp(): Boolean {
        val crumbs = _state.value.breadcrumbs
        if (crumbs.size <= 1) return false
        navigateToBreadcrumb(crumbs.lastIndex - 1)
        return true
    }

    fun refresh() {
        val folder = _state.value.currentFolder ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val items = repository.listChildren(folder.uri)
            _state.update { it.copy(items = items, isLoading = false) }
            if (_state.value.searchOpen) scheduleSearch()
        }
    }

    fun openSearch(filter: MediaSearchFilter = MediaSearchFilter.MUSIC) {
        _state.update {
            it.copy(
                searchOpen = true,
                searchFilter = filter,
                searchQuery = "",
                selectionMode = false,
                selected = emptySet()
            )
        }
        scheduleSearch()
    }

    fun closeSearch() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                searchOpen = false,
                searchQuery = "",
                searchHits = emptyList(),
                searchLoading = false
            )
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        scheduleSearch()
    }

    fun setSearchFilter(filter: MediaSearchFilter) {
        _state.update { it.copy(searchFilter = filter) }
        scheduleSearch()
    }

    fun setSearchWholeAlbum(enabled: Boolean) {
        _state.update { it.copy(searchWholeAlbum = enabled) }
        scheduleSearch()
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        if (!_state.value.searchOpen) return
        searchJob = viewModelScope.launch {
            delay(220) // auto-search while typing
            val snapshot = _state.value
            val folderUri = if (snapshot.searchWholeAlbum) {
                snapshot.rootUri ?: snapshot.currentFolder?.uri
            } else {
                snapshot.currentFolder?.uri
            } ?: return@launch
            _state.update { it.copy(searchLoading = true) }
            val ocr = prefs.getOcrIndexOnce()
            val hits = repository.search(
                folderUri = folderUri,
                query = snapshot.searchQuery,
                filter = snapshot.searchFilter,
                recursive = true,
                ocrIndex = ocr
            )
            // Ignore stale results if query changed mid-search
            if (_state.value.searchQuery == snapshot.searchQuery &&
                _state.value.searchFilter == snapshot.searchFilter &&
                _state.value.searchWholeAlbum == snapshot.searchWholeAlbum
            ) {
                _state.update { it.copy(searchHits = hits, searchLoading = false) }
            }
        }
    }

    fun openPhoto(item: AlbumItem) {
        if (item.type != AlbumItemType.IMAGE) return
        _state.update { it.copy(viewingPhoto = item) }
    }

    fun closePhoto() {
        _state.update { it.copy(viewingPhoto = null) }
    }

    fun beginLinkSong(photo: AlbumItem) {
        val root = _state.value.rootUri ?: return
        viewModelScope.launch {
            val tracks = repository.search(
                folderUri = root,
                query = "",
                filter = MediaSearchFilter.MUSIC,
                recursive = true
            ).map { it.item }
            _state.update {
                it.copy(
                    linkSongForPhoto = photo,
                    songPickerTracks = tracks
                )
            }
        }
    }

    fun cancelLinkSong() {
        _state.update { it.copy(linkSongForPhoto = null, songPickerTracks = emptyList()) }
    }

    fun confirmLinkSong(song: AlbumItem) {
        val photo = _state.value.linkSongForPhoto ?: return
        viewModelScope.launch {
            prefs.setSongLink(photo.uri, song.uri, song.name)
            _state.update {
                it.copy(
                    linkSongForPhoto = null,
                    songPickerTracks = emptyList(),
                    message = "Linked \"${song.name}\" to photo"
                )
            }
        }
    }

    fun unlinkSong(photoUri: android.net.Uri) {
        viewModelScope.launch {
            prefs.removeSongLink(photoUri)
            _state.update { it.copy(message = "Song unlinked from photo") }
        }
    }

    fun scanOcrInCurrentFolder() {
        val items = _state.value.items
        if (items.none { it.type == AlbumItemType.IMAGE || it.type == AlbumItemType.PDF }) {
            _state.update { it.copy(message = "No photos/PDFs here to scan") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(ocrScanning = true, ocrProgress = "Starting OCR…") }
            val indexed = ocrIndexer.indexItems(items) { done, total ->
                _state.update { it.copy(ocrProgress = "OCR $done / $total") }
            }
            _state.update {
                it.copy(
                    ocrScanning = false,
                    ocrProgress = null,
                    message = "OCR indexed $indexed file(s). Search can find text inside them."
                )
            }
            if (_state.value.searchOpen) scheduleSearch()
        }
    }

    fun setShowCreateDialog(show: Boolean) {
        _state.update { it.copy(showCreateDialog = show) }
    }

    fun createCategory(name: String) {
        val folder = _state.value.currentFolder ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(message = "Enter a category name") }
            return
        }
        viewModelScope.launch {
            repository.createCategory(folder.uri, trimmed)
                .onSuccess {
                    _state.update {
                        it.copy(showCreateDialog = false, message = "Created \"$trimmed\"")
                    }
                    refresh()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(message = error.message ?: "Could not create category")
                    }
                }
        }
    }

    fun toggleSelection(uri: Uri) {
        _state.update { current ->
            val next = current.selected.toMutableSet()
            if (!next.add(uri)) next.remove(uri)
            current.copy(
                selected = next,
                selectionMode = next.isNotEmpty()
            )
        }
    }

    fun selectAll() {
        _state.update { current ->
            val all = current.items.map { it.uri }.toSet()
            current.copy(selected = all, selectionMode = all.isNotEmpty())
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptySet(), selectionMode = false) }
    }

    fun copySelected() {
        val selected = _state.value.selected
        if (selected.isEmpty()) return
        _state.update {
            it.copy(
                clipboard = AlbumClipboard(ClipboardMode.COPY, selected.toList()),
                message = "Copied ${selected.size} item(s)",
                selected = emptySet(),
                selectionMode = false
            )
        }
    }

    fun cutSelected() {
        val selected = _state.value.selected
        if (selected.isEmpty()) return
        _state.update {
            it.copy(
                clipboard = AlbumClipboard(ClipboardMode.CUT, selected.toList()),
                message = "Ready to move ${selected.size} item(s)",
                selected = emptySet(),
                selectionMode = false
            )
        }
    }

    fun pasteClipboard() {
        val folder = _state.value.currentFolder ?: return
        val clipboard = _state.value.clipboard ?: run {
            _state.update { it.copy(message = "Clipboard is empty") }
            return
        }
        viewModelScope.launch {
            val conflicts = repository.findConflictNamesForClipboard(folder.uri, clipboard)
            if (conflicts.isNotEmpty()) {
                _state.update {
                    it.copy(
                        conflictPrompt = NameConflictPrompt(
                            kind = PendingTransferKind.PASTE,
                            destinationUri = folder.uri,
                            destinationTitle = folder.title,
                            conflictNames = conflicts,
                            sourceUris = clipboard.uris,
                            clipboard = clipboard
                        )
                    )
                }
                return@launch
            }
            runPaste(folder.uri, clipboard, NameConflictAction.KEEP_BOTH)
        }
    }

    fun resolveConflict(action: NameConflictAction) {
        val prompt = _state.value.conflictPrompt ?: return
        _state.update { it.copy(conflictPrompt = null) }
        when (prompt.kind) {
            PendingTransferKind.PASTE -> {
                val clipboard = prompt.clipboard ?: return
                runPaste(prompt.destinationUri, clipboard, action)
            }
            PendingTransferKind.IMPORT_COPY -> {
                runImport(prompt.destinationUri, prompt.destinationTitle, cutAfterCopy = false, action)
            }
            PendingTransferKind.IMPORT_CUT -> {
                runImport(prompt.destinationUri, prompt.destinationTitle, cutAfterCopy = true, action)
            }
        }
    }

    fun dismissConflict() {
        _state.update { it.copy(conflictPrompt = null) }
    }

    private fun runPaste(
        folderUri: Uri,
        clipboard: AlbumClipboard,
        action: NameConflictAction
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.pasteItems(folderUri, clipboard, action)
                .onSuccess { result ->
                    val parts = mutableListOf<String>()
                    val verb = if (clipboard.mode == ClipboardMode.CUT) "Moved" else "Copied"
                    if (result.copied > 0) parts += "$verb ${result.copied}"
                    if (result.skipped > 0) parts += "skipped ${result.skipped} (already exists)"
                    if (result.replaced > 0) parts += "replaced ${result.replaced}"
                    _state.update {
                        it.copy(
                            isLoading = false,
                            clipboard = if (clipboard.mode == ClipboardMode.CUT) null else clipboard,
                            message = parts.joinToString(" · ").ifBlank { "Nothing pasted" }
                        )
                    }
                    refresh()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            message = error.message ?: "Paste failed"
                        )
                    }
                }
        }
    }

    fun deleteSelected() {
        val selected = _state.value.selected.toList()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            repository.deleteItems(selected)
                .onSuccess { count ->
                    _state.update {
                        it.copy(
                            selected = emptySet(),
                            selectionMode = false,
                            message = "Deleted $count item(s)"
                        )
                    }
                    refresh()
                }
                .onFailure { error ->
                    _state.update { it.copy(message = error.message ?: "Delete failed") }
                }
        }
    }

    fun selectedShareUris(): List<Uri> {
        return repository.prepareShareUris(_state.value.selected.toList())
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun setIncomingShare(share: IncomingShare?) {
        _state.update {
            it.copy(
                incomingShare = share,
                showImportPicker = share != null,
                showImportCreateCategory = false
            )
        }
        if (share != null) {
            prepareImportBrowser()
        }
    }

    fun cancelImport() {
        _state.update {
            it.copy(
                incomingShare = null,
                showImportPicker = false,
                importFolders = emptyList(),
                importFolder = null,
                importBreadcrumbs = emptyList(),
                showImportCreateCategory = false
            )
        }
    }

    /**
     * Always open the picker at album ROOT so the full category list is visible.
     * If "Share → Inbox" is enabled, ensure Inbox exists as one of those categories
     * (do not auto-enter Inbox — that hid other categories).
     */
    fun prepareImportBrowser() {
        val root = _state.value.rootUri
        if (root == null) {
            // Cold start from Gallery: root loads async from DataStore shortly after.
            return
        }
        viewModelScope.launch {
            if (_state.value.preferInbox) {
                repository.ensureInbox(root)
            }
            val nav = FolderNav(root, "Albums")
            _state.update {
                it.copy(
                    importFolder = nav,
                    importBreadcrumbs = listOf(nav),
                    showImportPicker = it.incomingShare != null
                )
            }
            val folders = repository.listFolders(root)
            _state.update { it.copy(importFolders = folders) }
        }
    }

    fun refreshImportFolders() {
        val folder = _state.value.importFolder ?: return
        viewModelScope.launch {
            val folders = repository.listFolders(folder.uri)
            _state.update { it.copy(importFolders = folders) }
        }
    }

    fun openImportFolder(item: AlbumItem) {
        if (item.type != AlbumItemType.FOLDER) return
        val nav = FolderNav(item.uri, item.name)
        _state.update {
            it.copy(
                importFolder = nav,
                importBreadcrumbs = it.importBreadcrumbs + nav
            )
        }
        refreshImportFolders()
    }

    /** One-tap save into a category without needing Copy on the root screen. */
    fun importCopyInto(folder: AlbumItem) {
        if (folder.type != AlbumItemType.FOLDER) return
        beginImport(folder.uri, folder.name, cutAfterCopy = false)
    }

    fun importCutInto(folder: AlbumItem) {
        if (folder.type != AlbumItemType.FOLDER) return
        beginImport(folder.uri, folder.name, cutAfterCopy = true)
    }

    fun navigateImportBreadcrumb(index: Int) {
        val crumbs = _state.value.importBreadcrumbs.take(index + 1)
        val target = crumbs.lastOrNull() ?: return
        _state.update {
            it.copy(importBreadcrumbs = crumbs, importFolder = target)
        }
        refreshImportFolders()
    }

    fun setShowImportCreateCategory(show: Boolean) {
        _state.update { it.copy(showImportCreateCategory = show) }
    }

    fun createImportCategory(name: String) {
        val folder = _state.value.importFolder ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(message = "Enter a category name") }
            return
        }
        viewModelScope.launch {
            repository.createCategory(folder.uri, trimmed)
                .onSuccess { created ->
                    _state.update { it.copy(showImportCreateCategory = false) }
                    refreshImportFolders()
                    if (_state.value.currentFolder?.uri == folder.uri) {
                        refresh()
                    }
                    _state.update { it.copy(message = "Created \"${created.name}\" — tap Save here on it") }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(message = error.message ?: "Could not create category")
                    }
                }
        }
    }

    fun importCopyToSelectedCategory() {
        val folder = _state.value.importFolder ?: return
        // Discourage accidental save on root when categories exist
        val atRoot = _state.value.rootUri != null &&
            folder.uri == _state.value.rootUri
        if (atRoot && _state.value.importFolders.isNotEmpty()) {
            _state.update {
                it.copy(message = "Select a category below, then tap Save here")
            }
            return
        }
        beginImport(folder.uri, folder.title, cutAfterCopy = false)
    }

    fun importCutToSelectedCategory() {
        val folder = _state.value.importFolder ?: return
        val atRoot = _state.value.rootUri != null &&
            folder.uri == _state.value.rootUri
        if (atRoot && _state.value.importFolders.isNotEmpty()) {
            _state.update {
                it.copy(message = "Select a category below, then tap Save here")
            }
            return
        }
        beginImport(folder.uri, folder.title, cutAfterCopy = true)
    }

    private fun beginImport(folderUri: Uri, destinationTitle: String, cutAfterCopy: Boolean) {
        val share = _state.value.incomingShare ?: return
        val uris = share.uris()
        viewModelScope.launch {
            val conflicts = repository.findConflictNamesForUris(folderUri, uris)
            if (conflicts.isNotEmpty()) {
                _state.update {
                    it.copy(
                        showImportPicker = false,
                        conflictPrompt = NameConflictPrompt(
                            kind = if (cutAfterCopy) PendingTransferKind.IMPORT_CUT
                            else PendingTransferKind.IMPORT_COPY,
                            destinationUri = folderUri,
                            destinationTitle = destinationTitle,
                            conflictNames = conflicts,
                            sourceUris = uris
                        )
                    )
                }
                return@launch
            }
            runImport(folderUri, destinationTitle, cutAfterCopy, NameConflictAction.KEEP_BOTH)
        }
    }

    private fun runImport(
        folderUri: Uri,
        destinationTitle: String,
        cutAfterCopy: Boolean,
        action: NameConflictAction
    ) {
        val share = _state.value.incomingShare ?: return
        val uris = share.uris()
        val importCrumbs = _state.value.importBreadcrumbs
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, showImportPicker = false) }
            repository.importSharedUris(
                folderUri,
                uris,
                cutAfterCopy = cutAfterCopy,
                conflictAction = action
            ).onSuccess { result ->
                val parts = mutableListOf<String>()
                when {
                    result.cutRequested && result.deleted == result.copied && result.copied > 0 ->
                        parts += "Moved ${result.copied} to \"$destinationTitle\""
                    result.cutRequested && result.copied > 0 ->
                        parts += "Saved ${result.copied} to \"$destinationTitle\""
                    result.copied > 0 ->
                        parts += "Copied ${result.copied} to \"$destinationTitle\""
                }
                if (result.skipped > 0) parts += "skipped ${result.skipped} (already exists)"
                if (result.replaced > 0) parts += "replaced ${result.replaced}"
                if (result.pendingGalleryDelete.isNotEmpty()) {
                    parts += "Confirm Gallery delete next"
                } else if (result.cutRequested && result.deleted < result.copied &&
                    result.pendingGalleryDelete.isEmpty() && result.copied > 0
                ) {
                    parts += "Gallery did not allow auto-delete"
                }

                _state.update {
                    it.copy(
                        isLoading = false,
                        incomingShare = null,
                        importFolders = emptyList(),
                        importFolder = null,
                        importBreadcrumbs = emptyList(),
                        showImportCreateCategory = false,
                        message = parts.joinToString(" · ").ifBlank { "Nothing imported" },
                        currentFolder = importCrumbs.lastOrNull()
                            ?: FolderNav(folderUri, destinationTitle),
                        breadcrumbs = importCrumbs.ifEmpty {
                            listOf(FolderNav(folderUri, destinationTitle))
                        },
                        selected = emptySet(),
                        selectionMode = false,
                        pendingGalleryDelete = result.pendingGalleryDelete
                    )
                }
                refresh()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        showImportPicker = true,
                        message = error.message ?: "Import failed"
                    )
                }
            }
        }
    }

    fun consumePendingGalleryDelete(): List<Uri> {
        val uris = _state.value.pendingGalleryDelete
        _state.update { it.copy(pendingGalleryDelete = emptyList()) }
        return uris
    }

    fun onGalleryDeleteResult(success: Boolean) {
        _state.update {
            it.copy(
                message = if (success) {
                    "Removed from Gallery"
                } else {
                    "Gallery delete cancelled — file stays in Gallery too"
                }
            )
        }
    }

    fun createGalleryDeleteRequest(uris: List<Uri>) =
        repository.createGalleryDeleteRequest(uris)

    fun importIntoCurrentFolder() {
        val folder = _state.value.importFolder ?: _state.value.currentFolder ?: return
        beginImport(folder.uri, folder.title, cutAfterCopy = false)
    }

    fun importInto(folderUri: Uri, cutAfterCopy: Boolean = false) {
        beginImport(folderUri, _state.value.importFolder?.title ?: "Albums", cutAfterCopy)
    }

    private var pendingPlayAll: List<AlbumItem> = emptyList()

    fun consumePlayAllTracks(): List<AlbumItem> {
        val list = pendingPlayAll
        pendingPlayAll = emptyList()
        return list
    }

    fun preparePlayAllMusic() {
        val folder = _state.value.currentFolder ?: return
        viewModelScope.launch {
            pendingPlayAll = repository.listAudioRecursive(folder.uri)
            _state.update {
                it.copy(
                    message = if (pendingPlayAll.isEmpty()) "No music under this category"
                    else "Playing ${pendingPlayAll.size} track(s) from \"${folder.title}\" + subfolders",
                    advancedBusyMessage = if (pendingPlayAll.isEmpty()) null else "PLAY_ALL"
                )
            }
        }
    }

    fun openStorageMap() {
        val root = _state.value.rootUri ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val stats = repository.storageStats(root)
            _state.update { it.copy(isLoading = false, showStorage = true, storageStats = stats) }
        }
    }

    fun closeStorage() = _state.update { it.copy(showStorage = false) }

    fun openDuplicates() {
        val root = _state.value.rootUri ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val groups = repository.findDuplicates(root)
            _state.update {
                it.copy(
                    isLoading = false,
                    showDuplicates = true,
                    duplicateGroups = groups,
                    message = if (groups.isEmpty()) {
                        "No duplicates found (checked exact + renamed + look-alike photos)"
                    } else {
                        "${groups.size} duplicate group(s) found"
                    }
                )
            }
        }
    }

    fun closeDuplicates() = _state.update { it.copy(showDuplicates = false) }

    fun deleteDuplicate(uri: Uri) {
        viewModelScope.launch {
            repository.deleteItems(listOf(uri))
            openDuplicates()
        }
    }

    fun scanFaces() {
        val root = _state.value.rootUri ?: return
        viewModelScope.launch {
            _state.update { it.copy(faceScanning = true, advancedBusyMessage = "Scanning faces…") }
            val photos = repository.listImagesRecursive(root)
            val withFaces = faceIndexer.indexItems(photos) { done, total ->
                _state.update { it.copy(advancedBusyMessage = "Faces $done / $total") }
            }
            val index = prefs.getFaceIndexOnce()
            val hits = photos.mapNotNull { item ->
                val count = index[item.uri.toString()] ?: return@mapNotNull null
                if (count <= 0) null else FacePhotoHit(item, count, "")
            }.sortedByDescending { it.faceCount }
            _state.update {
                it.copy(
                    faceScanning = false,
                    showFaces = true,
                    faceHits = hits,
                    advancedBusyMessage = null,
                    message = "Found faces in $withFaces photo(s)"
                )
            }
        }
    }

    fun openFacesAlbum() {
        viewModelScope.launch {
            val root = _state.value.rootUri ?: return@launch
            val index = prefs.getFaceIndexOnce()
            val photos = repository.listImagesRecursive(root)
            val hits = photos.mapNotNull { item ->
                val count = index[item.uri.toString()] ?: return@mapNotNull null
                if (count <= 0) null else FacePhotoHit(item, count, "")
            }.sortedByDescending { it.faceCount }
            _state.update { it.copy(showFaces = true, faceHits = hits) }
        }
    }

    fun closeFaces() = _state.update { it.copy(showFaces = false) }

    fun setShowVaultSettings(show: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(showVaultSettings = show, hasPin = prefs.hasPin()) }
        }
    }

    fun setPreferInbox(enabled: Boolean) {
        viewModelScope.launch { prefs.setPreferInbox(enabled) }
    }

    fun setCrossfadePref(enabled: Boolean) {
        viewModelScope.launch { prefs.setCrossfade(enabled) }
    }

    fun setNightModePref(enabled: Boolean) {
        viewModelScope.launch { prefs.setNightMode(enabled) }
    }

    fun setShowPinSetup(show: Boolean) = _state.update { it.copy(showPinSetup = show) }

    fun setupPin(pin: String) {
        viewModelScope.launch {
            prefs.setPin(pin)
            _state.update { it.copy(showPinSetup = false, hasPin = true, message = "Vault PIN saved") }
        }
    }

    fun clearPin() {
        viewModelScope.launch {
            prefs.clearPin()
            _state.update {
                it.copy(
                    hasPin = false,
                    unlockedSession = emptySet(),
                    message = "PIN removed — folders unlocked"
                )
            }
        }
    }

    fun lockCurrentFolder() {
        val folder = _state.value.currentFolder ?: return
        if (folder.title == "Albums") {
            _state.update { it.copy(message = "Lock a category, not the album root") }
            return
        }
        viewModelScope.launch {
            if (!prefs.hasPin()) {
                _state.update { it.copy(showPinSetup = true, message = "Set a PIN first") }
                return@launch
            }
            prefs.setFolderLocked(folder.uri, true)
            _state.update { it.copy(message = "Locked \"${folder.title}\"") }
        }
    }

    fun unlockFolderWithPin(pin: String) {
        val uri = _state.value.pinUnlockFolderUri ?: return
        viewModelScope.launch {
            if (!prefs.verifyPin(pin)) {
                _state.update { it.copy(message = "Wrong PIN") }
                return@launch
            }
            val itemName = _state.value.items.find { it.uri == uri }?.name ?: "Folder"
            _state.update {
                it.copy(
                    pinUnlockFolderUri = null,
                    unlockedSession = it.unlockedSession + uri.toString()
                )
            }
            openFolder(AlbumItem(uri, itemName, null, AlbumItemType.FOLDER))
        }
    }

    fun dismissPinUnlock() = _state.update { it.copy(pinUnlockFolderUri = null) }

    fun setShowFolderNoteEditor(show: Boolean) =
        _state.update { it.copy(showFolderNoteEditor = show) }

    fun saveFolderNote(title: String, body: String, kind: String) {
        val folder = _state.value.currentFolder ?: return
        viewModelScope.launch {
            prefs.setFolderNote(
                FolderNote(folder.uri, title.ifBlank { folder.title }, body, kind)
            )
            _state.update { it.copy(showFolderNoteEditor = false, message = "Saved $kind note") }
        }
    }

    fun resumeLastMemoryReelRequest() {
        viewModelScope.launch {
            val last = prefs.getLastReel()
            _state.update {
                if (last == null) it.copy(message = "No previous Memory Reel")
                else it.copy(advancedBusyMessage = "RESUME_REEL:${last.first}|${last.second}")
            }
        }
    }

    fun markReelSaved(folderUri: Uri, title: String) {
        viewModelScope.launch { prefs.saveLastReel(folderUri, title) }
    }

    fun clearAdvancedBusy() = _state.update { it.copy(advancedBusyMessage = null) }

    fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtraCompat(Intent.EXTRA_STREAM) ?: return
                setIncomingShare(IncomingShare.Single(uri, intent.type))
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtraCompat(Intent.EXTRA_STREAM)
                    ?: return
                if (uris.isNotEmpty()) {
                    setIncomingShare(IncomingShare.Multiple(uris, intent.type))
                }
            }
        }
    }

    private fun Intent.getParcelableExtraCompat(key: String): Uri? {
        return androidx.core.content.IntentCompat.getParcelableExtra(this, key, Uri::class.java)
    }

    private fun Intent.getParcelableArrayListExtraCompat(key: String): ArrayList<Uri>? {
        return androidx.core.content.IntentCompat.getParcelableArrayListExtra(
            this,
            key,
            Uri::class.java
        )
    }
}
