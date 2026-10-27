package app.resonance.core.testing

import app.resonance.core.domain.repository.FavoritesRepository
import app.resonance.core.domain.repository.HistoryRepository
import app.resonance.core.domain.repository.MediaLibraryRepository
import app.resonance.core.domain.repository.PlaybackSessionRepository
import app.resonance.core.domain.repository.PlaylistRepository
import app.resonance.core.domain.repository.UserPreferencesRepository
import app.resonance.core.domain.repository.VideoProgressRepository
import app.resonance.core.model.Album
import app.resonance.core.model.Artist
import app.resonance.core.model.Folder
import app.resonance.core.model.Genre
import app.resonance.core.model.LibraryScanState
import app.resonance.core.model.MediaCollection
import app.resonance.core.model.MediaFile
import app.resonance.core.model.MediaKind
import app.resonance.core.model.PlaybackSnapshot
import app.resonance.core.model.Playlist
import app.resonance.core.model.SearchResults
import app.resonance.core.model.SongSort
import app.resonance.core.model.ThemeMode
import app.resonance.core.model.UserPreferences
import app.resonance.core.model.VideoProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory library; set [media] to change what every query sees. */
class FakeMediaLibraryRepository(initial: List<MediaFile> = emptyList()) : MediaLibraryRepository {
    val media = MutableStateFlow(initial)
    override val scanState = MutableStateFlow<LibraryScanState>(LibraryScanState.Idle)
    var refreshCount = 0
        private set

    private val audio get() = media.map { list -> list.filter { it.kind == MediaKind.AUDIO } }

    override fun songs(sort: SongSort): Flow<List<MediaFile>> = audio.map { list ->
        when (sort) {
            SongSort.TITLE -> list.sortedBy { it.title.lowercase() }
            SongSort.ARTIST -> list.sortedBy { it.artist.orEmpty().lowercase() }
            SongSort.ALBUM -> list.sortedBy { it.album.orEmpty().lowercase() }
            SongSort.DATE_ADDED -> list.sortedByDescending { it.dateAddedEpochSec }
            SongSort.DURATION -> list.sortedByDescending { it.durationMs }
        }
    }

    override fun videos(): Flow<List<MediaFile>> = media.map { list -> list.filter { it.kind == MediaKind.VIDEO } }

    override fun albums(): Flow<List<Album>> = audio.map { list ->
        list.groupBy { it.albumId }.map { (id, tracks) -> tracks.toAlbum(id) }.sortedBy { it.title }
    }

    override fun artists(): Flow<List<Artist>> = audio.map { list ->
        list.groupBy { it.artistId }.map { (id, tracks) ->
            Artist(id, tracks.first().artist, tracks.size, tracks.distinctBy { it.albumId }.size)
        }.sortedBy { it.name }
    }

    override fun genres(): Flow<List<Genre>> = audio.map { list ->
        list.mapNotNull { it.genre }.groupingBy { it }.eachCount().map { (name, count) -> Genre(name, count) }
    }

    override fun folders(): Flow<List<Folder>> = audio.map { list ->
        list.groupingBy { it.folderPath }.eachCount().map { (path, count) -> Folder(path, count) }
    }

    override fun album(albumId: Long): Flow<Album?> = audio.map { list ->
        list.filter { it.albumId == albumId }.takeIf { it.isNotEmpty() }?.toAlbum(albumId)
    }

    override fun artist(artistId: Long): Flow<Artist?> = artists().map { list ->
        list.firstOrNull { it.id == artistId }
    }

    override fun albumsByArtist(artistId: Long): Flow<List<Album>> = audio.map { list ->
        list.filter { it.artistId == artistId }.groupBy { it.albumId }.map { (id, tracks) -> tracks.toAlbum(id) }
    }

    override fun tracks(collection: MediaCollection): Flow<List<MediaFile>> = audio.map { list ->
        when (collection) {
            is MediaCollection.AlbumTracks -> list.filter { it.albumId == collection.albumId }
            is MediaCollection.ArtistTracks -> list.filter { it.artistId == collection.artistId }
            is MediaCollection.GenreTracks -> list.filter { it.genre.equals(collection.genre, ignoreCase = true) }
            is MediaCollection.FolderTracks -> list.filter { it.folderPath == collection.path }
        }
    }

    override fun recentlyAdded(limit: Int): Flow<List<MediaFile>> =
        audio.map { list -> list.sortedByDescending { it.dateAddedEpochSec }.take(limit) }

    override fun search(query: String, limitPerSection: Int): Flow<SearchResults> = media.map { list ->
        val matches = list.filter { it.title.contains(query, ignoreCase = true) }
        SearchResults(
            songs = matches.filter { it.kind == MediaKind.AUDIO }.take(limitPerSection),
            videos = matches.filter { it.kind == MediaKind.VIDEO }.take(limitPerSection)
        )
    }

