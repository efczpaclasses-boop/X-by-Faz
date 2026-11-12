package com.faz.xbyfaz.core.model

/**
 * In-memory video queue for one feed. Order is insertion order.
 * Duplicate video ids are ignored. Unavailable videos are removed and playback
 * continues at the item that shifts into the current slot.
 */
class PlaybackQueue {
    private val items = mutableListOf<Playable>()
    var index: Int = 0
        private set

    val size: Int get() = items.size

    fun snapshot(): List<Playable> = items.toList()

    fun current(): Playable? = items.getOrNull(index)

    fun peekNext(): Playable? = items.getOrNull(index + 1)

    fun positionLabel(): String = if (items.isEmpty()) "0 / 0" else "${index + 1} / ${items.size}"

    fun addAll(incoming: List<Playable>): Int {
        val seen = items.mapTo(HashSet()) { it.videoId }
        var added = 0
        for (item in incoming) {
            if (item.videoId.isBlank()) continue
            if (!seen.add(item.videoId)) continue
            items += item
            added++
        }
        return added
    }

    fun jumpTo(videoId: String): Playable? {
        val found = items.indexOfFirst { it.videoId == videoId }
        if (found < 0) return current()
        index = found
        return items[found]
    }

    /**
     * Move [delta] available steps. The walk is synchronous so a burst of
     * remote repeats cannot skip past the clamp.
     */
    fun move(delta: Int): Playable? {
        if (items.isEmpty() || delta == 0) return current()
        val dir = if (delta > 0) 1 else -1
        var steps = kotlin.math.abs(delta)
        var i = index
        while (steps > 0) {
            val next = i + dir
            if (next !in items.indices) break
            i = next
            steps--
        }
        index = i
        return items[i]
    }

    fun markUnavailable(videoId: String): Playable? {
        val idx = items.indexOfFirst { it.videoId == videoId }
        if (idx < 0) return current()
        items.removeAt(idx)
        if (items.isEmpty()) {
            index = 0
            return null
        }
        if (idx < index) index--
        if (index >= items.size) index = items.lastIndex
        return items[index]
    }

    fun needsMore(threshold: Int = 3): Boolean {
        if (items.isEmpty()) return true
        return index >= items.size - threshold
    }
}

class KeyRepeatGate(private val intervalMs: Long) {
    private var lastAccepted = Long.MIN_VALUE

    fun allow(nowMs: Long, repeatCount: Int): Boolean {
        if (repeatCount <= 0) {
            lastAccepted = nowMs
            return true
        }
        if (nowMs - lastAccepted >= intervalMs) {
            lastAccepted = nowMs
            return true
        }
        return false
    }
}
