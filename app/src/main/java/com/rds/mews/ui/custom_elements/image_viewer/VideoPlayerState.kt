package com.rds.mews.ui.custom_elements.image_viewer

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
    val lastSegment = Uri.parse(url).lastPathSegment?.lowercase(Locale.ROOT) ?: return false
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
    private val preloadSemaphore = Semaphore(2)

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

@Stable
class VideoPlayerState(
    val player: ExoPlayer,
    restoredPage: Int = -1,
    restoredUrl: String? = null,
    restoredPositionMs: Long = 0L,
    restoredDurationMs: Long = 0L,
    restoredPlayWhenReady: Boolean = true,
    private val onPersistState: (page: Int, url: String?, positionMs: Long, durationMs: Long, playWhenReady: Boolean) -> Unit
) {
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

    private var desiredPlayWhenReady: Boolean = restoredPlayWhenReady
    private var pendingRestorePage: Int = restoredPage
    private var pendingRestoreUrl: String? = restoredUrl
    private var pendingRestorePositionMs: Long = restoredPositionMs
    private var pendingRestoreDurationMs: Long = restoredDurationMs
    private var pendingRestorePlayWhenReady: Boolean = restoredPlayWhenReady

    val canRetry: Boolean
        get() = retryCount < 3 && (System.currentTimeMillis() - lastRetryTime > 2000L)

    val displayPositionMs: Long
        get() = if (isScrubbing) scrubPositionMs else currentPositionMs

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            isBuffering = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) {
                hasError = false
                isUnsupportedFormat = false
                val dur = player.duration
                if (dur != C.TIME_UNSET && dur > 0L) {
                    durationMs = dur
                    onPersistState(activePage, activeUrl, currentPositionMs, dur, desiredPlayWhenReady)
                }
            }
        }

        @OptIn(UnstableApi::class)
        override fun onPlayerError(error: PlaybackException) {
            hasError = true
            isBuffering = false
            isPlaying = false
            isUnsupportedFormat = error.cause is UnrecognizedInputFormatException ||
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
        }
    }

    init {
        player.addListener(listener)
    }

    fun bindToPage(page: Int, url: String) {
        if (activePage == page && activeUrl == url) return

        val hasPendingRestore = pendingRestoreUrl == url && (pendingRestorePage == page || pendingRestorePage == -1)
        val startPosition = if (hasPendingRestore) pendingRestorePositionMs.coerceAtLeast(0L) else 0L
        val startDuration = if (hasPendingRestore) pendingRestoreDurationMs.coerceAtLeast(0L) else 0L
        val startPlayWhenReady = if (hasPendingRestore) pendingRestorePlayWhenReady else true

        pendingRestoreUrl = null
        pendingRestorePage = -1
        pendingRestorePositionMs = 0L
        pendingRestoreDurationMs = 0L
        pendingRestorePlayWhenReady = true

        activePage = page
        activeUrl = url
        desiredPlayWhenReady = startPlayWhenReady
        hasError = false
        isUnsupportedFormat = false
        isBuffering = true
        currentPositionMs = startPosition
        durationMs = startDuration
        isScrubbing = false
        scrubPositionMs = startPosition
        retryCount = 0
        lastRetryTime = 0L

        player.setMediaItem(MediaItem.fromUri(url), startPosition)
        player.prepare()
        player.playWhenReady = startPlayWhenReady
        onPersistState(page, url, startPosition, startDuration, startPlayWhenReady)
    }

    fun clearPage() {
        if (activePage == -1 && activeUrl == null) return
        activePage = -1
        activeUrl = null
        desiredPlayWhenReady = true
        isPlaying = false
        isBuffering = false
        hasError = false
        isUnsupportedFormat = false
        isScrubbing = false
        currentPositionMs = 0L
        durationMs = 0L
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        onPersistState(-1, null, 0L, 0L, true)
    }

    fun pause(persistUserChoice: Boolean = true) {
        if (persistUserChoice) {
            desiredPlayWhenReady = false
        }
        player.playWhenReady = false
        val pos = player.currentPosition.coerceAtLeast(currentPositionMs)
        currentPositionMs = pos
        onPersistState(activePage, activeUrl, pos, durationMs, desiredPlayWhenReady)
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
        onPersistState(activePage, activeUrl, pos, durationMs, nextPlayState)
    }

    fun onScrubStart() {
        isScrubbing = true
        scrubPositionMs = currentPositionMs
    }

    fun onScrubChange(positionMs: Long) {
        scrubPositionMs = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    }

    fun onScrubEnd() {
        val target = if (durationMs > 200L && scrubPositionMs >= durationMs - 200L) {
            0L
        } else {
            scrubPositionMs
        }
        currentPositionMs = target
        player.seekTo(target)
        isScrubbing = false
        onPersistState(activePage, activeUrl, target, durationMs, desiredPlayWhenReady)
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
        player.setMediaItem(MediaItem.fromUri(url), currentPositionMs)
        player.prepare()
        player.playWhenReady = true
        onPersistState(activePage, url, currentPositionMs, durationMs, true)
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
                onPersistState(activePage, activeUrl, pos, durationMs, desiredPlayWhenReady)
            }
        }
    }

    fun release() {
        player.removeListener(listener)
        player.release()
    }
}

@OptIn(UnstableApi::class)
@Composable
fun rememberVideoPlayerState(
    context: Context = LocalContext.current
): VideoPlayerState {
    val lifecycleOwner = LocalLifecycleOwner.current

    var savedPage by rememberSaveable { mutableIntStateOf(-1) }
    var savedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var savedPositionMs by rememberSaveable { mutableLongStateOf(0L) }
    var savedDurationMs by rememberSaveable { mutableLongStateOf(0L) }
    var savedPlayWhenReady by rememberSaveable { mutableStateOf(true) }

    val state = remember(context) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(
            VideoCacheManager.getCacheDataSourceFactory(context)
        )

        val exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 1f
            }

        VideoPlayerState(
            player = exoPlayer,
            restoredPage = savedPage,
            restoredUrl = savedUrl,
            restoredPositionMs = savedPositionMs,
            restoredDurationMs = savedDurationMs,
            restoredPlayWhenReady = savedPlayWhenReady,
            onPersistState = { page, url, positionMs, durationMs, playWhenReady ->
                savedPage = page
                savedUrl = url
                savedPositionMs = positionMs
                savedDurationMs = durationMs
                savedPlayWhenReady = playWhenReady
            }
        )
    }

    DisposableEffect(state, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                state.pause(persistUserChoice = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            state.release()
        }
    }

    LaunchedEffect(state, state.isPlaying, state.activePage) {
        while (state.activePage >= 0 && state.isPlaying) {
            state.syncProgress()
            delay(100L)
        }
    }

    return state
}