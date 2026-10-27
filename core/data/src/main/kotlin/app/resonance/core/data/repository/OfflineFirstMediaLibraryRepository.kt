package app.resonance.core.data.repository

import app.resonance.core.common.AppDispatcher
import app.resonance.core.common.Dispatcher
import app.resonance.core.data.mapper.asExternalModel
import app.resonance.core.data.mapper.asExternalModels
import app.resonance.core.data.sync.LibrarySynchronizer
import app.resonance.core.database.dao.MediaDao
import app.resonance.core.database.entity.MediaEntity
import app.resonance.core.datastore.UserPreferencesDataSource
import app.resonance.core.domain.repository.MediaLibraryRepository
import app.resonance.core.model.Album
import app.resonance.core.model.Artist
import app.resonance.core.model.Folder
import app.resonance.core.model.Genre
import app.resonance.core.model.LibraryScanState
import app.resonance.core.model.MediaCollection
import app.resonance.core.model.MediaFile
import app.resonance.core.model.MediaKind
import app.resonance.core.model.SearchResults
import app.resonance.core.model.SongSort
import java.text.Collator
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** The user's library preferences, resolved into query parameters. */
internal data class LibraryFilter(
    val minDurationMs: Long,
    val includeNonMusic: Boolean,
    val hideDuplicates: Boolean
) {
    fun admits(entity: MediaEntity): Boolean =
        entity.kind == MediaKind.VIDEO ||
            (entity.durationMs >= minDurationMs && (includeNonMusic || entity.isMusic))
}

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstMediaLibraryRepository @Inject constructor(
    private val mediaDao: MediaDao,
    private val preferences: UserPreferencesDataSource,
    private val synchronizer: LibrarySynchronizer,
    @param:Dispatcher(AppDispatcher.DEFAULT) private val default: CoroutineDispatcher,
    @param:Dispatcher(AppDispatcher.IO) private val io: CoroutineDispatcher
) : MediaLibraryRepository {

    private val filter: Flow<LibraryFilter> = preferences.preferences
        .map { LibraryFilter(it.minAudioDurationSec * 1000L, it.includeNonMusicAudio, it.hideDuplicates) }
        .distinctUntilChanged()

    override val scanState: StateFlow<LibraryScanState> = synchronizer.state

    override fun songs(sort: SongSort): Flow<List<MediaFile>> = filter
        .flatMapLatest { f -> mediaDao.songs(f.minDurationMs, f.includeNonMusic, f.hideDuplicates) }
        .map { entities -> entities.asExternalModels().sortedWith(songComparator(sort)) }
        .flowOn(default)

    override fun videos(): Flow<List<MediaFile>> = mediaDao.videos().map { it.asExternalModels() }.flowOn(default)

    override fun albums(): Flow<List<Album>> = filter
        .flatMapLatest { f -> mediaDao.albums(f.minDurationMs, f.includeNonMusic, f.hideDuplicates) }
        .map { rows -> rows.map { it.asExternalModel() }.sortedWith(compareByText { it.title }) }
        .flowOn(default)

    override fun artists(): Flow<List<Artist>> = filter
        .flatMapLatest { f -> mediaDao.artists(f.minDurationMs, f.includeNonMusic, f.hideDuplicates) }
        .map { rows -> rows.map { it.asExternalModel() }.sortedWith(compareByText { it.name }) }
        .flowOn(default)

    override fun genres(): Flow<List<Genre>> = filter
        .flatMapLatest { f -> mediaDao.genres(f.minDurationMs, f.includeNonMusic, f.hideDuplicates) }
        .map { rows -> rows.map { it.asExternalModel() }.sortedWith(compareByText { it.name }) }
        .flowOn(default)

    override fun folders(): Flow<List<Folder>> = filter
        .flatMapLatest { f -> mediaDao.folders(f.minDurationMs, f.includeNonMusic, f.hideDuplicates) }
        .map { rows -> rows.map { it.asExternalModel() }.sortedWith(compareByText { it.path }) }
        .flowOn(default)

    override fun album(albumId: Long): Flow<Album?> = filter
        .flatMapLatest { f -> mediaDao.album(albumId, f.minDurationMs, f.includeNonMusic, f.hideDuplicates) }
        .map { it?.asExternalModel() }

    override fun artist(artistId: Long): Flow<Artist?> = filter
        .flatMapLatest { f -> mediaDao.artist(artistId, f.minDurationMs, f.includeNonMusic, f.hideDuplicates) }
        .map { it?.asExternalModel() }

    override fun albumsByArtist(artistId: Long): Flow<List<Album>> = filter
        .flatMapLatest { f -> mediaDao.albumsByArtist(artistId, f.minDurationMs, f.includeNonMusic, f.hideDuplicates) }
        .map { rows -> rows.map { it.asExternalModel() } }

    override fun tracks(collection: MediaCollection): Flow<List<MediaFile>> = filter
        .flatMapLatest { f ->
            val (min, nonMusic, dupes) = Triple(f.minDurationMs, f.includeNonMusic, f.hideDuplicates)
            when (collection) {
                is MediaCollection.AlbumTracks -> mediaDao.tracksInAlbum(collection.albumId, min, nonMusic, dupes)
                is MediaCollection.ArtistTracks -> mediaDao.tracksByArtist(collection.artistId, min, nonMusic, dupes)
                is MediaCollection.GenreTracks -> mediaDao.tracksInGenre(collection.genre, min, nonMusic, dupes)
                is MediaCollection.FolderTracks -> mediaDao.tracksInFolder(collection.path, min, nonMusic, dupes)
            }
        }
        .map { it.asExternalModels() }
        .flowOn(default)

    override fun recentlyAdded(limit: Int): Flow<List<MediaFile>> = filter
        .flatMapLatest { f -> mediaDao.recentlyAdded(f.minDurationMs, f.includeNonMusic, f.hideDuplicates, limit) }
        .map { it.asExternalModels() }

    override fun search(query: String, limitPerSection: Int): Flow<SearchResults> {
        val fts = ftsQueryOf(query) ?: return flowOf(SearchResults.EMPTY)
        val like = escapeLike(query.trim())
        return filter.flatMapLatest { f ->
            combine(
                mediaDao.searchMedia(fts, MediaKind.AUDIO.name, limitPerSection * 4),
                mediaDao.searchAlbums(like, f.minDurationMs, f.includeNonMusic, f.hideDuplicates, limitPerSection),
                mediaDao.searchArtists(like, f.minDurationMs, f.includeNonMusic, f.hideDuplicates, limitPerSection),
                mediaDao.searchMedia(fts, MediaKind.VIDEO.name, limitPerSection)
            ) { songs, albums, artists, videos ->
                SearchResults(
                    songs = songs.filter(f::admits).take(limitPerSection).asExternalModels(),
                    albums = albums.map { it.asExternalModel() },
                    artists = artists.map { it.asExternalModel() },
                    videos = videos.asExternalModels()
                )
            }
        }.flowOn(default)
    }

    override suspend fun getMedia(ids: List<Long>): List<MediaFile> = withContext(io) {
        val found = ids.distinct().chunked(MediaDao.UPSERT_CHUNK)
            .flatMap { mediaDao.getByIds(it) }
            .associateBy { it.id }
        ids.mapNotNull { found[it]?.asExternalModel() }
    }

    override suspend fun getMedia(id: Long): MediaFile? = withContext(io) { mediaDao.getById(id)?.asExternalModel() }

    override suspend fun refresh(full: Boolean) = synchronizer.sync(full)

    internal companion object {
        /**
         * Builds an FTS4 prefix query: every word must prefix-match some indexed column
         * (`daft pu` -> `daft* pu*`). Input is reduced to letters and digits, so quotes,
         * operators and column filters typed by the user cannot change the MATCH syntax.
         */
        fun ftsQueryOf(raw: String): String? = raw
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ") { "$it*" }

        fun escapeLike(raw: String): String = raw
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

        private fun <T> compareByText(selector: (T) -> String?): Comparator<T> {
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            // Unknown values sort last instead of clustering at the top.
            return compareBy<T> { selector(it) == null }.thenComparator { a, b ->
                collator.compare(selector(a).orEmpty(), selector(b).orEmpty())
            }
        }

        fun songComparator(sort: SongSort): Comparator<MediaFile> {
            val byTitle = compareByText<MediaFile> { it.title }
            return when (sort) {
                SongSort.TITLE -> byTitle

                SongSort.ARTIST -> compareByText<MediaFile> { it.artist }.then(byTitle)

                SongSort.ALBUM -> compareByText<MediaFile> { it.album }
                    .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                    .then(byTitle)

                SongSort.DATE_ADDED -> compareByDescending<MediaFile> { it.dateAddedEpochSec }.then(byTitle)

                SongSort.DURATION -> compareByDescending<MediaFile> { it.durationMs }.then(byTitle)
            }
        }
    }
}
