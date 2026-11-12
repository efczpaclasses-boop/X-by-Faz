package com.faz.xbyfaz.core.player

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.faz.xbyfaz.core.database.SettingsRepository
import com.faz.xbyfaz.core.model.AppFailure
import com.faz.xbyfaz.core.model.AppSettings
import com.faz.xbyfaz.core.model.ErrorCode
import com.faz.xbyfaz.core.model.Playable
import com.faz.xbyfaz.core.model.PlaybackQueue
import com.faz.xbyfaz.core.model.PlaybackSelector
import com.faz.xbyfaz.core.model.PreloadMode
import com.faz.xbyfaz.core.model.XClient
import com.faz.xbyfaz.core.model.resolve
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class PlayMode { Idle, Exo, Web }

sealed interface WebCommand {
    data object Toggle : WebCommand
    data class Seek(val deltaMs: Long) : WebCommand
}

data class PlayerUiState(
    val item: Playable? = null,
    val next: Playable? = null,
    val mode: PlayMode = PlayMode.Idle,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionLabel: String = "0 / 0",
    val error: AppFailure? = null,
    val speed: Float = 1f,
    val needsMore: Boolean = false,
)

@Singleton
class MediaCacheHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private var cache: SimpleCache? = null

    @Synchronized
    fun cache(): SimpleCache {
        cache?.let { return it }
        val mb = settings.current().cacheMb.coerceIn(64, 4_096)
        val dir = File(context.cacheDir, "media").apply { mkdirs() }
        val created = SimpleCache(
            dir,
            LeastRecentlyUsedCacheEvictor(mb * 1024L * 1024L),
            StandaloneDatabaseProvider(context),
        )
        cache = created
        return created
    }

    @Synchronized
    fun clear() {
        val current = cache ?: return
        current.keys.toList().forEach { key -> current.removeResource(key) }
    }

    fun clearImages() {
        val dir = File(context.cacheDir, "images")
        dir.listFiles()?.forEach { it.deleteRecursively() }
    }
}

