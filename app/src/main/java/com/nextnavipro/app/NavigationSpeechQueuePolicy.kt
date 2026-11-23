package com.nextnavipro.app

internal data class NavigationSpeechEvent(
    val key: String,
    val eventId: String,
    val text: String,
    val priority: Int,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val sequence: Long,
    val speechRate: Float
)

internal object NavigationSpeechQueuePolicy {
    private val order = compareByDescending<NavigationSpeechEvent> { it.priority }
        .thenBy { it.sequence }

    fun merge(
        pending: Collection<NavigationSpeechEvent>,
        incoming: NavigationSpeechEvent,
        nowMs: Long,
        maxPending: Int
    ): List<NavigationSpeechEvent> {
        val candidates = pending.filter { it.expiresAtMs > nowMs && it.key != incoming.key } + incoming
        return candidates.sortedWith(order).take(maxPending.coerceAtLeast(1))
    }

    fun takeNext(
        pending: Collection<NavigationSpeechEvent>,
        nowMs: Long
    ): Pair<NavigationSpeechEvent?, List<NavigationSpeechEvent>> {
        val fresh = pending.filter { it.expiresAtMs > nowMs }.sortedWith(order)
        return fresh.firstOrNull() to fresh.drop(1)
    }
}
