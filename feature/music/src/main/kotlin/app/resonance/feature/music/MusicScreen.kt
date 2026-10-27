package app.resonance.feature.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.resonance.core.designsystem.component.FastScrollIndex
import app.resonance.core.designsystem.component.LoadingState
import app.resonance.core.designsystem.component.MessageState
import app.resonance.core.designsystem.component.alphabetSections
import app.resonance.core.model.LibraryError
import app.resonance.core.model.LibraryScanState
import app.resonance.core.model.SongSort
import app.resonance.core.permissions.AccessLevel
import app.resonance.core.ui.AlbumRow
import app.resonance.core.ui.ArtistRow
import app.resonance.core.ui.FolderRow
import app.resonance.core.ui.GenreRow
import app.resonance.core.ui.LibraryNavigator
import app.resonance.core.ui.MediaPermissionRequest
import app.resonance.core.ui.R as CoreUiR
import app.resonance.core.ui.TrackActions
import app.resonance.core.ui.TrackRow
import app.resonance.core.ui.rememberMediaPermissionState
import app.resonance.core.ui.rememberOpenMediaDocument
import app.resonance.core.ui.rememberTrackActions

internal enum class MusicTab(val label: Int) {
    SONGS(R.string.music_tab_songs),
    ALBUMS(R.string.music_tab_albums),
    ARTISTS(R.string.music_tab_artists),
    GENRES(R.string.music_tab_genres),
    FOLDERS(R.string.music_tab_folders)
}

@Composable
internal fun MusicRoute(
    navigator: LibraryNavigator,
    onOpenGenre: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onSearch: () -> Unit,
    viewModel: MusicViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberMediaPermissionState(onAccessChanged = { viewModel.onAccessChanged() })
    val openFile =
        rememberOpenMediaDocument(arrayOf("audio/*")) { uri, name -> viewModel.playFile(uri.toString(), name) }
    val actions = rememberTrackActions(viewModel, navigator)

    MusicScreen(
        state = state,
        hasAudioAccess = permission.access.audio != AccessLevel.NONE,
        permissionContent = { MediaPermissionRequest(permission) },
        actions = actions,
        callbacks = MusicCallbacks(
            onPlaySong = viewModel::playSong,
            onShuffleAll = viewModel::shuffleAll,
            onSort = viewModel::setSort,
            onRefresh = viewModel::refresh,
            onOpenFile = openFile,
            onSearch = onSearch,
            onOpenAlbum = navigator.onOpenAlbum,
            onOpenArtist = navigator.onOpenArtist,
            onOpenGenre = onOpenGenre,
            onOpenFolder = onOpenFolder
        )
    )
}

internal data class MusicCallbacks(
    val onPlaySong: (Int) -> Unit,
    val onShuffleAll: () -> Unit,
    val onSort: (SongSort) -> Unit,
    val onRefresh: () -> Unit,
    val onOpenFile: () -> Unit,
    val onSearch: () -> Unit,
    val onOpenAlbum: (Long) -> Unit,
    val onOpenArtist: (Long) -> Unit,
    val onOpenGenre: (String) -> Unit,
    val onOpenFolder: (String) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicScreen(
    state: MusicUiState,
    hasAudioAccess: Boolean,
    permissionContent: @Composable () -> Unit,
    actions: TrackActions,
    callbacks: MusicCallbacks
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val selected = MusicTab.entries[tab]
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.music_title)) },
                actions = {
                    IconButton(onClick = callbacks.onSearch) {
                        Icon(Icons.Rounded.Search, stringResource(CoreUiR.string.core_ui_search))
                    }
                    if (selected == MusicTab.SONGS && hasAudioAccess) SortMenu(state.sort, callbacks.onSort)
                    IconButton(onClick = callbacks.onOpenFile) {
                        Icon(Icons.Rounded.FileOpen, stringResource(R.string.music_open_file))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (!hasAudioAccess) {
                permissionContent()
                return@Column
            }
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                MusicTab.entries.forEachIndexed { index, item ->
                    Tab(
                        selected = index == tab,
                        onClick = { tab = index },
                        text = { Text(stringResource(item.label)) },
                        modifier = Modifier.testTag("tab_${item.name.lowercase()}")
                    )
                }
            }
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = callbacks.onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    state.isLoading -> LoadingState()
                    state.content.songs.isEmpty() -> EmptyLibrary(state.scanState, callbacks.onRefresh)
                    else -> TabContent(selected, state, actions, callbacks)
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(scan: LibraryScanState, onRescan: () -> Unit) {
    // A LazyColumn keeps pull-to-refresh working on the empty state.
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            when {
                scan is LibraryScanState.Scanning -> MessageState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.music_scanning),
                    message = ""
                )

                scan is LibraryScanState.Failed && scan.error == LibraryError.StorageUnavailable -> MessageState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.music_empty_title),
                    message = stringResource(R.string.music_scan_failed),
                    actionLabel = stringResource(R.string.music_rescan),
                    onAction = onRescan
                )

                else -> MessageState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.music_empty_title),
                    message = stringResource(R.string.music_empty_message),
                    actionLabel = stringResource(R.string.music_rescan),
                    onAction = onRescan
                )
            }
        }
    }
}

