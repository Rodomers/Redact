package com.rds.mews.ui.custom_elements.image_viewer

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.Options
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val mediaDownloadClient by lazy { OkHttpClient() }

@OptIn(UnstableApi::class)
private class CacheBackedMediaDataSource(
    context: Context,
    private val uri: Uri
) : MediaDataSource() {
    private val appContext = context.applicationContext
    private val dataSource: CacheDataSource = VideoCacheManager
        .getCacheDataSourceFactory(appContext)
        .createDataSource()
    private var currentPosition = -1L
    private var isOpened = false
    private var cachedSize: Long? = null

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size == 0) return 0
        val totalSize = getSize()
        if (totalSize > 0L && position >= totalSize) return -1

        try {
            if (!isOpened || currentPosition != position) {
                if (isOpened) {
                    runCatching { dataSource.close() }
                    isOpened = false
                }
                val dataSpec = DataSpec.Builder()
                    .setUri(uri)
                    .setPosition(position)
                    .setLength(C.LENGTH_UNSET.toLong())
                    .build()
                val openedLength = dataSource.open(dataSpec)
                isOpened = true
                currentPosition = position
                if (cachedSize == null && openedLength != C.LENGTH_UNSET.toLong() && openedLength > 0L) {
                    cachedSize = position + openedLength
                }
            }

            val bytesRead = dataSource.read(buffer, offset, size)
            if (bytesRead == C.RESULT_END_OF_INPUT) {
                return -1
            }
            if (bytesRead > 0) {
                currentPosition += bytesRead
            }
            return bytesRead
        } catch (_: Exception) {
            runCatching { dataSource.close() }
            isOpened = false
            currentPosition = -1L
            return -1
        }
    }

    @Synchronized
    override fun getSize(): Long {
        cachedSize?.let { return it }
        val cache = VideoCacheManager.getCache(appContext)
        val metadataLength = ContentMetadata.getContentLength(cache.getContentMetadata(uri.toString()))
        if (metadataLength != C.LENGTH_UNSET.toLong() && metadataLength > 0L) {
            cachedSize = metadataLength
            return metadataLength
        }
        return try {
            if (isOpened) {
                runCatching { dataSource.close() }
                isOpened = false
                currentPosition = -1L
            }
            val dataSpec = DataSpec.Builder()
                .setUri(uri)
                .setPosition(0L)
                .setLength(C.LENGTH_UNSET.toLong())
                .build()
            val length = dataSource.open(dataSpec)
            isOpened = true
            currentPosition = 0L
            if (length != C.LENGTH_UNSET.toLong() && length > 0L) {
                cachedSize = length
                length
            } else {
                -1L
            }
        } catch (_: Exception) {
            runCatching { dataSource.close() }
            isOpened = false
            currentPosition = -1L
            -1L
        }
    }

    @Synchronized
    override fun close() {
        if (isOpened) {
            runCatching { dataSource.close() }
            isOpened = false
        }
        currentPosition = -1L
    }
}

