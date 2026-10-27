package app.resonance

import android.app.Application
import android.os.StrictMode
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.resonance.core.common.ReleaseTree
import app.resonance.core.data.sync.LibrarySyncCoordinator
import app.resonance.core.designsystem.image.MediaThumbnailFetcher
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class ResonanceApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var librarySync: LibrarySyncCoordinator

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            enableStrictMode()
        } else {
            Timber.plant(ReleaseTree())
        }
        // Sync the library while any part of the UI is visible; stop observing in the background.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = librarySync.onForeground()
                override fun onStop(owner: LifecycleOwner) = librarySync.onBackground()
            }
        )
    }

    /** Artwork comes from MediaStore thumbnails at display size; no network loader is installed. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(MediaThumbnailFetcher.Factory(contentResolver)) }
            .crossfade(true)
            .build()

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectActivityLeaks().penaltyLog().build()
        )
    }
}
