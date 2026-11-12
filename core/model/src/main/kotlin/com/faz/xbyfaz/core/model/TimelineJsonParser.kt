package com.faz.xbyfaz.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

sealed interface ParseResult {
    data class Ok(
        val posts: List<Post>,
        val profiles: List<Profile>,
        val cursor: String?,
    ) : ParseResult

    data object Unreadable : ParseResult
}

object TimelineJsonParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): ParseResult {
        if (raw.isBlank()) return ParseResult.Unreadable
        val root = try {
            json.parseToJsonElement(raw)
        } catch (_: Exception) {
            return ParseResult.Unreadable
        }
        val posts = mutableListOf<Post>()
        val profiles = mutableListOf<Profile>()
        val cursors = mutableListOf<String>()
        walk(root, posts, profiles, cursors)
        return ParseResult.Ok(
            posts = posts.distinctBy { it.id },
            profiles = profiles.distinctBy { it.handle.lowercase() },
            cursor = cursors.lastOrNull(),
        )
    }

    fun looksLikeTimeline(raw: String): Boolean {
        return raw.contains("tweet_results") || raw.contains("full_text")
    }

    private fun walk(
        element: JsonElement,
        posts: MutableList<Post>,
        profiles: MutableList<Profile>,
        cursors: MutableList<String>,
    ) {
        when (element) {
            is JsonObject -> {
                val tweetCarrier = element.obj("tweet") ?: element
                asTweet(tweetCarrier)?.let { posts += it }
                asProfile(element)?.let { profiles += it }
                cursorOf(element)?.let { cursors += it }
                element.values.forEach { walk(it, posts, profiles, cursors) }
            }
            is JsonArray -> element.forEach { walk(it, posts, profiles, cursors) }
            else -> Unit
        }
    }

    private fun cursorOf(o: JsonObject): String? {
        val value = o.str("value") ?: return null
        val type = o.str("cursorType")
        val entry = o.str("entryId").orEmpty()
        if (type.equals("Bottom", true) || entry.contains("cursor-bottom")) return value
        return null
    }

    private fun asTweet(o: JsonObject): Post? {
        val legacy = o.obj("legacy") ?: return null
        if (legacy.str("screen_name") != null && legacy.str("full_text") == null) return null
        val text = noteText(o) ?: legacy.str("full_text") ?: legacy.str("text") ?: return null
        val id = o.str("rest_id") ?: legacy.str("id_str") ?: return null
        if (id.isBlank()) return null
        val userLegacy = o.obj("core")
            ?.obj("user_results")
            ?.obj("result")
            ?.let { it.obj("legacy") ?: it.obj("core")?.obj("user_results")?.obj("result")?.obj("legacy") }
            ?: o.obj("core")?.obj("user_results")?.obj("result")?.obj("legacy")
        val name = userLegacy?.str("name").orEmpty().ifBlank { "X user" }
        val handle = userLegacy?.str("screen_name").orEmpty().removePrefix("@")
        val avatar = Sanitizer.usableMediaUrl(userLegacy?.str("profile_image_url_https"))
        val (photos, video) = mediaOf(id, legacy)
        val views = o.obj("views")?.str("count")?.toLongOrNull()
            ?: o.obj("views")?.long("count")?.takeIf { it > 0L }
        return Post(
            id = id,
            text = text,
            createdAt = legacy.str("created_at"),
            author = Author(name = name, handle = handle, avatarUrl = avatar),
            photos = photos,
            video = video,
            replyCount = legacy.long("reply_count"),
            repostCount = legacy.long("retweet_count"),
            likeCount = legacy.long("favorite_count"),
            viewCount = views,
            permalink = if (handle.isBlank()) "https://x.com/i/status/$id" else "https://x.com/$handle/status/$id",
        )
    }

    private fun noteText(o: JsonObject): String? {
        return o.obj("note_tweet")
            ?.obj("note_tweet_results")
            ?.obj("result")
            ?.str("text")
            ?.takeIf { it.isNotBlank() }
    }

    private fun mediaOf(postId: String, legacy: JsonObject): Pair<List<String>, VideoMedia?> {
        val entities = legacy.obj("extended_entities") ?: legacy.obj("entities")
        val media = entities?.arr("media") ?: return emptyList<String>() to null
        val photos = mutableListOf<String>()
        var video: VideoMedia? = null
        for (el in media) {
            val item = el as? JsonObject ?: continue
            val type = item.str("type").orEmpty()
            val poster = Sanitizer.usableMediaUrl(item.str("media_url_https"))
            if (type == "photo") {
                poster?.let { photos += it }
                continue
            }
            if (type == "video" || type == "animated_gif") {
                if (video != null) continue
                val info = item.obj("video_info")
                val variants = info?.arr("variants").orEmpty()
                var hls: String? = null
                val mp4 = mutableListOf<Mp4Variant>()
                var protectedOnly = false
                for (vEl in variants) {
                    val v = vEl as? JsonObject ?: continue
                    val content = v.str("content_type").orEmpty()
                    val url = v.str("url")
                    when {
                        content.contains("mpegurl", true) -> hls = hls ?: Sanitizer.usableMediaUrl(url)
                        content.contains("mp4", true) -> {
                            val usable = Sanitizer.usableMediaUrl(url)
                            if (usable != null) mp4 += Mp4Variant(usable, v.int("bitrate"))
                        }
                        content.contains("dash", true) || content.contains("mpd", true) -> protectedOnly = true
                    }
                }
                val direct = hls != null || mp4.isNotEmpty()
                video = VideoMedia(
                    videoId = item.str("id_str")?.takeIf { it.isNotBlank() } ?: "$postId-v",
                    durationMs = info?.long("duration_millis") ?: 0L,
                    posterUrl = poster,
                    hlsUrl = hls,
                    mp4Urls = mp4,
                    protectedStream = !direct && (protectedOnly || variants.isNotEmpty() || info == null),
                )
            }
        }
        return photos to video
    }

    private fun asProfile(o: JsonObject): Profile? {
        val legacy = o.obj("legacy") ?: return null
        if (legacy.str("full_text") != null || legacy.str("text") != null) return null
        val handle = legacy.str("screen_name")?.removePrefix("@") ?: return null
        if (handle.isBlank()) return null
        val name = legacy.str("name") ?: return null
        val looksLikeUser = legacy.containsKey("followers_count") ||
            legacy.containsKey("friends_count") ||
            legacy.str("description") != null ||
            legacy.str("profile_image_url_https") != null
        if (!looksLikeUser) return null
        return Profile(
            handle = handle,
            name = name,
            bio = legacy.str("description").orEmpty(),
            avatarUrl = Sanitizer.usableMediaUrl(legacy.str("profile_image_url_https")),
            bannerUrl = Sanitizer.usableMediaUrl(legacy.str("profile_banner_url")),
            followers = legacy.longOrNull("followers_count"),
            following = legacy.longOrNull("friends_count"),
            posts = legacy.longOrNull("statuses_count"),
            location = legacy.str("location"),
            joined = legacy.str("created_at"),
            isFollowing = legacy.bool("following"),
        )
    }
}

object DomFeedParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): ParseResult {
        val root = try {
            json.parseToJsonElement(raw) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return ParseResult.Unreadable
        val tweets = root.arr("tweets") ?: return ParseResult.Unreadable
        val posts = tweets.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("id").orEmpty()
            val text = o.str("text").orEmpty()
            if (id.isBlank() && text.isBlank()) return@mapNotNull null
            val handle = o.str("handle").orEmpty().removePrefix("@")
            val photos = o.arr("photos").orEmpty().mapNotNull { element ->
                val primitive = element as? JsonPrimitive ?: return@mapNotNull null
                Sanitizer.usableMediaUrl(primitive.content)
            }
            val videoUrl = Sanitizer.usableMediaUrl(o.str("videoUrl"))
            val video = if (videoUrl != null || o.str("videoUrl")?.startsWith("blob:") == true) {
                VideoMedia(
                    videoId = "$id-v",
                    durationMs = o.long("durationMs"),
                    posterUrl = Sanitizer.usableMediaUrl(o.str("poster")),
                    hlsUrl = videoUrl?.takeIf { it.contains(".m3u8") },
                    mp4Urls = videoUrl?.takeUnless { it.contains(".m3u8") }?.let { listOf(Mp4Variant(it, null)) }.orEmpty(),
                    protectedStream = videoUrl == null,
                )
            } else {
                null
            }
            val stableId = id.ifBlank { text.hashCode().toUInt().toString() }
            Post(
                id = stableId,
                text = text,
                createdAt = o.str("time"),
                author = Author(
                    name = o.str("name").orEmpty().ifBlank { handle.ifBlank { "X user" } },
                    handle = handle,
                    avatarUrl = Sanitizer.usableMediaUrl(o.str("avatar")),
                ),
                photos = photos,
                video = video,
                replyCount = o.long("replies"),
                repostCount = o.long("reposts"),
                likeCount = o.long("likes"),
                viewCount = o.longOrNull("views"),
                permalink = if (handle.isBlank()) "https://x.com/i/status/$stableId" else "https://x.com/$handle/status/$stableId",
            )
        }
        return ParseResult.Ok(posts.distinctBy { it.id }, emptyList(), root.str("cursor"))
    }
}

internal fun JsonObject.str(key: String): String? {
    val el = this[key] ?: return null
    if (el is JsonNull) return null
    val p = el as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.content
}

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonObject.long(key: String): Long = longOrNull(key) ?: 0L

internal fun JsonObject.longOrNull(key: String): Long? {
    val el = this[key] ?: return null
    if (el is JsonNull) return null
    val p = el as? JsonPrimitive ?: return null
    return p.longOrNull ?: p.content.toLongOrNull()
}

internal fun JsonObject.int(key: String): Int? = longOrNull(key)?.toInt()

internal fun JsonObject.bool(key: String): Boolean? {
    val el = this[key] as? JsonPrimitive ?: return null
    return when (el.content.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}