class VideoThumbnailFetcher(
    private val url: String,
    private val options: Options
) : Fetcher {

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val inFlightTasks = ConcurrentHashMap<String, Deferred<Bitmap?>>()

        private fun hashUrl(url: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }

        private fun getThumbFile(context: Context, url: String): File {
            val dir = File(context.applicationContext.cacheDir, "video_thumbs_cache")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return File(dir, "${hashUrl(url)}.jpg")
        }

        fun clearCachedThumbnail(context: Context, url: String) {
            inFlightTasks.remove(url)?.cancel()
            runCatching {
                val file = getThumbFile(context, url)
                if (file.exists()) {
                    file.delete()
                }
            }
        }

        private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= maxDimension && height <= maxDimension) return bitmap
            val ratio = width.toFloat() / height.toFloat()
            val targetWidth: Int
            val targetHeight: Int
            if (width >= height) {
                targetWidth = maxDimension
                targetHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
            } else {
                targetHeight = maxDimension
                targetWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
            }
            return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        }
    }

    override suspend fun fetch(): FetchResult {
        val appContext = options.context.applicationContext
        val thumbFile = getThumbFile(appContext, url)

        if (thumbFile.exists() && thumbFile.length() > 0L) {
            val cachedBitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(thumbFile.absolutePath)
            }
            if (cachedBitmap != null) {
                VideoCacheManager.recordMediaSize(
                    url,
                    cachedBitmap.width.toFloat(),
                    cachedBitmap.height.toFloat()
                )
                return DrawableResult(
                    drawable = cachedBitmap.toDrawable(options.context.resources),
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            }
        }

        val deferred = inFlightTasks.computeIfAbsent(url) {
            scope.async {
                try {
                    if (thumbFile.exists() && thumbFile.length() > 0L) {
                        BitmapFactory.decodeFile(thumbFile.absolutePath)?.let { return@async it }
                    }

                    VideoCacheManager.preload(appContext, url, 2L * 1024L * 1024L)

                    val retriever = MediaMetadataRetriever()
                    val mediaDataSource = CacheBackedMediaDataSource(appContext, Uri.parse(url))
                    try {
                        retriever.setDataSource(mediaDataSource)
                        val rawBitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.getFrameAtTime(-1L)
                            ?: return@async null

                        val scaledBitmap = scaleBitmapIfNeeded(rawBitmap, 720)
                        if (scaledBitmap !== rawBitmap) {
                            rawBitmap.recycle()
                        }

                        VideoCacheManager.recordMediaSize(
                            url,
                            scaledBitmap.width.toFloat(),
                            scaledBitmap.height.toFloat()
                        )

                        runCatching {
                            FileOutputStream(thumbFile).use { out ->
                                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            }
                        }

                        scaledBitmap
                    } catch (_: Exception) {
                        null
                    } finally {
                        runCatching { retriever.release() }
                        runCatching { mediaDataSource.close() }
                    }
                } finally {
                    inFlightTasks.remove(url)
                }
            }
        }

        val bitmap = deferred.await()
            ?: throw IllegalStateException("Failed to extract video thumbnail for $url")
        return DrawableResult(
            drawable = bitmap.toDrawable(options.context.resources),
            isSampled = true,
            dataSource = DataSource.NETWORK
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val urlString = data.toString()
            if (!isVideoUrl(urlString)) return null
            return VideoThumbnailFetcher(urlString, options)
        }
    }
}

suspend fun saveImageToGallery(
    context: Context,
    imageUrl: String,
    fileName: String = "Redact_${System.currentTimeMillis()}"
): Boolean = saveMediaToGallery(
    context = context,
    mediaUrl = imageUrl,
    isVideo = false,
    fileName = fileName
)

suspend fun saveMediaToGallery(
    context: Context,
    mediaUrl: String,
    isVideo: Boolean = isVideoUrl(mediaUrl),
    fileName: String = "Redact_${System.currentTimeMillis()}"
): Boolean = withContext(Dispatchers.IO) {
    if (isVideo) {
        saveVideoToGallery(context, mediaUrl, fileName)
    } else {
        saveBitmapToGallery(context, mediaUrl, fileName)
    }
}

private suspend fun saveBitmapToGallery(
    context: Context,
    imageUrl: String,
    fileName: String
): Boolean {
    try {
        val imageRequest = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false)
            .build()

        val result = (context.imageLoader.execute(imageRequest) as? SuccessResult)?.drawable
        val bitmap = (result as? BitmapDrawable)?.bitmap ?: return false

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Redact")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return false

        resolver.openOutputStream(imageUri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        } ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(imageUri, contentValues, null, null)
        }

        return true
    } catch (e: Exception) {
        e.printStackTrace()
        return false
    }
}

private fun saveVideoToGallery(
    context: Context,
    videoUrl: String,
    fileName: String
): Boolean {
    val resolver = context.contentResolver
    var insertedUri: Uri? = null
    try {
        val request = Request.Builder().url(videoUrl).build()
        mediaDownloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body

            val rawExt = videoUrl.toUri()
                .lastPathSegment
                ?.substringAfterLast('.', "")
                ?.lowercase(Locale.ROOT)
                .orEmpty()
            val extension = if (rawExt in setOf("mp4", "webm", "mkv", "mov", "m4v", "3gp")) rawExt else "mp4"
            val mimeType = body.contentType()?.toString()
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: "video/mp4"

            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "$fileName.$extension")
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Redact")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val videoUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return false
            insertedUri = videoUri

            resolver.openOutputStream(videoUri)?.use { outputStream ->
                body.byteStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(videoUri, contentValues, null, null)
            }
            return true
        }
    } catch (e: Exception) {
        e.printStackTrace()
        insertedUri?.let { uri ->
            runCatching { resolver.delete(uri, null, null) }
        }
        return false
    }
}