package com.faz.xbyfaz

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.faz.xbyfaz.core.auth.SessionRepository
import com.faz.xbyfaz.core.database.HistoryRepository
import com.faz.xbyfaz.core.database.SettingsRepository
import com.faz.xbyfaz.core.model.ErrorCode
import com.faz.xbyfaz.core.model.FeedKind
import com.faz.xbyfaz.core.model.XClient
import com.faz.xbyfaz.core.model.HistoryRules
import com.faz.xbyfaz.core.model.Playable
import com.faz.xbyfaz.core.model.Post
import com.faz.xbyfaz.core.model.Sanitizer
import com.faz.xbyfaz.core.model.WatchEntry
import com.faz.xbyfaz.core.model.resolve
import com.faz.xbyfaz.core.model.toPlayable
import com.faz.xbyfaz.core.navigation.Dest
import com.faz.xbyfaz.core.player.PlaybackController
import com.faz.xbyfaz.core.ui.AppBackground
import com.faz.xbyfaz.core.ui.Avatar
import com.faz.xbyfaz.core.ui.InterFamily
import com.faz.xbyfaz.core.ui.LoadingState
import com.faz.xbyfaz.core.ui.ProvideXTheme
import com.faz.xbyfaz.core.ui.Wordmark
import com.faz.xbyfaz.core.ui.XColors
import com.faz.xbyfaz.core.web.ContentRepository
import com.faz.xbyfaz.core.web.EngineRouter
import com.faz.xbyfaz.core.web.XWebViewHost
import com.faz.xbyfaz.feature.history.HistoryRoute
import com.faz.xbyfaz.feature.history.toPlayable
import com.faz.xbyfaz.feature.home.BookmarksRoute
import com.faz.xbyfaz.feature.home.FollowingRoute
import com.faz.xbyfaz.feature.home.HomeRoute
import com.faz.xbyfaz.feature.home.LikesRoute
import com.faz.xbyfaz.feature.home.PostDetailRoute
import com.faz.xbyfaz.feature.profile.ProfileRoute
import com.faz.xbyfaz.feature.profile.ProfilesHomeRoute
import com.faz.xbyfaz.feature.search.SearchRoute
import com.faz.xbyfaz.feature.settings.SettingsRoute
import com.faz.xbyfaz.feature.videos.VideosRoute
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var okHttp: OkHttpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as XByFazApplication).installImageLoader(okHttp)
        val startDest = if (BuildConfig.DEBUG) intent.getStringExtra("dest") else null
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { AppRoot(startDest) }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShellEntry {
    fun session(): SessionRepository
    fun settings(): SettingsRepository
    fun content(): ContentRepository
    fun history(): HistoryRepository
    fun controller(): PlaybackController
    fun router(): EngineRouter
    fun host(): XWebViewHost
}

private data class RailItem(val dest: Dest, val label: String, val icon: ImageVector)

