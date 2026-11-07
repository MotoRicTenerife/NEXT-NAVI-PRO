package com.nextnavipro.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Serializes navigation speech. Ordinary navigation events never flush an utterance already
 * being spoken; pending events are deduplicated, prioritized and expire instead of becoming stale.
 *
 * All public methods are marshalled onto the main thread because Android TextToSpeech is owned
 * by the Activity lifecycle. Jarvis is the only intentional user-driven interruption.
 */
internal class NavigationSpeechCoordinator(
    private val tts: TextToSpeech,
    private val onSpeechStateChanged: ((Boolean) -> Unit)? = null
) {
    private data class Item(
        val key: String,
        val text: String,
        val priority: Int,
        val expiresAtMs: Long,
        val sequence: Long
    )

    private val main = Handler(Looper.getMainLooper())
    private val sequence = AtomicLong()
    private val pending = PriorityQueue<Item>(
        compareByDescending<Item> { it.priority }.thenBy { it.sequence }
    )
    private val maxPending = 5
    private var activeId: String? = null
    private var generation = 0L
    private var closed = false

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                main.post { if (utteranceId == activeId) onSpeechStateChanged?.invoke(true) }
            }

            override fun onDone(utteranceId: String?) = complete(utteranceId)
            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) = complete(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = complete(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = complete(utteranceId)
        })
    }

    fun enqueue(
        key: String,
        text: String,
        priority: Int = PRIORITY_GUIDANCE,
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS
    ) = onMain {
        if (closed || text.isBlank()) return@onMain
        val now = SystemClock.elapsedRealtime()
        discardExpired(now)

        // A newer stage for the currently speaking maneuver must not interrupt it or repeat it.
        if (activeId?.startsWith("$key#") == true) return@onMain

        // Replace older pending information with the newest information for the same event.
        pending.removeAll { it.key == key }
        pending.add(Item(key, text, priority, now + maxAgeMs.coerceAtLeast(1L), sequence.incrementAndGet()))
        while (pending.size > maxPending) {
            val lowest = pending.minWithOrNull(compareBy<Item> { it.priority }.thenByDescending { it.sequence })
                ?: break
            pending.remove(lowest)
        }
        pump()
    }

    /** Called only when the rider explicitly opens Jarvis and requests the microphone. */
    fun interruptForJarvis() = onMain {
        generation++
        pending.clear()
        activeId = null
        onSpeechStateChanged?.invoke(false)
        try { tts.stop() } catch (_: Exception) { }
    }

    fun clear() = onMain {
        generation++
        pending.clear()
        activeId = null
        onSpeechStateChanged?.invoke(false)
        try { tts.stop() } catch (_: Exception) { }
    }

    fun close() = onMain {
        closed = true
        generation++
        pending.clear()
        activeId = null
        onSpeechStateChanged?.invoke(false)
    }

    private fun complete(utteranceId: String?) {
        main.post {
            if (utteranceId == null || utteranceId != activeId) return@post
            activeId = null
            onSpeechStateChanged?.invoke(false)
            pump()
        }
    }

    private fun pump() {
        if (closed || activeId != null) return
        discardExpired(SystemClock.elapsedRealtime())
        val next = pending.poll() ?: return
        val token = generation
        val id = "${next.key}#${next.sequence}#${token}"
        activeId = id
        onSpeechStateChanged?.invoke(true)
        val result = try {
            tts.speak(next.text, TextToSpeech.QUEUE_ADD, null, id)
        } catch (_: Exception) {
            TextToSpeech.ERROR
        }
        if (result == TextToSpeech.ERROR && activeId == id) {
            activeId = null
            onSpeechStateChanged?.invoke(false)
            main.post { if (generation == token) pump() }
        }
    }

    private fun discardExpired(now: Long) {
        pending.removeAll { it.expiresAtMs <= now }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    companion object {
        const val PRIORITY_GUIDANCE = 50
        const val PRIORITY_REROUTE = 35
        const val PRIORITY_RADAR = 80
        const val PRIORITY_OVERSPEED = 100
        const val PRIORITY_ARRIVAL = 90
        const val PRIORITY_START = 25
        const val DEFAULT_MAX_AGE_MS = 15_000L
    }
}
