package com.nextnavipro.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicLong

/** Serializes navigation and safety speech; only explicit Jarvis interaction may interrupt. */
internal class NavigationSpeechCoordinator(private val tts: TextToSpeech) {
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
    private var activeStartedAtMs = 0L
    private var jarvisUtteranceId: String? = null
    private var pausedForJarvis = false
    private var generation = 0L
    private var closed = false

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                main.post {
                    if (utteranceId == activeId) {
                        activeStartedAtMs = SystemClock.elapsedRealtime()
                        Log.i(TAG, "tts_start id=$utteranceId at=$activeStartedAtMs")
                    }
                }
            }
            override fun onDone(utteranceId: String?) = complete(utteranceId, "done")
            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) = complete(utteranceId, "error")
            override fun onError(utteranceId: String?, errorCode: Int) = complete(utteranceId, "error_$errorCode")
            override fun onStop(utteranceId: String?, interrupted: Boolean) =
                complete(utteranceId, if (interrupted) "interrupted" else "stopped")
        })
    }

    fun enqueue(key: String, text: String, priority: Int = PRIORITY_GUIDANCE, maxAgeMs: Long = DEFAULT_MAX_AGE_MS) = onMain {
        if (closed || text.isBlank()) return@onMain
        val now = SystemClock.elapsedRealtime()
        discardExpired(now)
        // A later stage for the same maneuver may queue behind the current complete phrase.
        pending.removeAll { it.key == key }
        pending.add(Item(key, text, priority, now + maxAgeMs.coerceAtLeast(1L), sequence.incrementAndGet()))
        while (pending.size > maxPending) {
            val lowest = pending.minWithOrNull(compareBy<Item> { it.priority }.thenByDescending { it.sequence }) ?: break
            pending.remove(lowest)
        }
        Log.i(TAG, "tts_enqueue key=$key priority=$priority pending=${pending.size} at=$now")
        pump()
    }

    /** Pause navigation while the rider is listening to Jarvis. */
    fun interruptForJarvis() = onMain {
        generation++
        pausedForJarvis = true
        jarvisUtteranceId = null
        pending.clear()
        activeId = null
        activeStartedAtMs = 0L
        try { tts.stop() } catch (_: Exception) { }
        Log.i(TAG, "tts_paused_for_jarvis at=${SystemClock.elapsedRealtime()}")
    }

    /** Register a Jarvis reply so navigation resumes only after that reply completes. */
    fun beginJarvisSpeech(utteranceId: String) = onMain {
        generation++
        pausedForJarvis = true
        pending.clear()
        activeId = null
        activeStartedAtMs = 0L
        jarvisUtteranceId = utteranceId
        try { tts.stop() } catch (_: Exception) { }
    }

    fun resumeAfterJarvis() = onMain {
        jarvisUtteranceId = null
        pausedForJarvis = false
        pump()
    }

    fun discardPendingByPrefix(prefix: String) = onMain {
        val before = pending.size
        pending.removeAll { it.key.startsWith(prefix) }
        if (before != pending.size) Log.i(TAG, "tts_discard_prefix prefix=$prefix count=${before - pending.size}")
    }

    fun close() = onMain {
        closed = true
        generation++
        pending.clear()
        activeId = null
        activeStartedAtMs = 0L
    }

    private fun complete(utteranceId: String?, outcome: String) {
        main.post {
            if (utteranceId == null) return@post
            if (utteranceId == jarvisUtteranceId) {
                Log.i(TAG, "jarvis_tts_end id=$utteranceId outcome=$outcome at=${SystemClock.elapsedRealtime()}")
                jarvisUtteranceId = null
                pausedForJarvis = false
                pump()
                return@post
            }
            if (utteranceId != activeId) return@post
            val now = SystemClock.elapsedRealtime()
            val started = activeStartedAtMs
            Log.i(TAG, "tts_end id=$utteranceId outcome=$outcome at=$now duration_ms=${if (started > 0L) now - started else -1L}")
            activeId = null
            activeStartedAtMs = 0L
            pump()
        }
    }

    private fun pump() {
        if (closed || pausedForJarvis || activeId != null) return
        val now = SystemClock.elapsedRealtime()
        discardExpired(now)
        val next = pending.poll() ?: return
        val token = generation
        val id = "${next.key}#${next.sequence}#$token"
        activeId = id
        activeStartedAtMs = 0L
        val result = try { tts.speak(next.text, TextToSpeech.QUEUE_ADD, null, id) }
        catch (_: Exception) { TextToSpeech.ERROR }
        if (result == TextToSpeech.ERROR && activeId == id) {
            Log.w(TAG, "tts_speak_error id=$id at=${SystemClock.elapsedRealtime()}")
            activeId = null
            activeStartedAtMs = 0L
            main.post { if (generation == token) pump() }
        }
    }

    private fun discardExpired(now: Long) {
        val before = pending.size
        pending.removeAll { it.expiresAtMs <= now }
        if (before != pending.size) Log.i(TAG, "tts_expired count=${before - pending.size} at=$now")
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    companion object {
        private const val TAG = "NEXT_NAVI_SPEECH"
        const val PRIORITY_GUIDANCE = 50
        const val PRIORITY_REROUTE = 35
        const val PRIORITY_RADAR = 80
        const val PRIORITY_OVERSPEED = 100
        const val PRIORITY_ARRIVAL = 90
        const val PRIORITY_START = 25
        const val DEFAULT_MAX_AGE_MS = 15_000L
    }
}