@Composable
private fun AppRoot(startDest: String?) {
    val context = LocalContext.current
    val shell = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, ShellEntry::class.java)
    }
    val session by shell.session().state.collectAsState()
    val settings by shell.settings().settings.collectAsState()
    val confirmation by shell.host().confirmation.collectAsState()
    var showPhone by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<com.faz.xbyfaz.core.model.AppFailure?>(null) }
    val stack = remember { mutableStateListOf<Dest>(Dest.Home) }
    var anchor by remember { mutableStateOf<String?>(null) }
    var feedKind by remember { mutableStateOf(FeedKind.Home) }
    val scope = rememberCoroutineScope()
    val loginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        scope.launch { shell.session().refresh() }
    }
    val current = stack.last()

    LaunchedEffect(Unit) {
        shell.session().refresh()
        if (startDest == "login") {
            loginLauncher.launch(Intent(context, LoginActivity::class.java))
        }
        if (startDest == "phone") showPhone = true
    }
    LaunchedEffect(session.checking, session.signedIn) {
        if (session.signedIn && session.handle.isNullOrBlank()) {
            val account = runCatching { shell.router().currentAccount() }.getOrNull()
            if (account != null) shell.session().rememberAccount(account.handle, account.displayName)
        }
    }
    LaunchedEffect(current, shell.controller().ui.collectAsState().value.item?.videoId) {
        if (current != Dest.Player) return@LaunchedEffect
        while (true) {
            delay(5_000)
            recordWatch(shell, settings.historyEnabled, session.handle)
        }
    }

    fun open(dest: Dest) {
        if (dest == Dest.Home || dest == Dest.Following || dest == Dest.Videos || dest == Dest.Bookmarks || dest == Dest.Likes || dest == Dest.Search || dest == Dest.Profiles || dest == Dest.History || dest == Dest.Settings) {
            stack.clear()
            stack.add(dest)
        } else {
            stack.add(dest)
        }
    }

    fun play(posts: List<Post>, startId: String, source: String, kind: FeedKind) {
        val playables = posts.mapNotNull { it.toPlayable(source) }
        if (playables.isEmpty()) return
        val startVideo = posts.firstOrNull { it.id == startId }?.toPlayable(source)?.videoId ?: playables.first().videoId
        anchor = startId
        feedKind = kind
        scope.launch {
            val saved = shell.history().get(startVideo)
            val position = if (saved == null) 0L else HistoryRules.resumePosition(saved.positionMs, saved.durationMs, shell.settings().current().resume)
            shell.controller().open(playables, startVideo, position)
            if (stack.last() != Dest.Player) stack.add(Dest.Player)
        }
    }

    BackHandler(enabled = confirmation != null) { shell.host().cancelConfirmation() }
    BackHandler(enabled = stack.size > 1 && confirmation == null) {
        if (stack.last() == Dest.Player) recordNow(scope, shell, settings.historyEnabled, session.handle)
        stack.removeAt(stack.lastIndex)
    }

    ProvideXTheme(settings.textScale.multiplier) {
        AppBackground {
            Box(Modifier.fillMaxSize()) {
            when {
                session.checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingState("Checking the saved session…") }
                !session.signedIn && showPhone -> PhoneQrScreen(
                    onCancel = { showPhone = false },
                    onContinueOnTv = {
                        showPhone = false
                        loginLauncher.launch(Intent(context, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_PHONE, true))
                    },
                )
                !session.signedIn -> LoginWelcome(
                    onSignIn = {
                        loginError = null
                        loginLauncher.launch(Intent(context, LoginActivity::class.java))
                    },
                    onPhone = { showPhone = true },
                    error = loginError,
                    onRetry = {
                        loginError = null
                        loginLauncher.launch(Intent(context, LoginActivity::class.java))
                    },
                    onDismissError = { loginError = null },
                )
                current == Dest.Player -> PlayerScreen(
                    controller = shell.controller(),
                    settings = settings,
                    backLabel = "Back",
                    onBack = {
                        recordNow(scope, shell, settings.historyEnabled, session.handle)
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    },
                    onNearEnd = {
                        scope.launch {
                            runCatching {
                                val page = shell.content().feed(feedKind, "more", session.handle)
                                shell.controller().append(page.posts.mapNotNull { it.toPlayable(feedKind.name) })
                            }
                        }
                    },
                    onToggleScale = { scope.launch { shell.settings().update { it.copy(scaleFill = !it.scaleFill) } } },
                )
                else -> Row(Modifier.fillMaxSize()) {
                    NavRail(current, session.handle, session.displayName) { open(it) }
                    Box(Modifier.weight(1f).fillMaxHeight().padding(start = 28.dp)) {
                        Clock(Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 48.dp))
                        when (val dest = current) {
                            Dest.Home -> HomeRoute({ posts, id -> play(posts, id, "home", FeedKind.Home) }, { open(Dest.PostDetail(it.id)) }, { open(Dest.Profile(it)) }, anchor)
                            Dest.Following -> FollowingRoute({ posts, id -> play(posts, id, "following", FeedKind.Following) }, { open(Dest.PostDetail(it.id)) }, { open(Dest.Profile(it)) })
                            Dest.Videos -> VideosRoute { posts, id -> play(posts, id, "videos", FeedKind.Videos) }
                            Dest.Bookmarks -> BookmarksRoute({ posts, id -> play(posts, id, "bookmarks", FeedKind.Bookmarks) }, { open(Dest.PostDetail(it.id)) }, { open(Dest.Profile(it)) })
                            Dest.Likes -> LikesRoute({ posts, id -> play(posts, id, "likes", FeedKind.Likes) }, { open(Dest.PostDetail(it.id)) }, { open(Dest.Profile(it)) })
                            Dest.Search -> SearchRoute({ posts, id -> play(posts, id, "search", FeedKind.Search) }, { open(Dest.Profile(it)) }, { open(Dest.PostDetail(it.id)) })
                            Dest.Profiles -> ProfilesHomeRoute { open(Dest.Profile(it)) }
                            is Dest.Profile -> ProfileRoute(dest.handle, { posts, id -> play(posts, id, "profile", FeedKind.ProfileVideos) }, { open(Dest.PostDetail(it.id)) })
                            Dest.History -> HistoryRoute(
                                onResume = { entry -> openHistory(scope, shell, entry, false) { stack.add(Dest.Player) } },
                                onRewatch = { entry -> openHistory(scope, shell, entry, true) { stack.add(Dest.Player) } },
                            )
                            Dest.Settings -> SettingsRoute(BuildConfig.VERSION_NAME) {
                                stack.clear()
                                stack.add(Dest.Home)
                            }
                            is Dest.PostDetail -> PostDetailRoute(dest.postId, { post -> play(listOf(post), post.id, "post", FeedKind.Home) }, { open(Dest.Profile(it)) })
                            Dest.Player -> Unit
                        }
                    }
                }
            }
            if (session.signedIn) ReaderHost(shell.host(), expanded = confirmation != null)
            }
        }
    }
}

