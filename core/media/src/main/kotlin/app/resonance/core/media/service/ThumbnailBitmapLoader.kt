package app.resonance.core.media.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService

/**
 * Artwork URIs point at MediaStore items (an audio file, not an image), so they are loaded via
 * `ContentResolver.loadThumbnail`, which extracts embedded art at a bounded size. Anything else
 * goes through Media3's default loader.
 */
@OptIn(UnstableApi::class)
internal class ThumbnailBitmapLoader(
    private val context: Context,
    private val executor: ListeningExecutorService
) : BitmapLoader {
    private val fallback = DataSourceBitmapLoader.Builder(context).setExecutorService(executor).build()

    override fun supportsMimeType(mimeType: String): Boolean = fallback.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = fallback.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        if (uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) {
            executor.submit<Bitmap> { context.contentResolver.loadThumbnail(uri, Size(SIZE_PX, SIZE_PX), null) }
        } else {
            fallback.loadBitmap(uri)
        }

    private companion object {
        /** Large enough for the lock screen, small enough to keep notification updates cheap. */
        const val SIZE_PX = 512
    }
}
