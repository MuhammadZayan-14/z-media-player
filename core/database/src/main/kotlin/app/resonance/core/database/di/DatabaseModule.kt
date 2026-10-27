package app.resonance.core.database.di

import android.content.Context
import app.resonance.core.database.ResonanceDatabase
import app.resonance.core.database.dao.FavoriteDao
import app.resonance.core.database.dao.HistoryDao
import app.resonance.core.database.dao.MediaDao
import app.resonance.core.database.dao.PlaybackSessionDao
import app.resonance.core.database.dao.PlaylistDao
import app.resonance.core.database.dao.VideoProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ResonanceDatabase = ResonanceDatabase.build(context)

    @Provides fun mediaDao(db: ResonanceDatabase): MediaDao = db.mediaDao()

    @Provides fun playlistDao(db: ResonanceDatabase): PlaylistDao = db.playlistDao()

    @Provides fun favoriteDao(db: ResonanceDatabase): FavoriteDao = db.favoriteDao()

    @Provides fun historyDao(db: ResonanceDatabase): HistoryDao = db.historyDao()

    @Provides fun videoProgressDao(db: ResonanceDatabase): VideoProgressDao = db.videoProgressDao()

    @Provides fun playbackSessionDao(db: ResonanceDatabase): PlaybackSessionDao = db.playbackSessionDao()
}