private fun openHistory(scope: kotlinx.coroutines.CoroutineScope, shell: ShellEntry, entry: WatchEntry, fromStart: Boolean, onReady: () -> Unit) {
    scope.launch {
        val position = if (fromStart) 0L else HistoryRules.resumePosition(entry.positionMs, entry.durationMs, true)
        shell.controller().open(listOf(entry.toPlayable(fromStart)), entry.videoId, position)
        onReady()
    }
}

private fun recordNow(scope: kotlinx.coroutines.CoroutineScope, shell: ShellEntry, enabled: Boolean, handle: String?) {
    scope.launch { recordWatch(shell, enabled, handle) }
}

private suspend fun recordWatch(shell: ShellEntry, enabled: Boolean, handle: String?) {
    if (!enabled) return
    val state = shell.controller().ui.value
    val item = state.item ?: return
    val position = shell.controller().currentPosition()
    val duration = shell.controller().duration().takeIf { it > 0 } ?: item.durationMs
    val resolved = item.resolve(shell.settings().current().quality, shell.settings().current().dataSaver)
    val entry = WatchEntry(
        videoId = item.videoId,
        postId = item.postId,
        accountHandle = handle.orEmpty(),
        positionMs = position,
        durationMs = duration,
        updatedAt = System.currentTimeMillis(),
        completed = HistoryRules.isCompleted(position, duration),
        source = item.source,
        title = item.title,
        authorName = item.authorName,
        authorHandle = item.authorHandle,
        posterUrl = item.posterUrl,
        mediaUrl = Sanitizer.safeToStoreUrl(resolved.url),
        permalink = item.permalink,
    )
    runCatching { shell.history().upsert(entry) }
}

@Composable
private fun ReaderHost(host: XWebViewHost, expanded: Boolean) {
    val context = LocalContext.current
    val hook = remember {
        context.assets.open("engine/hook.js").bufferedReader().use { it.readText() }
    }
    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect
        while (isActive) {
            val url = host.current()?.url
            if (XClient.leftChallenge(url)) {
                host.passConfirmation()
                break
            }
            delay(400)
        }
    }
    Box(
        if (expanded) {
            Modifier.fillMaxSize().background(XColors.Bg)
        } else {
            Modifier.layout { measurable, _ ->
                val placeable = measurable.measure(Constraints.fixed(1280, 720))
                layout(1, 1) { placeable.place(-4000, -4000) }
            }
        },
    ) {
        AndroidView(
            modifier = if (expanded) Modifier.fillMaxSize().padding(top = 56.dp) else Modifier.fillMaxSize(),
            factory = { ctx ->
                try {
                    WebView(ctx).apply {
                        isFocusable = false
                        isFocusableInTouchMode = false
                        host.configure(this, hook)
                        host.attach(this)
                    }
                } catch (_: Throwable) {
                    android.view.View(ctx)
                }
            },
            update = { view ->
                if (view is WebView) {
                    view.isFocusable = expanded
                    view.isFocusableInTouchMode = expanded
                    view.setOnKeyListener { _, _, event ->
                        if (!expanded) false else challengeKey(host, event)
                    }
                    if (expanded) {
                        if (XClient.leftChallenge(view.url)) {
                            view.clearFocus()
                            host.passConfirmation()
                        } else {
                            view.requestFocus()
                            view.evaluateJavascript("try{document.documentElement.style.zoom='1.8'}catch(e){}", null)
                        }
                    } else {
                        view.isFocusable = false
                        view.isFocusableInTouchMode = false
                        view.clearFocus()
                        view.evaluateJavascript("try{document.documentElement.style.zoom='1'}catch(e){}", null)
                    }
                }
            },
        )
        if (expanded) {
            Text(
                "${ErrorCode.WEB_022.code}  X needs you to confirm it's you. Use arrows and OK. Back leaves this check.",
                color = XColors.Text,
                fontFamily = InterFamily,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.TopStart).background(XColors.Surface1).padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            )
        }
    }
}

