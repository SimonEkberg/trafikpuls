package com.simon.trafikpuls.core

import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Small in-memory LRU cache whose entries expire after [ttl]. Thread-safe. */
class TtlCache<K : Any, V : Any>(
    private val maxEntries: Int,
    private val ttl: Duration,
    private val clock: Clock,
) {
    private class Entry<V>(val value: V, val storedAt: Instant)

    private val entries = object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean =
            size > maxEntries
    }

    /** Returns the value and when it was stored, or null when missing or expired. */
    @Synchronized
    fun get(key: K): Pair<V, Instant>? {
        val entry = entries[key] ?: return null
        if (Duration.between(entry.storedAt, clock.instant()) > ttl) {
            entries.remove(key)
            return null
        }
        return entry.value to entry.storedAt
    }

    @Synchronized
    fun put(key: K, value: V) {
        entries[key] = Entry(value, clock.instant())
    }

    @Synchronized
    fun clear() = entries.clear()
}
