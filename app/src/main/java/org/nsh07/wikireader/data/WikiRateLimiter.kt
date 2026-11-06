package org.nsh07.wikireader.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-wide polite gate in front of every bulk Wikipedia request.
 *
 * The crawler used to limit only how many requests were in flight, which on a fast connection can
 * still mean dozens of requests per second: Wikipedia answers that with HTTP 429 and a
 * `Retry-After` header. This is a token bucket shared by every [TopicIndexBuilder] in the process,
 * plus a global cool-down that every worker respects after a 429/503.
 *
 * Builds default to about five requests per second with a burst of five. That is deliberately
 * below the rate that produced repeated 429 storms on large libraries while still finishing a
 * normal page-info batch in seconds. A 429/503 halves the rate and it only climbs back slowly
 * after a long run of successes. The interactive article/search path does not go through this
 * gate, so normal reading stays responsive.
 */
class WikiRateLimiter(
    private val maxRatePerSecond: Double = 5.0,
    private val burst: Int = 5
) {
    private val mutex = Mutex()
    private var tokens = burst.toDouble()
    private var lastRefillNanos = System.nanoTime()
    private val cooldownUntil = AtomicLong(0L)

    @Volatile
    private var ratePerSecond = maxRatePerSecond
    private val successesSinceIncrease = AtomicInteger(0)
    @Volatile
    private var lastRateCutMs = 0L

    /** Suspends until the caller may send one request. */
    suspend fun acquire() {
        while (true) {
            val waitMs = mutex.withLock {
                val now = System.currentTimeMillis()
                val cool = cooldownUntil.get() - now
                if (cool > 0L) return@withLock cool

                val nanos = System.nanoTime()
                val elapsed = (nanos - lastRefillNanos) / 1_000_000_000.0
                lastRefillNanos = nanos
                val rate = ratePerSecond
                tokens = minOf(burst.toDouble(), tokens + elapsed * rate)
                if (tokens >= 1.0) {
                    tokens -= 1.0
                    0L
                } else {
                    ((1.0 - tokens) / rate * 1_000.0).toLong().coerceAtLeast(1L)
                }
            }
            if (waitMs <= 0L) return
            delay(waitMs)
        }
    }

    /** Called after a successful bulk request; recovers the rate slowly after a throttle. */
    fun onSuccess() {
        if (ratePerSecond >= maxRatePerSecond) return
        if (successesSinceIncrease.incrementAndGet() < 40) return
        successesSinceIncrease.set(0)
        ratePerSecond = (ratePerSecond * 1.25).coerceAtMost(maxRatePerSecond)
    }

    /** Called after a 429/503. Nobody sends another bulk request before [retryAfterMs] passed. */
    suspend fun onThrottled(retryAfterMs: Long) {
        val until = System.currentTimeMillis() + retryAfterMs.coerceAtLeast(1_000L)
        while (true) {
            val current = cooldownUntil.get()
            if (until <= current || cooldownUntil.compareAndSet(current, until)) break
        }
        // A full bucket after a pause would immediately burst again; start empty instead. Halve
        // the rate once per throttle storm, not once per worker: several workers can see the same
        // 429 and would otherwise drive 8/s down to 1/s in a few milliseconds.
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastRateCutMs >= 10_000L) {
                ratePerSecond = (ratePerSecond / 2.0).coerceAtLeast(1.0)
                lastRateCutMs = now
            }
            successesSinceIncrease.set(0)
            tokens = 0.0
            lastRefillNanos = System.nanoTime()
        }
    }

    companion object {
        /** Shared by every builder: what Wikipedia told us about its limits stays valid. */
        val shared = WikiRateLimiter()
    }
}
