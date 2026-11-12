package com.faz.xbyfaz.core.web

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.faz.xbyfaz.core.model.XClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XWebViewHost @Inject constructor() {
    private val viewRef = AtomicReference<WebView?>(null)
    private val attached = MutableSharedFlow<WebView>(replay = 1, extraBufferCapacity = 1)
    @Volatile var pageUrl: String = ""
    @Volatile var originAllowed: Boolean = false
    private val confirmationState = MutableStateFlow<String?>(null)
    val confirmation: StateFlow<String?> = confirmationState.asStateFlow()
    @Volatile var confirmationCancelled: Boolean = false
    @Volatile var confirmationPassed: Boolean = false
    @Volatile var challengeReloads: Int = 0
    private val listener = AtomicReference<(String) -> Unit> {}
    private var hookInstalled = false

    fun attach(webView: WebView) {
        viewRef.set(webView)
        attached.tryEmit(webView)
    }

    fun detach(webView: WebView) {
        viewRef.compareAndSet(webView, null)
    }

    suspend fun view(): WebView = viewRef.get() ?: attached.first()

    fun current(): WebView? = viewRef.get()

    fun setListener(block: (String) -> Unit) {
        listener.set(block)
    }

    fun clearListener() {
        listener.set {}
    }

    fun onTimeline(payload: String) {
        if (!originAllowed) return
        listener.get().invoke(payload)
    }

    fun markNavigation(url: String) {
        pageUrl = url
        originAllowed = XClient.isXHost(android.net.Uri.parse(url).host)
    }

    fun showConfirmation(url: String) {
        confirmationCancelled = false
        confirmationPassed = false
        challengeReloads = 0
        confirmationState.value = url
    }

    fun noteMainFrame(url: String?) {
        if (url.isNullOrBlank()) return
        markNavigation(url)
        if (confirmationState.value != null && XClient.leftChallenge(url)) {
            passConfirmation()
        }
    }

    fun passConfirmation() {
        confirmationPassed = true
        CookieManager.getInstance().flush()
        confirmationState.value = null
    }

    fun noteChallengeReload() {
        challengeReloads += 1
    }

    fun clearConfirmation() {
        confirmationState.value = null
    }

    fun cancelConfirmation() {
        confirmationCancelled = true
        confirmationState.value = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView, hook: String) {
        val settings = webView.settings
        settings.mediaPlaybackRequiresUserGesture = true
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false
        webView.setInitialScale(100)
        BrowserIdentity.apply(webView)
        webView.addJavascriptInterface(TimelineBridge { onTimeline(it) }, "XByFazBridge")
        if (!hookInstalled && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                hook,
                setOf("https://x.com", "https://twitter.com", "https://mobile.x.com", "https://mobile.twitter.com"),
            )
            hookInstalled = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean = true
        }
    }

    fun clear() {
        current()?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            clearCache(true)
        }
        pageUrl = ""
        originAllowed = false
    }
}

class TimelineBridge(private val onPayload: (String) -> Unit) {
    @android.webkit.JavascriptInterface
    fun onTimeline(payload: String?) {
        if (payload.isNullOrBlank()) return
        onPayload(payload)
    }
}
