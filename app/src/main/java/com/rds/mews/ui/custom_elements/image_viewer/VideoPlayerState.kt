package com.rds.mews.ui.custom_elements.image_viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import kotlin.coroutines.resume

private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "mov", "m4v", "3gp", "m3u8")

fun isVideoUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val lastSegment = url.toUri().lastPathSegment?.lowercase(Locale.ROOT) ?: return false
    val extension = lastSegment.substringAfterLast('.', "")
    return extension in VIDEO_EXTENSIONS
}

fun formatMediaDuration(durationMs: Long): String {
    val safeMs = durationMs.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@OptIn(UnstableApi::class)
object VideoCacheManager {
    private const val MAX_CACHE_BYTES = 150L * 1024L * 1024L

    @Volatile
    private var simpleCache: SimpleCache? = null
    private val preloadedBytesByUrl = ConcurrentHashMap<String, Long>()
    private val mediaSizesByUrl = ConcurrentHashMap<String, Size>()
    private val preloadSemaphore = Semaphore(2)

    fun recordMediaSize(url: String, width: Float, height: Float) {
        if (url.isNotBlank() && width.isFinite() && height.isFinite() && width > 0f && height > 0f) {
            mediaSizesByUrl[url] = Size(width, height)
        }
    }

    fun getMediaSize(url: String): Size? = mediaSizesByUrl[url]

    fun getCache(context: Context): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: run {
                val appContext = context.applicationContext
                val cacheDir = File(appContext.cacheDir, "video_media_cache")
                val databaseProvider = StandaloneDatabaseProvider(appContext)
                val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
                SimpleCache(cacheDir, evictor, databaseProvider).also {
                    simpleCache = it
                }
            }
        }
    }

    fun getCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0")
            .setAllowCrossProtocolRedirects(true)

        return CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    suspend fun preload(
        context: Context,
        url: String,
        bytesToPreload: Long = 2L * 1024L * 1024L
    ) {
        if (!isVideoUrl(url)) return
        val alreadyPreloaded = preloadedBytesByUrl[url] ?: 0L
        if (alreadyPreloaded >= bytesToPreload) return

        withContext(Dispatchers.IO) {
            preloadSemaphore.acquire()
            try {
                val currentPreloaded = preloadedBytesByUrl[url] ?: 0L
                if (currentPreloaded >= bytesToPreload) return@withContext

                val dataSource = getCacheDataSourceFactory(context).createDataSource()
                val dataSpec = DataSpec.Builder()
                    .setUri(Uri.parse(url))
                    .setPosition(0L)
                    .setLength(bytesToPreload)
                    .build()

                val writer = CacheWriter(dataSource, dataSpec, null, null)
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation {
                        writer.cancel()
                    }
                    try {
                        writer.cache()
                        preloadedBytesByUrl[url] = bytesToPreload
                    } catch (_: Exception) {
                    } finally {
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                }
            } finally {
                preloadSemaphore.release()
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Stable
class SharedVideoPlayerController(
    context: Context
) {
    private val appContext = context.applicationContext

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                VideoCacheManager.getCacheDataSourceFactory(appContext)
            )
        )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 1f
        }

    var activeTitleId by mutableLongStateOf(-1L)
        private set

    var activePage by mutableIntStateOf(-1)
        private set

    var activeUrl by mutableStateOf<String?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var isBuffering by mutableStateOf(false)
        private set

    var hasError by mutableStateOf(false)
        private set

    var isUnsupportedFormat by mutableStateOf(false)
        private set

    var currentPositionMs by mutableLongStateOf(0L)
        private set

    var durationMs by mutableLongStateOf(0L)
        private set

    var isScrubbing by mutableStateOf(false)
        private set

    var scrubPositionMs by mutableLongStateOf(0L)
        private set

    var retryCount by mutableIntStateOf(0)
        private set

    var lastRetryTime by mutableLongStateOf(0L)
        private set

    var desiredPlayWhenReady by mutableStateOf(true)
        private set

    var videoSize by mutableStateOf<Size?>(null)
        private set

    var lastFrameBitmap by mutableStateOf<Bitmap?>(null)
        private set

    var lastFrameUrl by mutableStateOf<String?>(null)
        private set

    private var wasPlayingBeforeScrub: Boolean = false
    private var lastScrubSeekTimeMs: Long = 0L

    val canRetry: Boolean
        get() = retryCount < 3 && (System.currentTimeMillis() - lastRetryTime > 2000L)

    val displayPositionMs: Long
        get() = if (isScrubbing) scrubPositionMs else currentPositionMs

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                val width = videoSize.width * videoSize.pixelWidthHeightRatio
                val height = videoSize.height.toFloat()
                this@SharedVideoPlayerController.videoSize = Size(width, height)
                activeUrl?.let { VideoCacheManager.recordMediaSize(it, width, height) }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            isBuffering = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) {
                hasError = false
                isUnsupportedFormat = false
                val dur = player.duration
                if (dur != C.TIME_UNSET && dur > 0L) {
                    durationMs = dur
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            hasError = true
            isBuffering = false
            isPlaying = false
            clearLastFrame()
            isUnsupportedFormat = error.cause is UnrecognizedInputFormatException ||
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
        }
    }

    init {
        player.addListener(listener)
    }

    fun bindToPage(titleId: Long, page: Int, url: String) {
        if (activeTitleId == titleId && activePage == page && activeUrl == url) return

        activeTitleId = titleId
        activePage = page
        activeUrl = url
        desiredPlayWhenReady = true
        hasError = false
        isUnsupportedFormat = false
        isBuffering = true
        currentPositionMs = 0L
        durationMs = 0L
        isScrubbing = false
        scrubPositionMs = 0L
        wasPlayingBeforeScrub = false
        lastScrubSeekTimeMs = 0L
        retryCount = 0
        lastRetryTime = 0L
        videoSize = null
        clearLastFrame()

        player.setMediaItem(MediaItem.fromUri(url), 0L)
        player.prepare()
        player.playWhenReady = true
    }

    fun bindToPage(page: Int, url: String) {
        bindToPage(-1L, page, url)
    }

    fun clearPage() {
        if (activeTitleId == -1L && activePage == -1 && activeUrl == null) return
        activeTitleId = -1L
        activePage = -1
        activeUrl = null
        desiredPlayWhenReady = true
        isPlaying = false
        isBuffering = false
        hasError = false
        isUnsupportedFormat = false
        isScrubbing = false
        scrubPositionMs = 0L
        wasPlayingBeforeScrub = false
        lastScrubSeekTimeMs = 0L
        currentPositionMs = 0L
        durationMs = 0L
        retryCount = 0
        lastRetryTime = 0L
        videoSize = null
        clearLastFrame()
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
    }

    fun saveLastFrame(url: String, bitmap: Bitmap) {
        if (activeUrl != url || hasError) return
        lastFrameUrl = url
        lastFrameBitmap = bitmap
    }

    fun clearLastFrame() {
        lastFrameBitmap = null
        lastFrameUrl = null
    }

    fun pause(persistUserChoice: Boolean = true) {
        if (persistUserChoice) {
            desiredPlayWhenReady = false
        }
        val wasPlaying = isPlaying
        player.playWhenReady = false
        if (wasPlaying) {
            currentPositionMs = player.currentPosition.coerceAtLeast(0L)
        }
    }

    fun togglePlayPause() {
        if (hasError) return
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
            player.prepare()
        }
        val nextPlayState = !player.playWhenReady
        desiredPlayWhenReady = nextPlayState
        player.playWhenReady = nextPlayState
        val pos = player.currentPosition.coerceAtLeast(0L)
        currentPositionMs = pos
    }

    fun onScrubStart() {
        isScrubbing = true
        wasPlayingBeforeScrub = isPlaying || desiredPlayWhenReady
        scrubPositionMs = currentPositionMs
        lastScrubSeekTimeMs = 0L
        player.playWhenReady = false
    }

    fun onScrubChange(positionMs: Long) {
        val target = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        scrubPositionMs = target
        currentPositionMs = target
        val now = SystemClock.elapsedRealtime()
        if (now - lastScrubSeekTimeMs >= 60L) {
            lastScrubSeekTimeMs = now
            player.seekTo(target)
        }
    }

    fun onScrubEnd() {
        val target = if (durationMs > 200L && scrubPositionMs >= durationMs - 200L) {
            0L
        } else {
            scrubPositionMs
        }
        currentPositionMs = target
        scrubPositionMs = target
        player.seekTo(target)
        isScrubbing = false
        val shouldResume = wasPlayingBeforeScrub && desiredPlayWhenReady
        wasPlayingBeforeScrub = false
        player.playWhenReady = shouldResume
    }

    fun retry() {
        val url = activeUrl ?: return
        if (!canRetry) return
        lastRetryTime = System.currentTimeMillis()
        retryCount++
        hasError = false
        isUnsupportedFormat = false
        isBuffering = true
        desiredPlayWhenReady = true
        clearLastFrame()
        player.setMediaItem(MediaItem.fromUri(url), currentPositionMs)
        player.prepare()
        player.playWhenReady = true
    }

    fun syncProgress() {
        val dur = player.duration
        if (dur != C.TIME_UNSET && dur > 0L) {
            durationMs = dur
        }
        if (!isScrubbing) {
            val pos = player.currentPosition
            if (pos >= 0L) {
                currentPositionMs = pos
            }
        }
    }

    fun release() {
        clearLastFrame()
        videoSize = null
        player.removeListener(listener)
        player.release()
    }
}

typealias VideoPlayerState = SharedVideoPlayerController

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun rememberVideoPlayerState(
    controller: SharedVideoPlayerController
): SharedVideoPlayerController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(controller, lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                val activity = context.findActivity()
                if (activity == null || !activity.isChangingConfigurations) {
                    controller.pause(persistUserChoice = false)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(controller, controller.isPlaying, controller.activePage) {
        while (controller.activePage >= 0 && controller.isPlaying) {
            controller.syncProgress()
            delay(100L)
        }
    }

    return controller
}