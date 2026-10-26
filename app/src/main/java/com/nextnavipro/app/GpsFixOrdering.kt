package com.nextnavipro.app

/**
 * Orders raw fixes before they enter navigation state.
 *
 * Prefer Android's monotonic elapsed-realtime timestamp. Fall back to wall-clock
 * time only when either fix lacks a monotonic timestamp (for old/provider-created fixes).
 */
internal object GpsFixOrdering {
    fun isOutOfOrder(
        previousElapsedRealtimeNanos: Long,
        incomingElapsedRealtimeNanos: Long,
        previousWallTimeMs: Long,
        incomingWallTimeMs: Long
    ): Boolean {
        if (previousElapsedRealtimeNanos > 0L && incomingElapsedRealtimeNanos > 0L) {
            return incomingElapsedRealtimeNanos <= previousElapsedRealtimeNanos
        }
        if (previousWallTimeMs > 0L && incomingWallTimeMs > 0L) {
            return incomingWallTimeMs <= previousWallTimeMs
        }
        return false
    }
}
