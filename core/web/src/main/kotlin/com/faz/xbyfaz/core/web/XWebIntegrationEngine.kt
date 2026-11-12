package com.faz.xbyfaz.core.web

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.faz.xbyfaz.core.database.DiagnosticsRepository
import com.faz.xbyfaz.core.model.AppFailure
import com.faz.xbyfaz.core.model.ErrorCode
import com.faz.xbyfaz.core.model.FeedKind
import com.faz.xbyfaz.core.model.FeedPage
import com.faz.xbyfaz.core.model.ParseResult
import com.faz.xbyfaz.core.model.Post
import com.faz.xbyfaz.core.model.Profile
import com.faz.xbyfaz.core.model.Sanitizer
import com.faz.xbyfaz.core.model.SearchPage
import com.faz.xbyfaz.core.model.TimelineJsonParser
import com.faz.xbyfaz.core.model.XClient
import com.faz.xbyfaz.core.model.DomFeedParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class XWebIntegrationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val host: XWebViewHost,
    private val diagnostics: DiagnosticsRepository,
) : WebsiteIntegrationEngine {
    override val id: String = "x-web-v1"
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var lastUrl: String? = null
    private var sawMalformed = false

    private val config: EngineConfig by lazy { loadConfig() }

    override suspend fun feed(kind: FeedKind, cursor: String?, handle: String?): FeedPage = guard(kind.name) {
        val url = pageFor(kind, handle)
        val result = read(url, continuation = cursor != null, following = kind == FeedKind.Following)
        toFeed(kind, result)
    }

    override suspend fun search(query: String, cursor: String?): SearchPage = guard("search") {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@guard SearchPage(emptyList(), emptyList(), null)
        val encoded = URLEncoder.encode(trimmed, Charsets.UTF_8.name())
        val url = config.searchUrl.replace("{query}", encoded)
        val result = read(url, continuation = cursor != null, following = false)
        if (isLogin(result.finalUrl)) throw sessionExpired()
        if (result.posts.isEmpty() && result.profiles.isEmpty() && sawMalformed) {
            return@guard SearchPage(emptyList(), emptyList(), null, warning(ErrorCode.WEB_014, "Search results could not be read."))
        }
        SearchPage(result.profiles.take(8), result.posts, result.cursor)
    }

    override suspend fun profile(handle: String): Profile = guard("profile") {
        val safe = requireHandle(handle)
        val result = read(config.profileUrl.replace("{handle}", safe), continuation = false, following = false)
        if (isLogin(result.finalUrl)) throw sessionExpired()
        result.profiles.firstOrNull { it.handle.equals(safe, true) }
            ?: result.profiles.firstOrNull()
            ?: throw AppFailure(ErrorCode.WEB_013, "That profile couldn't be read.", safe)
    }

    override suspend fun profileFeed(handle: String, videosOnly: Boolean, cursor: String?): FeedPage = guard("profile-feed") {
        val safe = requireHandle(handle)
        val url = config.profileUrl.replace("{handle}", safe) + if (videosOnly) "/media" else ""
        val result = read(url, continuation = cursor != null, following = false)
        val page = toFeed(if (videosOnly) FeedKind.ProfileVideos else FeedKind.ProfilePosts, result)
        if (videosOnly) page.copy(posts = page.posts.filter { it.hasVideo }) else page
    }

    override suspend fun post(id: String): Post = guard("post") {
        if (!id.all { it.isDigit() }) throw AppFailure(ErrorCode.WEB_015, "That post id isn't readable.", id)
        val result = read(config.statusUrl.replace("{id}", id), continuation = false, following = false)
        result.posts.firstOrNull { it.id == id }
            ?: result.posts.firstOrNull()
            ?: throw AppFailure(ErrorCode.WEB_015, "The video details couldn't be read from the post.", id)
    }

    override suspend fun currentAccount(): AccountIdentity? = try {
        guard("account") {
            val result = read(config.homeUrl, continuation = false, following = false)
            val fromDom = evalAccount()
            val handle = fromDom?.handle ?: result.profiles.firstOrNull()?.handle
            if (handle.isNullOrBlank()) null else AccountIdentity(handle, fromDom?.displayName ?: result.profiles.firstOrNull()?.name)
        }
    } catch (_: Throwable) {
        null
    }

    private suspend fun <T> guard(screen: String, block: suspend () -> T): T {
        try {
            return block()
        } catch (t: TimeoutCancellationException) {
            val failure = AppFailure(ErrorCode.WEB_010, "x.com took too long to respond.", "timeout", t)
            diagnostics.record(failure, screen)
            throw failure
        } catch (t: CancellationException) {
            throw t
        } catch (t: AppFailure) {
            diagnostics.record(t, screen)
            throw t
        } catch (t: Throwable) {
            val failure = AppFailure(ErrorCode.APP_001, "Something went wrong reading X.", t.javaClass.simpleName, t)
            diagnostics.record(failure, screen)
            throw failure
        }
    }

    private suspend fun read(url: String, continuation: Boolean, following: Boolean): ReadResult = mutex.withLock {
        withContext(Dispatchers.Main) {
            val webView = try {
                host.view()
            } catch (t: Throwable) {
                throw AppFailure(ErrorCode.WEB_001, "The page reader isn't ready.", t.javaClass.simpleName, t)
            }
            val samePage = continuation && lastUrl == url
            if (!samePage) {
                openForReading(webView, url)
            }
            withTimeout(28_000) {
                sawMalformed = false
                val channel = Channel<String>(capacity = 8)
                host.setListener { body ->
                    val redacted = Sanitizer.redact(body)
                    if (redacted.length in 1..2_000_000) channel.trySend(redacted)
                }
                try {
                    if (!samePage) {
                        webView.evaluateJavascript(hookSource(), null)
                        webView.evaluateJavascript(MUTE_JS, null)
                        lastUrl = url
                    } else {
                        webView.evaluateJavascript("window.scrollTo(0, Math.max(document.body.scrollHeight, 4000));", null)
                    }
                    if (XClient.needsConfirmation(webView.url, pageTitle(webView))) {
                        throw AppFailure(ErrorCode.WEB_022, "X needs you to confirm it's you.", webView.url)
                    }
                    if (following) selectFollowing(webView)
                    val graphql = collect(channel)
                    if (graphql == null || graphql.posts.isEmpty()) {
                        val domRaw = eval(webView, domSource())
                        val dom = DomFeedParser.parse(domRaw)
                        val domOk = dom as? ParseResult.Ok
                        ReadResult(
                            posts = domOk?.posts ?: graphql?.posts.orEmpty(),
                            profiles = graphql?.profiles.orEmpty(),
                            cursor = domOk?.cursor ?: graphql?.cursor,
                            finalUrl = host.pageUrl,
                            sawShape = graphql != null || sawMalformed,
                        )
                    } else {
                        ReadResult(graphql.posts, graphql.profiles, graphql.cursor, host.pageUrl, true)
                    }
                } finally {
                    host.clearListener()
                    channel.close()
                }
            }
        }
    }

    private suspend fun selectFollowing(webView: WebView) {
        val labels = config.followingLabels.joinToString(",") { "\"" + it.replace("\"", "") + "\"" }
        val js = """
            (function(){
              var labels = [$labels];
              var nodes = [].slice.call(document.querySelectorAll('[role="tab"],a,span,div'));
              var t = nodes.find(function(el){
                var value = (el.innerText || '').trim();
                return labels.indexOf(value) >= 0;
              });
              if (!t) return 'no';
              t.click();
              return 'yes';
            })()
        """.trimIndent()
        val clicked = eval(webView, js)
        if (clicked != "yes") {
            throw AppFailure(ErrorCode.WEB_019, "The Following tab wasn't available on the home page.", null)
        }
        delay(900)
    }

    private suspend fun collect(channel: Channel<String>): ParseResult.Ok? {
        val deadline = SystemClock.elapsedRealtime() + 12_000
        var best: ParseResult.Ok? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            val wait = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1)
            val body = kotlinx.coroutines.withTimeoutOrNull(wait) { channel.receive() } ?: break
            when (val parsed = TimelineJsonParser.parse(body)) {
                ParseResult.Unreadable -> sawMalformed = true
                is ParseResult.Ok -> if (parsed.posts.size >= (best?.posts?.size ?: 0) && (parsed.posts.isNotEmpty() || parsed.profiles.isNotEmpty())) {
                    best = parsed
                    if (parsed.posts.size >= 4) break
                }
            }
        }
        return best
    }

    private suspend fun openForReading(webView: WebView, url: String) {
        val finished = load(webView, url)
        if (XClient.needsConfirmation(finished, pageTitle(webView))) {
            awaitConfirmation(webView)
            val again = load(webView, url)
            if (XClient.needsConfirmation(again, pageTitle(webView))) {
                throw AppFailure(ErrorCode.WEB_022, "X needs you to confirm it's you.", again)
            }
        }
        if (!XClient.isXHost(Uri.parse(webView.url ?: finished).host)) {
            throw AppFailure(ErrorCode.WEB_020, "The reader stopped because the page left X.", Uri.parse(finished).host)
        }
    }

    private suspend fun awaitConfirmation(webView: WebView) {
        host.showConfirmation(webView.url ?: host.pageUrl)
        webView.evaluateJavascript(CHECK_ZOOM, null)
        var seenRay: String? = null
        try {
            val deadline = SystemClock.elapsedRealtime() + 6 * 60_000
            while (SystemClock.elapsedRealtime() < deadline) {
                if (host.confirmationCancelled) {
                    throw AppFailure(ErrorCode.WEB_022, "X needs you to confirm it's you.", webView.url)
                }
                if (host.challengeReloads >= 3) throw checkFailed()
                val current = webView.url.orEmpty()
                if (host.confirmationPassed || XClient.leftChallenge(current)) {
                    CookieManager.getInstance().flush()
                    return
                }
                delay(400)
                val again = webView.url.orEmpty()
                if (host.confirmationPassed || XClient.leftChallenge(again)) {
                    CookieManager.getInstance().flush()
                    return
                }
                if (XClient.needsConfirmation(again, webView.title)) {
                    val ray = rayId(webView)
                    val token = ray.ifBlank { again }
                    if (token.isNotBlank() && token != seenRay) {
                        if (seenRay != null) host.noteChallengeReload()
                        seenRay = token
                    }
                }
                if (host.challengeReloads >= 3) throw checkFailed()
            }
            throw AppFailure(ErrorCode.WEB_022, "X still needs you to confirm it's you.", webView.url)
        } finally {
            webView.evaluateJavascript("try{document.documentElement.style.zoom='1'}catch(e){}", null)
            host.clearConfirmation()
        }
    }

    private fun checkFailed() = AppFailure(
        ErrorCode.WEB_023,
        "This check started over three times. Update Android System WebView from the Play Store.",
        null,
    )

    private suspend fun rayId(webView: WebView): String = runCatching {
        eval(webView, "(function(){var t=(document.body&&document.body.innerText)||''; var m=t.match(/Ray ID[:\\s]*([A-Za-z0-9]+)/i); return m?m[1]:'';})()")
    }.getOrDefault("")

    private suspend fun pageTitle(webView: WebView): String = runCatching {
        eval(webView, "(function(){return document.title || ''})()")
    }.getOrDefault("")

    private suspend fun load(webView: WebView, url: String): String = suspendCancellableCoroutine { cont ->
        host.markNavigation(url)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                host.noteMainFrame(url)
            }

            override fun onPageFinished(view: WebView, finishedUrl: String) {
                host.noteMainFrame(finishedUrl)
                if (host.confirmation.value != null) view.evaluateJavascript(CHECK_ZOOM, null)
                if (cont.isActive) cont.resume(finishedUrl)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                host.noteMainFrame(url)
            }

            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                if (cont.isActive) {
                    cont.resumeWithException(AppFailure(ErrorCode.WEB_010, "The page closed.", null, null))
                }
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && cont.isActive) {
                    cont.resumeWithException(
                        AppFailure(ErrorCode.WEB_010, "The page failed to load.", error.description?.toString(), null),
                    )
                }
            }
        }
        webView.loadUrl(url)
    }

    private suspend fun eval(webView: WebView, script: String): String = suspendCancellableCoroutine { cont ->
        webView.evaluateJavascript(script) { raw ->
            if (cont.isActive) cont.resume(unwrap(raw))
        }
    }

    private suspend fun evalAccount(): AccountIdentity? {
        val webView = host.current() ?: return null
        val raw = eval(
            webView,
            """
            (function(){
              var a = document.querySelector("a[data-testid='AppTabBar_Profile_Link']");
              var href = a ? (a.getAttribute('href') || '') : '';
              var handle = href.split('/').filter(Boolean)[0] || '';
              var name = a ? (a.getAttribute('aria-label') || '') : '';
              return JSON.stringify({handle: handle, name: name});
            })()
            """.trimIndent(),
        )
        return try {
            val obj = json.parseToJsonElement(raw) as? JsonObject ?: return null
            val handle = obj["handle"]?.jsonPrimitive?.contentOrNull?.removePrefix("@").orEmpty()
            if (handle.isBlank()) null else AccountIdentity(handle, obj["name"]?.jsonPrimitive?.contentOrNull)
        } catch (_: Exception) {
            null
        }
    }

    private fun toFeed(kind: FeedKind, result: ReadResult): FeedPage {
        if (XClient.needsConfirmation(result.finalUrl)) {
            throw AppFailure(ErrorCode.WEB_022, "X needs you to confirm it's you.", result.finalUrl)
        }
        if (isLogin(result.finalUrl)) throw sessionExpired()
        if (result.posts.isEmpty() && result.sawShape && sawMalformed) {
            return FeedPage(emptyList(), null, warning(codeFor(kind), "The page didn't match the reader."))
        }
        if (result.posts.isEmpty() && result.sawShape && kind == FeedKind.Home) {
            return FeedPage(emptyList(), null, warning(ErrorCode.WEB_012, "The timeline response didn't include readable posts."))
        }
        if (result.posts.isEmpty() && !result.sawShape) {
            val code = if (kind == FeedKind.Home) ErrorCode.WEB_011 else codeFor(kind)
            return FeedPage(emptyList(), null, warning(code, code.ownerHint))
        }
        return FeedPage(result.posts, result.cursor)
    }

    private fun codeFor(kind: FeedKind): ErrorCode = when (kind) {
        FeedKind.Bookmarks -> ErrorCode.WEB_017
        FeedKind.Likes -> ErrorCode.WEB_018
        FeedKind.Following -> ErrorCode.WEB_019
        FeedKind.Search -> ErrorCode.WEB_014
        FeedKind.ProfilePosts, FeedKind.ProfileVideos -> ErrorCode.WEB_013
        else -> ErrorCode.WEB_011
    }

    private fun warning(code: ErrorCode, message: String) = AppFailure(code, message, null)

    private fun sessionExpired() = AppFailure(ErrorCode.AUTH_003, "Your X session has expired. Sign in again.", "login")

    private fun isLogin(url: String): Boolean {
        val path = Uri.parse(url).path.orEmpty()
        return path.contains("/i/flow/login") || path.startsWith("/login")
    }

    private fun pageFor(kind: FeedKind, handle: String?): String = when (kind) {
        FeedKind.Home, FeedKind.Videos, FeedKind.Following -> config.homeUrl
        FeedKind.Bookmarks -> config.bookmarksUrl
        FeedKind.Likes -> {
            val safe = handle?.let { runCatching { requireHandle(it) }.getOrNull() }
                ?: throw AppFailure(ErrorCode.WEB_018, "Likes need the signed-in handle. Open Account and sign in again.", null)
            config.likesUrl.replace("{handle}", safe)
        }
        FeedKind.ProfilePosts, FeedKind.ProfileVideos, FeedKind.Search -> config.homeUrl
    }

    private fun requireHandle(handle: String): String {
        val safe = handle.trim().removePrefix("@")
        if (!safe.matches(Regex("[A-Za-z0-9_]{1,15}"))) {
            throw AppFailure(ErrorCode.WEB_013, "That handle doesn't look right.", safe.take(32))
        }
        return safe
    }

    private fun loadConfig(): EngineConfig {
        return try {
            val text = context.assets.open("engine/x-web-v1.json").bufferedReader().use { it.readText() }
            val obj = json.parseToJsonElement(text) as JsonObject
            fun str(key: String) = obj[key]?.jsonPrimitive?.contentOrNull ?: error(key)
            EngineConfig(
                id = str("id"),
                homeUrl = str("homeUrl"),
                bookmarksUrl = str("bookmarksUrl"),
                searchUrl = str("searchUrl"),
                profileUrl = str("profileUrl"),
                likesUrl = str("likesUrl"),
                statusUrl = str("statusUrl"),
                article = str("article"),
                userName = str("userName"),
                tweetText = str("tweetText"),
                followingLabels = listOf("Following"),
            )
        } catch (t: Throwable) {
            throw AppFailure(ErrorCode.WEB_001, "The website integration config could not be read.", t.javaClass.simpleName, t)
        }
    }

    private fun hookSource(): String = context.assets.open("engine/hook.js").bufferedReader().use { it.readText() }

    private fun domSource(): String {
        val cfg = buildString {
            append("{")
            append("\"article\":").append(quote(config.article)).append(",")
            append("\"userName\":").append(quote(config.userName)).append(",")
            append("\"tweetText\":").append(quote(config.tweetText))
            append("}")
        }
        val template = context.assets.open("engine/dom.js").bufferedReader().use { it.readText() }
        return template.replace("__CONFIG__", cfg)
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(ch)
            }
        }
        append('"')
    }

    private fun unwrap(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        return try {
            val el = json.parseToJsonElement(raw)
            if (el is kotlinx.serialization.json.JsonPrimitive && el.isString) el.content else raw
        } catch (_: Exception) {
            raw.trim('"')
        }
    }

    private data class ReadResult(
        val posts: List<Post>,
        val profiles: List<Profile>,
        val cursor: String?,
        val finalUrl: String,
        val sawShape: Boolean,
    )

    private data class EngineConfig(
        val id: String,
        val homeUrl: String,
        val bookmarksUrl: String,
        val searchUrl: String,
        val profileUrl: String,
        val likesUrl: String,
        val statusUrl: String,
        val article: String,
        val userName: String,
        val tweetText: String,
        val followingLabels: List<String>,
    )

    private companion object {
        const val MUTE_JS = "(function(){document.querySelectorAll('video').forEach(function(v){v.muted=true;try{v.pause();}catch(e){}});return 'ok';})()"
        const val CHECK_ZOOM = "try{document.documentElement.style.zoom='1.8'}catch(e){}"
    }
}
