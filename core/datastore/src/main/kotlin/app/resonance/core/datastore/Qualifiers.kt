package app.resonance.core.datastore

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class UserPreferencesStore

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class LibrarySyncStore
