package com.faz.xbyfaz.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CoreLogicTest {
    private fun playable(id: String, title: String = id) = Playable(
        videoId = id,
        postId = "p-$id",
        title = title,
        authorName = "A",
        authorHandle = "a",
        avatarUrl = null,
        posterUrl = null,
        hlsUrl = "https://video.example/$id.m3u8",
        mp4Urls = emptyList(),
        permalink = "https://x.com/a/status/$id",
        durationMs = 10_000,
        protectedStream = false,
        source = "home",
    )

    @Test
    fun queuePreservesOrderAndDropsDuplicates() {
        val q = PlaybackQueue()
        assertEquals(2, q.addAll(listOf(playable("a"), playable("a"), playable("b"))))
        assertEquals(1, q.addAll(listOf(playable("b"), playable("c"))))
        assertEquals(listOf("a", "b", "c"), q.snapshot().map { it.videoId })
        assertEquals("a", q.current()?.videoId)
        assertEquals("b", q.move(1)?.videoId)
        assertEquals("c", q.move(5)?.videoId)
        assertEquals("c", q.move(1)?.videoId)
        assertEquals("b", q.move(-1)?.videoId)
        assertEquals("2 / 3", q.positionLabel())
    }

    @Test
    fun fastMovesStayInsideTheQueue() {
        val q = PlaybackQueue()
        q.addAll((1..27).map { playable(it.toString()) })
        q.jumpTo("4")
        repeat(10) { q.move(1) }
        assertEquals("14", q.current()?.videoId)
        repeat(100) { q.move(1) }
        assertEquals("27", q.current()?.videoId)
        assertEquals("27 / 27", q.positionLabel())
    }

    @Test
    fun unavailableCurrentIsSkippedAndNearEndAsksForMore() {
        val q = PlaybackQueue()
        q.addAll(listOf(playable("a"), playable("b"), playable("c"), playable("d")))
        q.jumpTo("b")
        assertEquals("c", q.markUnavailable("b")?.videoId)
        assertEquals(listOf("a", "c", "d"), q.snapshot().map { it.videoId })
        q.jumpTo("a")
        assertFalse(q.needsMore(threshold = 1))
        q.jumpTo("d")
        assertTrue(q.needsMore(threshold = 3))
        assertEquals("d", q.markUnavailable("a")?.videoId)
        q.markUnavailable("c")
        q.markUnavailable("d")
        assertNull(q.markUnavailable("a"))
        assertEquals(0, q.size)
        assertTrue(q.needsMore())
    }

    @Test
    fun keyRepeatGateThrottlesHolds() {
        val gate = KeyRepeatGate(350)
        assertTrue(gate.allow(1_000, 0))
        assertFalse(gate.allow(1_100, 1))
        assertFalse(gate.allow(1_200, 4))
        assertTrue(gate.allow(1_350, 6))
        assertTrue(gate.allow(1_400, 0))
    }

    @Test
    fun parserReadsTimelineVideoTextCursorAndSkipsTokenizedUrl() {
        val raw = checkNotNull(javaClass.classLoader.getResourceAsStream("timeline.json")) { "timeline fixture" }.bufferedReader().readText()
        val parsed = TimelineJsonParser.parse(raw) as ParseResult.Ok
        assertEquals(listOf("1001", "1002"), parsed.posts.map { it.id })
        val video = parsed.posts.first()
        assertEquals("Ocean Lab", video.author.name)
        assertEquals("oceanlab_sample", video.author.handle)
        assertEquals(58_000L, video.video?.durationMs)
        assertEquals("https://video.example/a.m3u8", video.video?.hlsUrl)
        assertEquals(listOf("https://video.example/low.mp4"), video.video?.mp4Urls?.map { it.url })
        assertEquals(1_100_000L, video.viewCount)
        assertEquals(19_000L, video.likeCount)
        assertEquals("Just a text post about the ridge.", parsed.posts[1].text)
        assertNull(parsed.posts[1].video)
        assertEquals("NEXT_CURSOR_VALUE", parsed.cursor)
        assertTrue(parsed.profiles.any { it.handle == "oceanlab_sample" })
    }

    @Test
    fun malformedJsonIsUnreadableAndDomParserKeepsHttpsOnly() {
        assertTrue(TimelineJsonParser.parse("{") is ParseResult.Unreadable)
        val dom = DomFeedParser.parse(
            """
            {"tweets":[
              {"id":"9","text":"hello","name":"N","handle":"n","videoUrl":"blob:https://x.com/abc","photos":["https://pbs.twimg.com/a.jpg"]},
              {"id":"","text":""}
            ]}
            """.trimIndent(),
        ) as ParseResult.Ok
        assertEquals(1, dom.posts.size)
        assertTrue(dom.posts[0].video?.protectedStream == true)
        assertEquals("https://pbs.twimg.com/a.jpg", dom.posts[0].photos.single())
    }

    @Test
    fun playbackSelectorPrefersHlsAndCapsMp4() {
        val video = VideoMedia(
            videoId = "v",
            durationMs = 1,
            posterUrl = null,
            hlsUrl = "https://video.example/a.m3u8",
            mp4Urls = listOf(Mp4Variant("https://video.example/low.mp4", 800_000)),
            protectedStream = false,
        )
        val hls = PlaybackSelector.select(video, VideoQuality.Auto, dataSaver = false)
        assertEquals("https://video.example/a.m3u8", hls.url)
        assertEquals(PlaybackSelector.MIME_HLS, hls.mime)
        val mp4Only = video.copy(hlsUrl = null, mp4Urls = listOf(
            Mp4Variant("https://video.example/low.mp4", 800_000),
            Mp4Variant("https://video.example/mid.mp4", 2_000_000),
            Mp4Variant("https://video.example/high.mp4", 8_000_000),
        ))
        assertEquals(
            "https://video.example/mid.mp4",
            PlaybackSelector.bestMp4(mp4Only.mp4Urls, VideoQuality.Q720),
        )
        assertEquals(VideoQuality.Q720, PlaybackSelector.effectiveQuality(VideoQuality.Auto, dataSaver = true))
        val locked = VideoMedia("v", 1, null, null, emptyList(), protectedStream = true)
        assertTrue(PlaybackSelector.select(locked, VideoQuality.Auto, false).webFallback)
        assertNull(PlaybackSelector.select(locked, VideoQuality.Auto, false).url)
    }

    @Test
    fun historyResumeAndRecentList() {
        assertEquals(0L, HistoryRules.resumePosition(500, 10_000, true))
        assertEquals(4_000L, HistoryRules.resumePosition(4_000, 10_000, true))
        assertEquals(0L, HistoryRules.resumePosition(9_500, 10_000, true))
        assertEquals(0L, HistoryRules.resumePosition(4_000, 10_000, false))
        assertTrue(HistoryRules.isCompleted(9_000, 10_000))
        assertEquals(listOf("b", "a"), rememberRecent(listOf("a", "c"), "b", max = 2))
        assertEquals(listOf("A", "c"), rememberRecent(listOf("c", "a", "a"), "A", max = 2))
    }

    @Test
    fun sanitizerStripsSecretsAndCountsFormat() {
        val cleaned = Sanitizer.clean("auth_token=abc123&ok=1 \"password\":\"hunter2\"")
        assertFalse(cleaned.contains("abc123"))
        assertFalse(cleaned.contains("hunter2"))
        assertTrue(cleaned.contains("[redacted]"))
        assertNull(Sanitizer.safeToStoreUrl("https://x.com/a?auth_token=zzz"))
        assertNull(Sanitizer.usableMediaUrl("blob:https://x.com/1"))
        assertEquals("999", formatCount(999))
        assertEquals("1.2K", formatCount(1_200))
        assertEquals("8.4K", formatCount(8_400))
        assertEquals("19K", formatCount(19_000))
        assertEquals("412K", formatCount(412_000))
        assertEquals("1.1M", formatCount(1_100_000))
        assertEquals("0:21", formatClock(21_000))
        assertEquals("1:01:01", formatClock(3_661_000))
    }

    @Test
    fun loginHostPolicyAndEveryCodeIsDocumented() {
        assertTrue(XClient.hostAllowedForLogin("x.com"))
        assertTrue(XClient.hostAllowedForLogin("accounts.google.com"))
        assertTrue(XClient.hostAllowedForLogin("challenges.cloudflare.com"))
        assertTrue(XClient.needsConfirmation("https://x.com/account/access", "Just a moment..."))
        assertFalse(XClient.needsConfirmation("https://x.com/home?lang=en", "Home / X"))
        assertTrue(XClient.leftChallenge("https://x.com/home?lang=en"))
        assertFalse(XClient.leftChallenge("https://x.com/account/access"))
        assertFalse(XClient.leftChallenge("https://challenges.cloudflare.com/cdn-cgi/challenge"))
        assertFalse(XClient.hostAllowedForLogin("evil.example"))
        assertTrue(XClient.hostAllowedForMedia("video.twimg.com"))
        assertFalse(XClient.hostAllowedForMedia("accounts.google.com"))
        val readme = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "ERROR_CODES.md") }
            .first { it.exists() }
            .readText()
        ErrorCode.entries.forEach { code ->
            assertTrue("Missing ${code.code} in ERROR_CODES.md", readme.contains(code.code))
        }
    }
}
