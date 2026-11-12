package com.faz.xbyfaz

import android.app.Application
import android.webkit.CookieManager
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import com.faz.xbyfaz.core.network.WebViewCookieJar
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.io.File

@HiltAndroidApp
class XByFazApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            CookieManager.getInstance().setAcceptCookie(true)
        } catch (_: Throwable) {
            // Some TV images throw until a WebView exists. Login configures cookies again.
        }
    }

    fun installImageLoader(okHttp: OkHttpClient) {
        val cacheDir = File(cacheDir, "images").apply { mkdirs() }
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(okHttp)
                .diskCache(DiskCache.Builder().directory(cacheDir).maxSizeBytes(128L * 1024 * 1024).build())
                .crossfade(true)
                .build(),
        )
    }
}