    override suspend fun getMedia(ids: List<Long>): List<MediaFile> {
        val byId = media.value.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    override suspend fun getMedia(id: Long): MediaFile? = media.value.firstOrNull { it.id == id }

    override suspend fun refresh(full: Boolean) {
        refreshCount++
    }

    private fun List<MediaFile>.toAlbum(id: Long) =
        Album(id, first().album, first().artist, size, first().year, first().artworkUri)
}

class FakePlaylistRepository(private val library: FakeMediaLibraryRepository = FakeMediaLibraryRepository()) :
    PlaylistRepository {
    private val playlistsState = MutableStateFlow<Map<Long, Playlist>>(emptyMap())
    private val items = MutableStateFlow<Map<Long, List<Long>>>(emptyMap())
    private var nextId = 1L

    override fun playlists(): Flow<List<Playlist>> =
        combine(playlistsState, items) { lists, entries ->
            lists.values.map { it.copy(trackCount = entries[it.id].orEmpty().size) }.sortedBy { it.name }
        }

    override fun playlist(id: Long): Flow<Playlist?> = playlists().map { list -> list.firstOrNull { it.id == id } }

    override fun tracks(playlistId: Long): Flow<List<MediaFile>> = combine(items, library.media) { entries, media ->
        val byId = media.associateBy { it.id }
        entries[playlistId].orEmpty().mapNotNull { byId[it] }
    }

    override suspend fun create(name: String): Long {
        val id = nextId++
        playlistsState.update { it + (id to Playlist(id, name, 0, 0L, 0L)) }
        return id
    }

    override suspend fun rename(id: Long, name: String) {
        playlistsState.update { map -> map[id]?.let { map + (id to it.copy(name = name)) } ?: map }
    }

    override suspend fun delete(id: Long) {
        playlistsState.update { it - id }
        items.update { it - id }
    }

    override suspend fun nameExists(name: String, excludingId: Long?): Boolean =
        playlistsState.value.values.any { it.name.equals(name, ignoreCase = true) && it.id != excludingId }

    override suspend fun addTracks(playlistId: Long, mediaIds: List<Long>): Int {
        val existing = items.value[playlistId].orEmpty()
        val added = mediaIds.distinct().filterNot { it in existing }
        items.update { it + (playlistId to existing + added) }
        return added.size
    }

    override suspend fun removeTrack(playlistId: Long, mediaId: Long) {
        items.update { it + (playlistId to it[playlistId].orEmpty() - mediaId) }
    }

    override suspend fun moveTrack(playlistId: Long, fromIndex: Int, toIndex: Int) {
        val list = items.value[playlistId].orEmpty().toMutableList()
        list.add(toIndex, list.removeAt(fromIndex))
        items.update { it + (playlistId to list) }
    }
}

class FakeFavoritesRepository(private val library: FakeMediaLibraryRepository = FakeMediaLibraryRepository()) :
    FavoritesRepository {
    val ids = MutableStateFlow<Set<Long>>(emptySet())

    override fun favoriteIds(): Flow<Set<Long>> = ids

    override fun favorites(): Flow<List<MediaFile>> =
        combine(ids, library.media) { favs, media -> media.filter { it.id in favs } }

    override suspend fun setFavorite(mediaId: Long, favorite: Boolean) {
        ids.update { if (favorite) it + mediaId else it - mediaId }
    }
}

class FakeHistoryRepository(private val library: FakeMediaLibraryRepository = FakeMediaLibraryRepository()) :
    HistoryRepository {
    val plays = MutableStateFlow<List<Pair<Long, Long>>>(emptyList())

    override fun recentlyPlayed(limit: Int): Flow<List<MediaFile>> = combine(plays, library.media) { p, media ->
        val byId = media.associateBy { it.id }
        p.sortedByDescending { it.second }.map { it.first }.distinct().mapNotNull { byId[it] }.take(limit)
    }

    override fun mostPlayed(limit: Int): Flow<List<MediaFile>> = combine(plays, library.media) { p, media ->
        val byId = media.associateBy { it.id }
        p.groupingBy { it.first }.eachCount().entries.sortedByDescending { it.value }
            .mapNotNull { byId[it.key] }.take(limit)
    }

    override suspend fun recordPlay(mediaId: Long, playedAtEpochMs: Long) {
        plays.update { it + (mediaId to playedAtEpochMs) }
    }

    override suspend fun clear() {
        plays.value = emptyList()
    }
}

class FakeVideoProgressRepository : VideoProgressRepository {
    val progress = MutableStateFlow<Map<Long, VideoProgress>>(emptyMap())

    override fun inProgress(): Flow<List<VideoProgress>> = progress.map { it.values.toList() }

    override suspend fun get(mediaId: Long): VideoProgress? = progress.value[mediaId]

    override suspend fun save(progress: VideoProgress) {
        this.progress.update { it + (progress.mediaId to progress) }
    }

    override suspend fun clear(mediaId: Long) {
        progress.update { it - mediaId }
    }
}

class FakePlaybackSessionRepository(var snapshot: PlaybackSnapshot? = null) : PlaybackSessionRepository {
    override suspend fun load(): PlaybackSnapshot? = snapshot

    override suspend fun save(snapshot: PlaybackSnapshot, queueChanged: Boolean) {
        this.snapshot = snapshot
    }

    override suspend fun clear() {
        snapshot = null
    }
}

class FakeUserPreferencesRepository(initial: UserPreferences = UserPreferences()) : UserPreferencesRepository {
    private val state = MutableStateFlow(initial)
    override val preferences: StateFlow<UserPreferences> = state

    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }
    override suspend fun setUseDynamicColor(enabled: Boolean) = state.update { it.copy(useDynamicColor = enabled) }
    override suspend fun setHideDuplicates(enabled: Boolean) = state.update { it.copy(hideDuplicates = enabled) }
    override suspend fun setMinAudioDurationSec(seconds: Int) = state.update { it.copy(minAudioDurationSec = seconds) }
    override suspend fun setIncludeNonMusicAudio(
        enabled: Boolean
    ) = state.update { it.copy(includeNonMusicAudio = enabled) }
    override suspend fun setSongSort(sort: SongSort) = state.update { it.copy(songSort = sort) }
    override suspend fun setSkipSilence(enabled: Boolean) = state.update { it.copy(skipSilence = enabled) }
    override suspend fun setRestoreLastSession(
        enabled: Boolean
    ) = state.update { it.copy(restoreLastSession = enabled) }
}
