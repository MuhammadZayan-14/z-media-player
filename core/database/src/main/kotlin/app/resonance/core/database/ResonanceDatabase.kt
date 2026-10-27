package app.resonance.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import app.resonance.core.database.dao.FavoriteDao
import app.resonance.core.database.dao.HistoryDao
import app.resonance.core.database.dao.MediaDao
import app.resonance.core.database.dao.PlaybackSessionDao
import app.resonance.core.database.dao.PlaylistDao
import app.resonance.core.database.dao.VideoProgressDao
import app.resonance.core.database.entity.FavoriteEntity
import app.resonance.core.database.entity.MediaEntity
import app.resonance.core.database.entity.MediaFtsEntity
import app.resonance.core.database.entity.PlayHistoryEntity
import app.resonance.core.database.entity.PlaybackStateEntity
import app.resonance.core.database.entity.PlaylistEntity
import app.resonance.core.database.entity.PlaylistItemEntity
import app.resonance.core.database.entity.QueueEntryEntity
import app.resonance.core.database.entity.VideoProgressEntity

@Database(
    entities = [
        MediaEntity::class,
        MediaFtsEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        FavoriteEntity::class,
        PlayHistoryEntity::class,
        VideoProgressEntity::class,
        QueueEntryEntity::class,
        PlaybackStateEntity::class
    ],
    version = ResonanceDatabase.VERSION,
    exportSchema = true
)
abstract class ResonanceDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun videoProgressDao(): VideoProgressDao
    abstract fun playbackSessionDao(): PlaybackSessionDao

    companion object {
        const val VERSION = 1
        const val NAME = "resonance.db"

        /**
         * Hand-written migrations, oldest first. Each schema bump adds one entry here (or an
         * `AutoMigration` on [Database]) plus a case in `MigrationTest`; there is no destructive
         * fallback, because playlists and history are user data.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun build(context: Context): ResonanceDatabase =
            Room.databaseBuilder(context, ResonanceDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
