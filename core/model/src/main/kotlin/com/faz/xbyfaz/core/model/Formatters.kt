package com.faz.xbyfaz.core.model

import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatCount(n: Long): String {
    val sign = if (n < 0) "-" else ""
    val v = kotlin.math.abs(n).toDouble()
    val (scaled, suffix) = when {
        v < 1_000 -> return sign + n.absoluteValueString()
        v < 1_000_000 -> v / 1_000 to "K"
        v < 1_000_000_000 -> v / 1_000_000 to "M"
        else -> v / 1_000_000_000 to "B"
    }
    return sign + trimOneDecimal(scaled) + suffix
}

private fun Long.absoluteValueString(): String = kotlin.math.abs(this).toString()

private fun trimOneDecimal(value: Double): String {
    val s = String.format(Locale.US, "%.1f", value)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatWhen(raw: String?, nowMs: Long = System.currentTimeMillis()): String {
    if (raw.isNullOrBlank()) return ""
    val parsed = parseTwitterDate(raw)
    if (parsed == null) return raw.take(24)
    val delta = (nowMs - parsed).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        minutes < 60 * 24 * 14 -> "${minutes / 60 / 24}d"
        else -> raw.take(16)
    }
}

fun parseTwitterDate(raw: String): Long? {
    return try {
        val fmt = java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US)
        fmt.isLenient = false
        fmt.parse(raw)?.time
    } catch (_: Exception) {
        null
    }
}

fun rememberRecent(existing: List<String>, value: String, max: Int = 8): List<String> {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return existing
    return (listOf(trimmed) + existing.filterNot { it.equals(trimmed, ignoreCase = true) }).take(max)
}

object HistoryRules {
    fun isCompleted(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        return positionMs >= (durationMs * 0.9).toLong()
    }

    fun resumePosition(positionMs: Long, durationMs: Long, resumeEnabled: Boolean): Long {
        if (!resumeEnabled) return 0L
        if (positionMs < 2_000L) return 0L
        if (isCompleted(positionMs, durationMs)) return 0L
        return positionMs
    }
}
