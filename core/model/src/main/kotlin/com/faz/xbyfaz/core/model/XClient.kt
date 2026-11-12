package com.faz.xbyfaz.core.model

object XClient {
    const val LOGIN_URL = "https://x.com/i/flow/login"
    const val HOME_URL = "https://x.com/home"
    const val BOOKMARKS_URL = "https://x.com/i/bookmarks"
    /** Media downloads only. WebViews use the device WebView's own user agent. */
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    /**
     * Tablet Chrome, no WebView token and no desktop viewport.
     * A desktop page at 320 dpi asks WebView 113 for more tile memory than it will draw.
     */
    const val LOGIN_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel Tablet) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.5672.136 Safari/537.36"

    private val loginHosts = listOf(
        "x.com",
        "twitter.com",
        "twimg.com",
        "google.com",
        "gstatic.com",
        "recaptcha.net",
        "arkoselabs.com",
        "funcaptcha.com",
        "challenges.cloudflare.com",
        "cloudflare.com",
    )

    private val mediaHosts = listOf("x.com", "twitter.com", "twimg.com")

    fun hostAllowedForLogin(host: String?): Boolean = matches(host, loginHosts)

    fun hostAllowedForMedia(host: String?): Boolean = matches(host, mediaHosts)

    fun isXHost(host: String?): Boolean {
        val h = host?.lowercase().orEmpty()
        return h == "x.com" || h.endsWith(".x.com") || h == "twitter.com" || h.endsWith(".twitter.com")
    }

    fun hostOf(url: String?): String {
        val raw = url?.lowercase()?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val afterScheme = if ("://" in raw) raw.substringAfter("://") else raw
        return afterScheme.substringBefore('/').substringBefore('?').substringBefore(':')
    }

    /** Path only. `?lang=en` and hashes are ignored. */
    fun pathOf(url: String?): String {
        val raw = url?.lowercase()?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val afterScheme = if ("://" in raw) raw.substringAfter("://") else raw
        val clean = afterScheme.substringAfter('/', "").substringBefore('?').substringBefore('#')
        return if (clean.isEmpty()) "/" else "/$clean"
    }

    /**
     * The human-check page is finished. A normal x.com URL such as
     * https://x.com/home?lang=en counts. Title text does not.
     */
    fun leftChallenge(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = hostOf(url)
        if (host == "challenges.cloudflare.com" || host.endsWith(".challenges.cloudflare.com")) return false
        if (!isXHost(host)) return false
        val path = pathOf(url)
        if (path.startsWith("/account")) return false
        return true
    }

    /** X or Cloudflare is asking the person to continue. The app must not solve this itself. */
    fun needsConfirmation(url: String?, title: String? = null): Boolean {
        if (leftChallenge(url)) return false
        val raw = url?.lowercase().orEmpty()
        val path = pathOf(url)
        val heading = title?.lowercase().orEmpty()
        if (path.startsWith("/account")) return true
        if (path.contains("/i/flow/") || path.contains("/i/jf/")) return true
        if (path.startsWith("/login")) return true
        if (raw.contains("challenges.cloudflare.com") || raw.contains("turnstile")) return true
        if (heading.contains("just a moment") || heading.contains("verify you are human")) return true
        return false
    }

    private fun matches(host: String?, suffixes: List<String>): Boolean {
        val h = host?.lowercase()?.trim().orEmpty()
        if (h.isEmpty()) return false
        return suffixes.any { suffix -> h == suffix || h.endsWith(".$suffix") }
    }
}
