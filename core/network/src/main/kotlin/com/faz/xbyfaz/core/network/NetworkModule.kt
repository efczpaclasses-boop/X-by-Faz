package com.faz.xbyfaz.core.network

import android.webkit.CookieManager
import com.faz.xbyfaz.core.model.XClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Media client. Cookies are copied from the WebView jar only for X media hosts
 * and are never logged. No interceptor prints headers.
 */
class WebViewCookieJar : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!XClient.hostAllowedForMedia(url.host)) return
        val manager = cookieManager() ?: return
        cookies.forEach { manager.setCookie(url.toString(), it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!XClient.hostAllowedForMedia(url.host)) return emptyList()
        val raw = try {
            cookieManager()?.getCookie(url.toString())
        } catch (_: Throwable) {
            null
        } ?: return emptyList()
        return raw.split(';').mapNotNull { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) null else Cookie.parse(url, trimmed)
        }
    }

    private fun cookieManager(): CookieManager? = try {
        CookieManager.getInstance()
    } catch (_: Throwable) {
        null
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(WebViewCookieJar())
        .retryOnConnectionFailure(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}