@Composable
private fun TabContent(tab: MusicTab, state: MusicUiState, actions: TrackActions, callbacks: MusicCallbacks) {
    val listState = rememberSaveable(tab, saver = LazyListState.Saver) { LazyListState() }
    val content = state.content
    val sections = remember(tab, content, state.sort) {
        when (tab) {
            MusicTab.SONGS -> if (state.sort ==
                SongSort.TITLE
            ) {
                alphabetSections(content.songs, offset = 1) { it.title }
            } else {
                emptyList()
            }

            MusicTab.ALBUMS -> alphabetSections(content.albums) { it.title }

            MusicTab.ARTISTS -> alphabetSections(content.artists) { it.name }

            MusicTab.GENRES -> alphabetSections(content.genres) { it.name }

            MusicTab.FOLDERS -> emptyList()
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.testTag("music_list")
        ) {
            when (tab) {
                MusicTab.SONGS -> {
                    item(key = "shuffle") {
                        FilledTonalButton(onClick = callbacks.onShuffleAll, modifier = Modifier.padding(16.dp, 8.dp)) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null)
                            Text(
                                stringResource(R.string.music_shuffle_all, content.songs.size),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    itemsIndexed(content.songs, key = { _, song -> song.id }) { index, song ->
                        TrackRow(
                            media = song,
                            isCurrent = song.id == state.currentMediaId,
                            isFavorite = song.id in state.favoriteIds,
                            actions = actions,
                            onClick = { callbacks.onPlaySong(index) },
                            showDetails = true
                        )
                    }
                }

                MusicTab.ALBUMS -> items(content.albums, key = { it.id }) { album ->
                    AlbumRow(album, onClick = { callbacks.onOpenAlbum(album.id) })
                }

                MusicTab.ARTISTS -> items(content.artists, key = { it.id }) { artist ->
                    ArtistRow(artist, onClick = { callbacks.onOpenArtist(artist.id) })
                }

                MusicTab.GENRES -> items(content.genres, key = { it.name }) { genre ->
                    GenreRow(genre, onClick = { callbacks.onOpenGenre(genre.name) })
                }

                MusicTab.FOLDERS -> items(content.folders, key = { it.path }) { folder ->
                    FolderRow(folder, onClick = { callbacks.onOpenFolder(folder.path) })
                }
            }
        }
        FastScrollIndex(sections, listState, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun SortMenu(current: SongSort, onSort: (SongSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.music_sort))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SongSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.label()), style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = { RadioButton(selected = sort == current, onClick = null) },
                    onClick = {
                        expanded = false
                        onSort(sort)
                    }
                )
            }
        }
    }
}

private fun SongSort.label(): Int = when (this) {
    SongSort.TITLE -> R.string.music_sort_title
    SongSort.ARTIST -> R.string.music_sort_artist
    SongSort.ALBUM -> R.string.music_sort_album
    SongSort.DATE_ADDED -> R.string.music_sort_date_added
    SongSort.DURATION -> R.string.music_sort_duration
}