private fun challengeKey(host: XWebViewHost, event: KeyEvent): Boolean {
    val web = host.current() ?: return false
    val down = event.action == KeyEvent.ACTION_DOWN
    if (event.keyCode == KeyEvent.KEYCODE_BACK && down) {
        host.cancelConfirmation()
        return true
    }
    if (!down) {
        return event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
    }
    web.isFocusable = true
    web.isFocusableInTouchMode = true
    web.requestFocus()
    when (event.keyCode) {
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> web.evaluateJavascript(moveFocus("next"), null)
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> web.evaluateJavascript(moveFocus("prev"), null)
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
            if (event.repeatCount == 0) {
                web.evaluateJavascript(CHALLENGE_ACTIVATE) { raw ->
                    val result = raw?.trim('"').orEmpty()
                    if (result == "input") {
                        web.postDelayed({
                            web.requestFocus()
                            web.context.getSystemService(InputMethodManager::class.java)
                                ?.showSoftInput(web, InputMethodManager.SHOW_IMPLICIT)
                        }, 150)
                    } else if (result.startsWith("tap:")) {
                        val parts = result.removePrefix("tap:").split(",")
                        val x = parts.getOrNull(0)?.toFloatOrNull()
                        val y = parts.getOrNull(1)?.toFloatOrNull()
                        if (x != null && y != null) tapWeb(web, x, y)
                    }
                }
            }
        }
        else -> return false
    }
    return true
}

private fun tapWeb(web: WebView, cssX: Float, cssY: Float) {
    val density = web.resources.displayMetrics.density
    val x = cssX * density
    val y = cssY * density
    val now = SystemClock.uptimeMillis()
    val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
    val up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0)
    web.dispatchTouchEvent(down)
    web.dispatchTouchEvent(up)
    down.recycle()
    up.recycle()
}

private const val CHALLENGE_ACTIVATE = """
(function(){
  function asField(el){
    var node = el;
    while (node && node !== document.body) {
      if (!node) break;
      if (node.tagName === 'INPUT' || node.tagName === 'TEXTAREA' || node.isContentEditable) return node;
      node = node.parentElement;
    }
    return null;
  }
  var field = asField(document.activeElement);
  if (field) {
    try { field.focus(); } catch (e) {}
    return 'input';
  }
  var frame = document.querySelector('iframe[src*="challenges.cloudflare"], iframe[src*="turnstile"], iframe[title*="Widget"], .cf-turnstile iframe, iframe');
  var el = frame || document.querySelector('input[type=checkbox], [role=checkbox], button, [role=button]');
  if (!el) return 'none';
  try { el.tabIndex = 0; el.focus(); } catch (e) {}
  var r = el.getBoundingClientRect();
  return 'tap:' + (r.left + r.width / 2) + ',' + (r.top + r.height / 2);
})()
"""

@Composable
private fun NavRail(current: Dest, handle: String?, name: String?, onSelect: (Dest) -> Unit) {
    val items = listOf(
        RailItem(Dest.Home, "Home", Icons.Filled.Home),
        RailItem(Dest.Following, "Following", Icons.Filled.Groups),
        RailItem(Dest.Videos, "Videos", Icons.Filled.PlayArrow),
        RailItem(Dest.Bookmarks, "Bookmarks", Icons.Filled.Bookmark),
        RailItem(Dest.Likes, "Likes", Icons.Filled.Favorite),
        RailItem(Dest.Search, "Search", Icons.Filled.Search),
        RailItem(Dest.Profiles, "Profiles", Icons.Filled.Person),
        RailItem(Dest.History, "History", Icons.Filled.History),
        RailItem(Dest.Settings, "Settings", Icons.Filled.Settings),
    )
    Column(
        Modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color(0xF2080A0D), androidx.compose.ui.graphics.Color.Transparent)))
            .padding(start = 12.dp, top = 12.dp, bottom = 10.dp, end = 6.dp),
    ) {
        Wordmark()
        Spacer(Modifier.height(8.dp))
        items.forEach { item ->
            val selected = sameRail(current, item.dest)
            com.faz.xbyfaz.core.ui.XChip(item.label, selected, item.icon) { onSelect(item.dest) }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
            Avatar(name ?: handle ?: "You", handle ?: "you", null, 28.dp)
            Text(
                handle?.let { "@$it" } ?: "Signed in",
                color = XColors.Text2,
                fontFamily = InterFamily,
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

private fun sameRail(current: Dest, item: Dest): Boolean {
    if (current == item) return true
    if (item == Dest.Profiles && current is Dest.Profile) return true
    return false
}

@Composable
private fun Clock(modifier: Modifier) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            delay(20_000)
        }
    }
    Text(text, color = XColors.Text, fontFamily = InterFamily, fontSize = 16.sp, modifier = modifier)
}
