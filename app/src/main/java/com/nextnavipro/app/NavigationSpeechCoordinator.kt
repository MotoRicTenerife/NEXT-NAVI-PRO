package com.nextnavipro.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/**
 * Serializes navigation and safety speech. A phrase already playing is allowed to finish;
 * pending events are coalesced by key, prioritized and expired before they can become stale.
 * Jarvis is the only intentional interruption path.
 */
internal class NavigationSpeechCoordinator(private val tts: TextToSpeech) {
    private val main = Handler(Looper.getMainLooper())
    private val sequence = AtomicLong()
    private val pending = mutableListOf<NavigationSpeechEvent>()
    private val maxPending = 5
    private var activeId: String? = null
    private var activeEvent: NavigationSpeechEvent? = null
    private var activeStartedAtMs = 0L
    private var generation = 0L
    private var pausedForJarvis = false
    private var closed = false

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                main.post {
                    if (utteranceId != activeId) return@post
                    val now = SystemClock.elapsedRealtime()
                    activeStartedAtMs = now
                    val event = activeEvent ?: return@post
                    Log.i(TAG, "event=tts_start id=${event.eventId} key=${event.key} " +
                        "queue_wait_ms=${(now - event.createdAtMs).coerceAtLeast(0L)} elapsed_ms=$now")
                }
            }

            override fun onDone(utteranceId: String?) {
                complete(utteranceId, "done")
            }

            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) {
                complete(utteranceId, "error")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                complete(utteranceId, "error_$errorCode")
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                complete(utteranceId, if (interrupted) "stopped" else "stop")
            }
        })
    }

    fun enqueue(
        key: String,
        eventId: String,
        text: String,
        priority: Int = PRIORITY_GUIDANCE,
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
        speechRate: Float = 0.96f
    ) = onMain {
        if (closed || text.isBlank()) return@onMain
        val now = SystemClock.elapsedRealtime()
        val event = NavigationSpeechEvent(
            key = key,
            eventId = eventId,
            text = text,
            priority = priority,
            createdAtMs = now,
            expiresAtMs = now + maxAgeMs.coerceAtLeast(1L),
            sequence = sequence.incrementAndGet(),
            speechRate = speechRate.coerceIn(0.70f, 1.20f)
        )
        val merged = NavigationSpeechQueuePolicy.merge(pending, event, now, maxPending)
        val accepted = merged.any { it.sequence == event.sequence }
        val dropped = pending.filter { old -> merged.none { it.sequence == old.sequence } }
        pending.clear()
        pending.addAll(merged)
        dropped.forEach { Log.w(TAG, "event=queue_drop id=${it.eventId} reason=replaced_or_capacity") }
        if (!accepted) {
            Log.w(TAG, "event=queue_drop id=$eventId reason=capacity priority=$priority")
            return@onMain
        }
        Log.i(TAG, "event=enqueue id=$eventId key=$key priority=$priority " +
            "queue_size=${pending.size} max_age_ms=$maxAgeMs elapsed_ms=$now")
        pump()
    }

    /** Pause navigation speech while the microphone is actively listening. */
    fun pauseForJarvis() = onMain {
        pausedForJarvis = true
        generation++
        Log.i(TAG, "event=jarvis_listening_start pending=${pending.size} elapsed_ms=${SystemClock.elapsedRealtime()}")
        pending.clear()
        activeId = null
        activeEvent = null
        activeStartedAtMs = 0L
        try { tts.stop() } catch (_: Exception) { }
    }

    /** Resume queued navigation after listening or after a Jarvis reply has been submitted. */
    fun resumeAfterJarvis() = onMain {
        if (pausedForJarvis) {
            pausedForJarvis = false
            Log.i(TAG, "event=jarvis_listening_end elapsed_ms=${SystemClock.elapsedRealtime()}")
        }
        pump()
    }

    /** Jarvis voice output is an intentional user-driven interruption. */
    fun interruptForJarvis() = onMain {
        generation++
        Log.i(TAG, "event=jarvis_interrupt pending=${pending.size} elapsed_ms=${SystemClock.elapsedRealtime()}")
        pending.clear()
        activeId = null
        activeEvent = null
        activeStartedAtMs = 0L
        try { tts.stop() } catch (_: Exception) { }
    }

    fun discardPendingByPrefix(prefix: String) = onMain {
        val before = pending.size
        pending.removeAll { it.key.startsWith(prefix) }
        val removed = before - pending.size
        if (removed > 0) Log.i(TAG, "event=queue_discard prefix=$prefix count=$removed")
    }

    fun clear() = onMain {
        generation++
        pending.clear()
        activeId = null
        activeEvent = null
        activeStartedAtMs = 0L
        try { tts.stop() } catch (_: Exception) { }
    }

    fun close() = onMain {
        closed = true
        pausedForJarvis = true
        pending.clear()
        activeId = null
        activeEvent = null
        activeStartedAtMs = 0L
        try { tts.setOnUtteranceProgressListener(null) } catch (_: Exception) { }
    }

    private fun complete(utteranceId: String?, outcome: String) {
        main.post {
            if (utteranceId == null || utteranceId != activeId) return@post
            val now = SystemClock.elapsedRealtime()
            val event = activeEvent
            val playbackMs = if (activeStartedAtMs > 0L) (now - activeStartedAtMs).coerceAtLeast(0L) else 0L
            if (event != null) {
                Log.i(TAG, "event=tts_$outcome id=${event.eventId} key=${event.key} " +
                    "playback_ms=$playbackMs total_ms=${(now - event.createdAtMs).coerceAtLeast(0L)} elapsed_ms=$now")
            }
            activeId = null
            activeEvent = null
            activeStartedAtMs = 0L
            pump()
        }
    }

    private fun pump() {
        if (closed || pausedForJarvis || activeId != null) return
        val now = SystemClock.elapsedRealtime()
        val (next, remaining) = NavigationSpeechQueuePolicy.takeNext(pending, now)
        pending.clear()
        pending.addAll(remaining)
        if (next == null) return

        val token = generation
        val utteranceId = "${next.eventId}#${next.sequence}#$token"
        activeId = utteranceId
        activeEvent = next
        activeStartedAtMs = 0L
        try {
            tts.setSpeechRate(next.speechRate)
            val result = tts.speak(next.text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            if (result == TextToSpeech.ERROR && activeId == utteranceId) {
                Log.e(TAG, "event=tts_enqueue_error id=${next.eventId} elapsed_ms=${SystemClock.elapsedRealtime()}")
                activeId = null
                activeEvent = null
                main.post { if (generation == token) pump() }
            }
        } catch (error: Exception) {
            Log.e(TAG, "event=tts_exception id=${next.eventId}", error)
            if (activeId == utteranceId) {
                activeId = null
                activeEvent = null
                main.post { if (generation == token) pump() }
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }

    companion object {
        private const val TAG = "NEXT_NAVI_TTS"
        const val PRIORITY_GUIDANCE = 60
        const val PRIORITY_REROUTE = 70
        const val PRIORITY_START = 20
        const val PRIORITY_RADAR = 80
        const val PRIORITY_ARRIVAL = 90
        const val PRIORITY_OVERSPEED = 100
        const val DEFAULT_MAX_AGE_MS = 12_000L
    }
}
