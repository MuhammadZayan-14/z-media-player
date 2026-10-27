package app.resonance.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Bookkeeping for incremental MediaStore syncs: the MediaStore version string (changes when
 * the provider's database is rebuilt, invalidating generations) and the last generation seen
 * per storage volume.
 */
data class LibrarySyncState(
    val mediaStoreVersion: String?,
    val generations: Map<String, Long>,
    val lastSyncEpochMs: Long?
)

@Singleton
class LibrarySyncStateDataSource @Inject constructor(
    @LibrarySyncStore private val dataStore: DataStore<Preferences>
) {
    suspend fun read(): LibrarySyncState {
        val prefs = dataStore.data.first()
        return LibrarySyncState(
            mediaStoreVersion = prefs[VERSION],
            generations = decodeGenerations(prefs[GENERATIONS]),
            lastSyncEpochMs = prefs[LAST_SYNC]
        )
    }

    suspend fun write(state: LibrarySyncState) {
        dataStore.edit { prefs ->
            state.mediaStoreVersion?.let { prefs[VERSION] = it } ?: prefs.remove(VERSION)
            prefs[GENERATIONS] = encodeGenerations(state.generations)
            state.lastSyncEpochMs?.let { prefs[LAST_SYNC] = it }
        }
    }

    suspend fun reset() {
        dataStore.edit { it.clear() }
    }

    internal companion object {
        private val VERSION = stringPreferencesKey("mediastore_version")
        private val GENERATIONS = stringPreferencesKey("generations")
        private val LAST_SYNC = longPreferencesKey("last_sync")

        // Volume names are MediaStore identifiers like "external_primary" or "1a2b-3c4d".
        fun encodeGenerations(map: Map<String, Long>): String =
            map.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }

        fun decodeGenerations(raw: String?): Map<String, Long> =
            raw.orEmpty().split(';').mapNotNull { entry ->
                val name = entry.substringBefore('=', missingDelimiterValue = "")
                val generation = entry.substringAfter('=', missingDelimiterValue = "").toLongOrNull()
                if (name.isEmpty() || generation == null) null else name to generation
            }.toMap()
    }
}
