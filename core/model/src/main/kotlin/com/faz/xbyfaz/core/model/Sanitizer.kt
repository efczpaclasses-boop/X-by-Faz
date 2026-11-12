package com.faz.xbyfaz.core.model

object Sanitizer {
    private val assignment = Regex(
        "(?i)(auth_token|ct0|password|authorization|cookie|x-guest-token|secret|oauth_token|email|phone)=([^\\s&\"']+)",
    )
    private val jsonField = Regex(
        "(?i)\"(auth_token|ct0|password|authorization|cookie|x-guest-token|secret|oauth_token|email|phone)\"\\s*:\\s*\"[^\"]*\"",
    )

    fun redact(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val s = assignment.replace(input) { "${it.groupValues[1]}=[redacted]" }
        return jsonField.replace(s) { "\"${it.groupValues[1]}\":\"[redacted]\"" }
    }

    fun clean(input: String?): String {
        val s = redact(input)
        if (s.isEmpty()) return ""
        return if (s.length > 500) s.take(500) + "…" else s
    }

    fun safeToStoreUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val lower = url.lowercase()
        if (!lower.startsWith("https://")) return null
        if (listOf("auth_token", "ct0", "password", "secret", "oauth_token").any { lower.contains(it) }) {
            return null
        }
        return url.take(500)
    }

    fun usableMediaUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        if (trimmed.startsWith("blob:", ignoreCase = true)) return null
        if (!trimmed.startsWith("https://")) return null
        return safeToStoreUrl(trimmed)
    }
}
