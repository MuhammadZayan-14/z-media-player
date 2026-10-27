package app.resonance.core.data.di

import app.resonance.core.data.mediastore.AndroidMediaStoreSource
import app.resonance.core.data.mediastore.MediaStoreSource
import app.resonance.core.data.repository.DataStoreUserPreferencesRepository
import app.resonance.core.data.repository.OfflineFirstMediaLibraryRepository
import app.resonance.core.data.repository.RoomFavoritesRepository
import app.resonance.core.data.repository.RoomHistoryRepository
import app.resonance.core.data.repository.RoomPlaybackSessionRepository
import app.resonance.core.data.repository.RoomPlaylistRepository
import app.resonance.core.data.repository.RoomVideoProgressRepository
import app.resonance.core.domain.repository.FavoritesRepository
import app.resonance.core.domain.repository.HistoryRepository
import app.resonance.core.domain.repository.MediaLibraryRepository
import app.resonance.core.domain.repository.PlaybackSessionRepository
import app.resonance.core.domain.repository.PlaylistRepository
import app.resonance.core.domain.repository.UserPreferencesRepository
import app.resonance.core.domain.repository.VideoProgressRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds fun mediaStoreSource(impl: AndroidMediaStoreSource): MediaStoreSource

    @Binds fun mediaLibrary(impl: OfflineFirstMediaLibraryRepository): MediaLibraryRepository

    @Binds fun playlists(impl: RoomPlaylistRepository): PlaylistRepository

    @Binds fun favorites(impl: RoomFavoritesRepository): FavoritesRepository

    @Binds fun history(impl: RoomHistoryRepository): HistoryRepository

    @Binds fun videoProgress(impl: RoomVideoProgressRepository): VideoProgressRepository

    @Binds fun playbackSession(impl: RoomPlaybackSessionRepository): PlaybackSessionRepository

    @Binds fun preferences(impl: DataStoreUserPreferencesRepository): UserPreferencesRepository
}