@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cacheHolder: MediaCacheHolder,
    private val okHttp: OkHttpClient,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val trackSelector = DefaultTrackSelector(context)
    private var exo: ExoPlayer? = null
    private var queue = PlaybackQueue()
    private var settings = AppSettings()
    private val knownMedia = linkedMapOf<String, MediaItem>()
    private val webTried = mutableSetOf<String>()
    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()
    private val _web = MutableSharedFlow<WebCommand>(extraBufferCapacity = 8)
    val webCommands: SharedFlow<WebCommand> = _web.asSharedFlow()
    private var resumeOnStart = false
    private var pendingPosition = 0L

    init {
        scope.launch {
            settingsRepository.settings.collect {
                settings = it
                applyTrackAndPreload()
            }
        }
    }

    fun player(): ExoPlayer {
        check(Looper.myLooper() == Looper.getMainLooper())
        exo?.let { return it }
        return buildPlayer().also {
            exo = it
            applyTrackAndPreload()
        }
    }

    fun open(items: List<Playable>, startVideoId: String?, positionMs: Long) {
        val player = player()
        queue = PlaybackQueue()
        queue.addAll(items)
        if (startVideoId != null) queue.jumpTo(startVideoId)
        webTried.clear()
        knownMedia.clear()
        player.clearMediaItems()
        pendingPosition = positionMs
        showCurrent(positionMs)
    }

    fun append(items: List<Playable>) {
        val added = queue.addAll(items)
        if (added > 0) publish()
    }

    fun next() = step(1)
    fun previous() = step(-1)

    fun toggle() {
        val state = _ui.value
        if (state.mode == PlayMode.Web) {
            _web.tryEmit(WebCommand.Toggle)
            _ui.value = state.copy(playing = !state.playing)
            return
        }
        val player = exo ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBy(deltaMs: Long) {
        if (_ui.value.mode == PlayMode.Web) {
            _web.tryEmit(WebCommand.Seek(deltaMs))
            return
        }
        val player = exo ?: return
        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, duration)
        player.seekTo(target)
    }

    fun setSpeed(speed: Float) {
        exo?.setPlaybackSpeed(speed)
        _ui.value = _ui.value.copy(speed = speed)
    }

    fun skip() {
        val id = queue.current()?.videoId ?: return
        webTried.remove(id)
        val next = queue.markUnavailable(id)
        if (next == null) {
            exo?.pause()
            _ui.value = _ui.value.copy(
                item = null,
                mode = PlayMode.Idle,
                error = AppFailure(ErrorCode.PLAY_004, "Nothing left in this queue.", null),
                positionLabel = queue.positionLabel(),
            )
        } else {
            showCurrent(0L)
        }
    }

    fun retry() {
        val id = queue.current()?.videoId
        if (id != null) webTried.remove(id)
        showCurrent(exo?.currentPosition ?: pendingPosition)
    }

    fun onWebFailed() {
        val item = queue.current()
        _ui.value = _ui.value.copy(
            error = AppFailure(
                ErrorCode.PLAY_003,
                "The post opened, but no video element was found.",
                item?.permalink,
            ),
            mode = PlayMode.Web,
            playing = false,
        )
    }

    fun pauseForLifecycle() {
        val player = exo ?: return
        resumeOnStart = player.isPlaying
        player.pause()
    }

    fun resumeIfWanted() {
        if (resumeOnStart && _ui.value.mode == PlayMode.Exo) exo?.play()
    }

    fun stop() {
        exo?.pause()
        exo?.clearMediaItems()
        queue = PlaybackQueue()
        knownMedia.clear()
        _ui.value = PlayerUiState()
    }

    fun currentPosition(): Long = exo?.currentPosition ?: 0L
    fun duration(): Long = exo?.duration?.takeIf { it > 0 } ?: (_ui.value.item?.durationMs ?: 0L)
    fun queueSnapshot(): List<Playable> = queue.snapshot()

    private fun step(delta: Int) {
        queue.move(delta)
        showCurrent(0L)
    }

    private fun showCurrent(positionMs: Long) {
        val item = queue.current()
        if (item == null) {
            _ui.value = PlayerUiState(error = AppFailure(ErrorCode.PLAY_004, "Nothing left in this queue.", null))
            return
        }
        val resolved = item.resolve(settings.quality, settings.dataSaver)
        val directUrl = resolved.url
        if (directUrl == null || webTried.contains(item.videoId)) {
            exo?.pause()
            _ui.value = baseState(item).copy(mode = PlayMode.Web, playing = true, buffering = false, error = null)
            return
        }
        val player = player()
        ensureMedia(player, item, directUrl, resolved.mime)
        val index = mediaIndex(player, item.videoId)
        if (index >= 0 && player.currentMediaItem?.mediaId != item.videoId) {
            player.seekTo(index, positionMs.coerceAtLeast(0L))
        } else if (positionMs > 1_500L) {
            player.seekTo(positionMs)
        }
        player.playWhenReady = true
        _ui.value = baseState(item).copy(mode = PlayMode.Exo, error = null)
    }

    private fun ensureMedia(player: ExoPlayer, item: Playable, url: String, mime: String?) {
        if (knownMedia.containsKey(item.videoId)) return
        // Keep upcoming direct items in the same playlist so ExoPlayer can preload them.
        queue.snapshot().forEach { candidate ->
            if (knownMedia.containsKey(candidate.videoId)) return@forEach
            val chosen = candidate.resolve(settings.quality, settings.dataSaver)
            val mediaUrl = chosen.url ?: return@forEach
            val media = MediaItem.Builder()
                .setUri(mediaUrl)
                .setMediaId(candidate.videoId)
                .setMimeType(chosen.mime ?: mime ?: MimeTypes.APPLICATION_M3U8)
                .build()
            knownMedia[candidate.videoId] = media
            player.addMediaItem(media)
        }
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (item.videoId == item.videoId && url.isNotBlank()) {
            // Touch url so a bad resolve still prepared the rest of the queue.
            Uri.parse(url)
        }
    }

    private fun mediaIndex(player: ExoPlayer, videoId: String): Int {
        for (i in 0 until player.mediaItemCount) {
            if (player.getMediaItemAt(i).mediaId == videoId) return i
        }
        return -1
    }

    private fun baseState(item: Playable) = PlayerUiState(
        item = item,
        next = queue.peekNext(),
        positionLabel = queue.positionLabel(),
        needsMore = queue.needsMore(),
        speed = _ui.value.speed,
        playing = exo?.isPlaying == true,
        buffering = exo?.playbackState == Player.STATE_BUFFERING,
    )

    private fun publish() {
        val item = queue.current() ?: return
        _ui.value = baseState(item).copy(
            mode = _ui.value.mode,
            error = _ui.value.error,
            playing = if (_ui.value.mode == PlayMode.Exo) exo?.isPlaying == true else _ui.value.playing,
            buffering = exo?.playbackState == Player.STATE_BUFFERING && _ui.value.mode == PlayMode.Exo,
        )
    }

    private fun applyTrackAndPreload() {
        val player = exo ?: return
        val quality = PlaybackSelector.effectiveQuality(settings.quality, settings.dataSaver)
        val height = quality.maxHeight() ?: Int.MAX_VALUE
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setMaxVideoSize(Int.MAX_VALUE, height)
            .build()
        val preload = if (settings.dataSaver) PreloadMode.Off else settings.preload
        player.preloadConfiguration = ExoPlayer.PreloadConfiguration(preload.targetPreloadUs())
    }

    private fun buildPlayer(): ExoPlayer {
        val upstream = OkHttpDataSource.Factory(okHttp).setUserAgent(XClient.USER_AGENT)
        val dataSource = try {
            CacheDataSource.Factory()
                .setCache(cacheHolder.cache())
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } catch (_: Throwable) {
            null
        }
        val factory = DefaultMediaSourceFactory(dataSource ?: upstream)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1_500, 50_000, 400, 800)
            .setBackBuffer(5_000, true)
            .build()
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        val player = ExoPlayer.Builder(context, renderers)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(factory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                publish()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && settings.autoplayNext) {
                    if (queue.peekNext() != null) next() else publish()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val item = queue.current() ?: return
                val code = if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                ) ErrorCode.NET_001 else ErrorCode.PLAY_002
                if (item.videoId !in webTried) {
                    webTried += item.videoId
                    player.pause()
                    _ui.value = baseState(item).copy(mode = PlayMode.Web, playing = true, error = null)
                } else {
                    _ui.value = baseState(item).copy(
                        error = AppFailure(code, "This video couldn't be played.", error.errorCodeName, error),
                        playing = false,
                    )
                }
            }
        })
        return player
    }
}
