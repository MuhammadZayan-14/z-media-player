package app.resonance.core.designsystem.image

import android.content.ContentResolver
import android.provider.MediaStore
import android.util.Size
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import coil3.toAndroidUri

/**
 * Loads artwork for MediaStore items (`content://media/...`).
 *
 * The URI of an audio or video file is not an image, so instead of decoding its bytes this asks
 * MediaStore for a thumbnail at the size actually being drawn: embedded album art for audio,
 * a representative frame for video. MediaStore caches these, so no disk cache is needed here.
 */
class MediaThumbnailFetcher(
    private val uri: android.net.Uri,
    private val options: Options,
    private val resolver: ContentResolver
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val width = options.size.width.pxOrElse { DEFAULT_SIZE_PX }.coerceAtMost(MAX_SIZE_PX)
        val height = options.size.height.pxOrElse { DEFAULT_SIZE_PX }.coerceAtMost(MAX_SIZE_PX)
        // Throws IOException when there is no artwork; Coil reports that as an error state.
        val bitmap = resolver.loadThumbnail(uri, Size(width, height), null)
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val resolver: ContentResolver) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == ContentResolver.SCHEME_CONTENT && data.authority == MediaStore.AUTHORITY) {
                MediaThumbnailFetcher(data.toAndroidUri(), options, resolver)
            } else {
                null
            }
    }

    private companion object {
        const val DEFAULT_SIZE_PX = 256
        const val MAX_SIZE_PX = 1024
    }
}
