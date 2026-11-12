package com.faz.xbyfaz.core.model

enum class FeedKind { Home, Following, Videos, Bookmarks, Likes, ProfilePosts, ProfileVideos, Search }

enum class VideoQuality {
    Auto, Q1080, Q720, Q480;

    fun maxHeight(): Int? = when (this) {
        Auto -> null
        Q1080 -> 1080
        Q720 -> 720
        Q480 -> 480
    }

    fun label(): String = when (this) {
        Auto -> "Auto"
        Q1080 -> "1080p"
        Q720 -> "720p"
        Q480 -> "480p"
    }

    companion object {
        fun fromStored(value: String?): VideoQuality = entries.firstOrNull { it.name == value } ?: Auto
    }
}

enum class PreloadMode {
    Off, Balanced, Aggressive;

    fun targetPreloadUs(): Long = when (this) {
        Off -> 0L
        Balanced -> 8_000_000L
        Aggressive -> 20_000_000L
    }

    companion object {
        fun fromStored(value: String?): PreloadMode = entries.firstOrNull { it.name == value } ?: Balanced
    }
}

enum class TextScale(val multiplier: Float, val label: String) {
    Small(0.9f, "Small"),
    Default(1f, "Default"),
    Large(1.15f, "Large"),
    ;

    companion object {
        fun fromStored(value: String?): TextScale = entries.firstOrNull { it.name == value } ?: Default
    }
}

data class Author(
    val name: String,
    val handle: String,
    val avatarUrl: String?,
)

data class Mp4Variant(
    val url: String,
    val bitrate: Int?,
)

data class VideoMedia(
    val videoId: String,
    val durationMs: Long,
    val posterUrl: String?,
    val hlsUrl: String?,
    val mp4Urls: List<Mp4Variant>,
    val protectedStream: Boolean,
) {
    fun hasDirectUrl(): Boolean = !hlsUrl.isNullOrBlank() || mp4Urls.isNotEmpty()
}

data class Post(
    val id: String,
    val text: String,
    val createdAt: String?,
    val author: Author,
    val photos: List<String>,
    val video: VideoMedia?,
    val replyCount: Long,
    val repostCount: Long,
    val likeCount: Long,
    val viewCount: Long?,
    val permalink: String,
) {
    val hasVideo: Boolean get() = video != null
}

data class Profile(
    val handle: String,
    val name: String,
    val bio: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val followers: Long?,
    val following: Long?,
    val posts: Long?,
    val location: String?,
    val joined: String?,
    val isFollowing: Boolean?,
)

data class FeedPage(
    val posts: List<Post>,
    val cursor: String?,
    val warning: AppFailure? = null,
)

data class SearchPage(
    val accounts: List<Profile>,
    val posts: List<Post>,
    val cursor: String?,
    val warning: AppFailure? = null,
)

data class Playable(
    val videoId: String,
    val postId: String,
    val title: String,
    val authorName: String,
    val authorHandle: String,
    val avatarUrl: String?,
    val posterUrl: String?,
    val hlsUrl: String?,
    val mp4Urls: List<Mp4Variant>,
    val permalink: String,
    val durationMs: Long,
    val protectedStream: Boolean,
    val source: String,
)

data class ResolvedPlayback(
    val url: String?,
    val mime: String?,
    val webFallback: Boolean,
)

data class WatchEntry(
    val videoId: String,
    val postId: String,
    val accountHandle: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val completed: Boolean,
    val source: String,
    val title: String,
    val authorName: String,
    val authorHandle: String,
    val posterUrl: String?,
    val mediaUrl: String?,
    val permalink: String,
)

data class DiagnosticEntry(
    val id: Long,
    val code: String,
    val message: String,
    val detail: String,
    val screen: String,
    val timestamp: Long,
)

data class AppSettings(
    val autoplayNext: Boolean = true,
    val quality: VideoQuality = VideoQuality.Auto,
    val resume: Boolean = true,
    val scaleFill: Boolean = false,
    val controlsTimeoutSec: Int = 4,
    val preload: PreloadMode = PreloadMode.Balanced,
    val cacheMb: Int = 512,
    val dataSaver: Boolean = false,
    val compactThumbnails: Boolean = false,
    val textScale: TextScale = TextScale.Default,
    val remoteHints: Boolean = true,
    val showOverlay: Boolean = true,
    val historyEnabled: Boolean = true,
    val handle: String? = null,
    val displayName: String? = null,
    val recentSearches: List<String> = emptyList(),
    val recentProfiles: List<String> = emptyList(),
)

object PlaybackSelector {
    const val MIME_HLS = "application/x-mpegURL"
    const val MIME_MP4 = "video/mp4"

    fun select(video: VideoMedia, quality: VideoQuality, dataSaver: Boolean): ResolvedPlayback {
        if (video.protectedStream && !video.hasDirectUrl()) {
            return ResolvedPlayback(url = null, mime = null, webFallback = true)
        }
        val effective = effectiveQuality(quality, dataSaver)
        val hls = Sanitizer.usableMediaUrl(video.hlsUrl)
        if (hls != null) {
            return ResolvedPlayback(url = hls, mime = MIME_HLS, webFallback = false)
        }
        val mp4 = bestMp4(video.mp4Urls, effective)
        if (mp4 != null) {
            return ResolvedPlayback(url = mp4, mime = MIME_MP4, webFallback = false)
        }
        return ResolvedPlayback(url = null, mime = null, webFallback = true)
    }

    fun effectiveQuality(quality: VideoQuality, dataSaver: Boolean): VideoQuality {
        if (!dataSaver) return quality
        return when (quality) {
            VideoQuality.Auto, VideoQuality.Q1080 -> VideoQuality.Q720
            else -> quality
        }
    }

    fun bestMp4(variants: List<Mp4Variant>, quality: VideoQuality): String? {
        val usable = variants.mapNotNull { v ->
            val url = Sanitizer.usableMediaUrl(v.url) ?: return@mapNotNull null
            v.copy(url = url)
        }
        if (usable.isEmpty()) return null
        val cap = bitrateCap(quality)
        val under = if (cap == null) usable else usable.filter { (it.bitrate ?: Int.MAX_VALUE) <= cap }
        val pool = if (under.isEmpty()) usable else under
        return pool.maxByOrNull { it.bitrate ?: 0 }?.url
    }

    private fun bitrateCap(quality: VideoQuality): Int? = when (quality) {
        VideoQuality.Auto -> null
        VideoQuality.Q1080 -> 6_500_000
        VideoQuality.Q720 -> 2_500_000
        VideoQuality.Q480 -> 900_000
    }
}

fun Post.toPlayable(source: String): Playable? {
    val video = video ?: return null
    return Playable(
        videoId = video.videoId.ifBlank { id },
        postId = id,
        title = text,
        authorName = author.name,
        authorHandle = author.handle,
        avatarUrl = author.avatarUrl,
        posterUrl = video.posterUrl ?: photos.firstOrNull(),
        hlsUrl = video.hlsUrl,
        mp4Urls = video.mp4Urls,
        permalink = permalink,
        durationMs = video.durationMs,
        protectedStream = video.protectedStream,
        source = source,
    )
}

fun Playable.resolve(quality: VideoQuality, dataSaver: Boolean): ResolvedPlayback {
    return PlaybackSelector.select(
        VideoMedia(
            videoId = videoId,
            durationMs = durationMs,
            posterUrl = posterUrl,
            hlsUrl = hlsUrl,
            mp4Urls = mp4Urls,
            protectedStream = protectedStream,
        ),
        quality,
        dataSaver,
    )
}
