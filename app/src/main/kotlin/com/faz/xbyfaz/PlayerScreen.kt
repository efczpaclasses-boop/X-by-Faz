package com.faz.xbyfaz

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.faz.xbyfaz.core.model.AppSettings
import com.faz.xbyfaz.core.model.KeyRepeatGate
import com.faz.xbyfaz.core.model.XClient
import com.faz.xbyfaz.core.model.formatClock
import com.faz.xbyfaz.core.player.PlayMode as EngineMode
import com.faz.xbyfaz.core.player.PlaybackController
import com.faz.xbyfaz.core.player.WebCommand
import com.faz.xbyfaz.core.ui.Avatar
import com.faz.xbyfaz.core.ui.ErrorPanel
import com.faz.xbyfaz.core.ui.InterFamily
import com.faz.xbyfaz.core.ui.Kbd
import com.faz.xbyfaz.core.ui.XButton
import com.faz.xbyfaz.core.ui.XChip
import com.faz.xbyfaz.core.ui.XColors
import com.faz.xbyfaz.core.ui.imageModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen(
    controller: PlaybackController,
    settings: AppSettings,
    backLabel: String,
    onBack: () -> Unit,
    onNearEnd: () -> Unit,
    onToggleScale: () -> Unit,
) {
    val state by controller.ui.collectAsState()
    var position by remember { mutableLongStateOf(0L) }
    var visible by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var options by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    var downAt by remember { mutableLongStateOf(0L) }
    val upGate = remember { KeyRepeatGate(350) }
    val downGate = remember { KeyRepeatGate(350) }
    val seekGate = remember { KeyRepeatGate(180) }
    val focus = remember { FocusRequester() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val view = LocalView.current

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        runCatching { focus.requestFocus() }
        onDispose {
            view.keepScreenOn = false
            controller.pauseForLifecycle()
        }
    }
    LaunchedEffect(Unit) {
        controller.webCommands.collectLatest { command ->
            val script = when (command) {
                WebCommand.Toggle -> "(function(){var v=document.querySelector('video');if(!v)return 'novideo';if(v.paused){v.play();}else{v.pause();}return 'ok';})()"
                is WebCommand.Seek -> "(function(){var v=document.querySelector('video');if(!v)return 'novideo';v.currentTime=Math.max(0,(v.currentTime||0)+${command.deltaMs / 1000.0});return 'ok';})()"
            }
            webView?.evaluateJavascript(script) { raw -> if (raw?.contains("novideo") == true) controller.onWebFailed() }
        }
    }
    LaunchedEffect(poke, settings.controlsTimeoutSec, state.playing) {
        if (!state.playing) return@LaunchedEffect
        delay(settings.controlsTimeoutSec.coerceAtLeast(2) * 1000L)
        visible = false
    }
    LaunchedEffect(state.item?.videoId) {
        while (true) {
            position = controller.currentPosition()
            delay(250)
        }
    }
    LaunchedEffect(state.needsMore, state.item?.videoId) {
        if (state.needsMore) onNearEnd()
    }
    val duration = controller.duration().takeIf { it > 0 } ?: state.item?.durationMs ?: 0L
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (options && native.keyCode == KeyEvent.KEYCODE_BACK && native.action == KeyEvent.ACTION_DOWN) {
                    options = false
                    return@onPreviewKeyEvent true
                }
                if (options) return@onPreviewKeyEvent false
                if (native.action == KeyEvent.ACTION_UP && (native.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || native.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    if (!longFired) controller.toggle()
                    longFired = false
                    visible = true
                    poke++
                    return@onPreviewKeyEvent true
                }
                if (native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val now = android.os.SystemClock.uptimeMillis()
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> if (upGate.allow(now, native.repeatCount)) controller.previous()
                    KeyEvent.KEYCODE_DPAD_DOWN -> if (downGate.allow(now, native.repeatCount)) controller.next()
                    KeyEvent.KEYCODE_DPAD_LEFT -> if (seekGate.allow(now, native.repeatCount)) controller.seekBy(-10_000)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> if (seekGate.allow(now, native.repeatCount)) controller.seekBy(10_000)
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (native.repeatCount == 0) downAt = now
                        if (!longFired && now - downAt >= ViewConfiguration.getLongPressTimeout()) {
                            longFired = true
                            options = true
                        }
                    }
                    KeyEvent.KEYCODE_BACK -> onBack()
                    else -> return@onPreviewKeyEvent false
                }
                visible = true
                poke++
                true
            },
    ) {
        if (state.mode != EngineMode.Web) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        player = controller.player()
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        setKeepContentOnPlayerReset(true)
                        resizeMode = if (settings.scaleFill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = {
                    it.player = controller.player()
                    it.resizeMode = if (settings.scaleFill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
            )
        }
        if (state.mode == EngineMode.Web) {
            val permalink = state.item?.permalink
            if (permalink != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        try {
                        WebView(context).apply {
                            this.settings.mediaPlaybackRequiresUserGesture = false
                            com.faz.xbyfaz.core.web.BrowserIdentity.apply(this)
                            isFocusable = false
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String) {
                                    view.evaluateJavascript(WEB_BOOT) { raw ->
                                        if (raw?.contains("novideo") == true) controller.onWebFailed()
                                    }
                                }
                            }
                            loadUrl(permalink)
                            webView = this
                        }
                        } catch (_: Throwable) {
                            controller.onWebFailed()
                            android.view.View(context)
                        }
                    },
                )
            }
        }
        val item = state.item
        if (visible && settings.showOverlay && item != null && state.error == null) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(0.55f), Color.Transparent, Color.Transparent, Color.Black.copy(0.82f)))))
            Row(Modifier.fillMaxWidth().padding(horizontal = 72.dp, vertical = 36.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(backLabel, color = XColors.Text, fontFamily = InterFamily, fontSize = 18.sp, modifier = Modifier.background(Color.Black.copy(0.45f), CircleShape).padding(horizontal = 16.dp, vertical = 8.dp))
                Text("Queue   ${state.positionLabel}", color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.background(Color.Black.copy(0.45f), CircleShape).padding(horizontal = 16.dp, vertical = 8.dp))
            }
            state.next?.let { next ->
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 110.dp, end = 72.dp)
                        .width(360.dp)
                        .background(XColors.Surface1.copy(alpha = 0.78f), RoundedCornerShape(22.dp))
                        .padding(14.dp),
                ) {
                    Text("Up next", color = XColors.Text2, fontFamily = InterFamily, fontSize = 16.sp)
                    val poster = imageModel(next.posterUrl)
                    if (poster != null) {
                        AsyncImage(poster, contentDescription = null, modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(next.authorName, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(next.title, color = XColors.Text, fontFamily = InterFamily, fontSize = 18.sp, maxLines = 2)
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 72.dp, vertical = 48.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(item.authorName, item.authorHandle, item.avatarUrl, 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(item.authorName, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                        Text("@${item.authorHandle}", color = XColors.Text2, fontFamily = InterFamily, fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(item.title, color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, maxLines = 2)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = XColors.Black)
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(formatClock(position), color = XColors.Text, fontFamily = InterFamily, fontSize = 18.sp)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(0.22f))) {
                        Box(Modifier.fillMaxWidth(fraction).height(8.dp).background(XColors.Accent))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(formatClock(duration), color = XColors.Text2, fontFamily = InterFamily, fontSize = 18.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(settings.quality.label(), color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.borderBox())
                }
                if (settings.remoteHints) {
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Kbd("▲"); Text("Previous", color = XColors.Text2, fontFamily = InterFamily, fontSize = 16.sp)
                        Kbd("▼"); Text("Next", color = XColors.Text2, fontFamily = InterFamily, fontSize = 16.sp)
                        Kbd("◀ ▶"); Text("10s", color = XColors.Text2, fontFamily = InterFamily, fontSize = 16.sp)
                        Kbd("OK"); Text(if (state.playing) "Pause" else "Play", color = XColors.Text2, fontFamily = InterFamily, fontSize = 16.sp)
                        Kbd("Hold OK"); Text("Options", color = XColors.Text2, fontFamily = InterFamily, fontSize = 16.sp)
                    }
                }
            }
        }
        state.error?.let { failure ->
            Box(Modifier.align(Alignment.Center).padding(80.dp)) {
                ErrorPanel(failure, controller::retry, controller::skip)
            }
        }
        if (options) {
            Column(
                Modifier.align(Alignment.Center).background(XColors.Surface1, RoundedCornerShape(24.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Options", color = XColors.Text, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.75f, 1f, 1.25f, 1.5f).forEach { speed ->
                        XChip("${speed}x", state.speed == speed) { controller.setSpeed(speed) }
                    }
                }
                XButton(if (settings.scaleFill) "Scaling: Fill" else "Scaling: Fit", onToggleScale)
                XButton("Restart video", { controller.seekBy(-controller.currentPosition()); options = false })
                XButton("Close", { options = false }, primary = true)
            }
        }
        if (state.buffering && state.error == null) {
            Text("Buffering", color = XColors.Text2, fontFamily = InterFamily, fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
        }
    }
}

private fun Modifier.borderBox(): Modifier = this
    .clip(RoundedCornerShape(8.dp))
    .padding(horizontal = 8.dp, vertical = 4.dp)

@Suppress("unused")
private const val WEB_BOOT = """
(function(){
  var v = document.querySelector('video');
  if (!v) return 'novideo';
  v.muted = false;
  v.controls = false;
  v.style.position = 'fixed';
  v.style.left = '0';
  v.style.top = '0';
  v.style.width = '100vw';
  v.style.height = '100vh';
  v.style.zIndex = '2147483646';
  v.style.background = '#000';
  v.style.objectFit = 'contain';
  var play = v.play();
  if (play && play.catch) play.catch(function(){});
  return 'ok';
})()
"""
