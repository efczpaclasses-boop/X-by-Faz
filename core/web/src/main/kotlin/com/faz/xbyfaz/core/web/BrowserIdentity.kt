package com.faz.xbyfaz.core.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * One browser identity for every WebView. Cloudflare ties cf_clearance to the
 * user agent, and a newer Chrome version than this WebView's engine makes the
 * human check start over.
 */
object BrowserIdentity {
    fun userAgent(context: Context): String {
        return WebSettings.getDefaultUserAgent(context).replace("; wv", "")
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun apply(webView: WebView) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.loadsImagesAutomatically = true
        settings.userAgentString = userAgent(webView.context)
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, true)
        cookies.flush()
        if (webView.webChromeClient == null) {
            webView.webChromeClient = SharedWindows()
        }
    }

    private class SharedWindows : WebChromeClient() {
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            val popup = WebView(view.context)
            apply(popup)
            popup.webChromeClient = this
            popup.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(child: WebView, request: WebResourceRequest): Boolean {
                    view.loadUrl(request.url.toString())
                    return true
                }
            }
            val transport = resultMsg.obj as WebView.WebViewTransport
            transport.webView = popup
            resultMsg.sendToTarget()
            return true
        }
    }
}
