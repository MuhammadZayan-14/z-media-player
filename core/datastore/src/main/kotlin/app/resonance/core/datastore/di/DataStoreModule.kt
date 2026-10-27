package app.resonance.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.resonance.core.common.AppDispatcher
import app.resonance.core.common.ApplicationScope
import app.resonance.core.common.Dispatcher
import app.resonance.core.datastore.LibrarySyncStore
import app.resonance.core.datastore.UserPreferencesStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    @UserPreferencesStore
    fun userPreferences(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @Dispatcher(AppDispatcher.IO) io: CoroutineDispatcher
    ): DataStore<Preferences> = create(context, scope, io, "user_preferences")

    @Provides
    @Singleton
    @LibrarySyncStore
    fun librarySync(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @Dispatcher(AppDispatcher.IO) io: CoroutineDispatcher
    ): DataStore<Preferences> = create(context, scope, io, "library_sync")

    private fun create(context: Context, scope: CoroutineScope, io: CoroutineDispatcher, name: String) =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope + io,
            produceFile = { context.preferencesDataStoreFile(name) }
        )
}
