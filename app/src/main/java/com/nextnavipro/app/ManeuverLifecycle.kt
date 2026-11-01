package com.nextnavipro.app

/**
 * Deterministic lifecycle for one route maneuver.
 *
 * The presentation layer may use this state to schedule prompts, but it must not
 * infer road topology or advance a maneuver from a single noisy GNSS fix.
 * Call reset() whenever the active route identity changes.
 */
internal class ManeuverLifecycle(
    private val normalPrepareSeconds: Double = 35.0,
    private val roundaboutPrepareSeconds: Double = 48.0,
    private val normalImminentSeconds: Double = 10.0,
    private val roundaboutImminentSeconds: Double = 14.0,
    private val completionConfirmations: Int = 2,
    private val missedConfirmations: Int = 3
) {
    enum class State { APPROACH, PREPARE, IMMINENT, EXECUTING, COMPLETED, MISSED }

    data class Observation(
        val maneuverId: String,
        val distanceMeters: Double,
        val speedMetersPerSecond: Double,
        val matchedAlongRouteMeters: Double,
        val maneuverAlongRouteMeters: Double,
        val matchConfidence: Double,
        val fixElapsedRealtimeMs: Long,
        val isRoundabout: Boolean = false
    )

    data class Update(
        val maneuverId: String,
        val previous: State,
        val current: State,
        val changed: Boolean
    )

    private var activeId: String? = null
    private var state = State.APPROACH
    private var completionEvidence = 0
    private var missedEvidence = 0
    private var lastEvidenceTimestampMs = Long.MIN_VALUE

    fun reset() {
        activeId = null
        state = State.APPROACH
        completionEvidence = 0
        missedEvidence = 0
        lastEvidenceTimestampMs = Long.MIN_VALUE
    }

    fun currentState(): State = state

    fun update(observation: Observation): Update? {
        if (observation.maneuverId.isBlank() ||
            !observation.distanceMeters.isFinite() || observation.distanceMeters < 0.0 ||
            !observation.speedMetersPerSecond.isFinite() || observation.speedMetersPerSecond < 0.0 ||
            !observation.matchedAlongRouteMeters.isFinite() ||
            !observation.maneuverAlongRouteMeters.isFinite() ||
            !observation.matchConfidence.isFinite() || observation.matchConfidence !in 0.0..1.0 ||
            observation.fixElapsedRealtimeMs < 0L
        ) return null

        val previous = state
        if (activeId != observation.maneuverId) {
            activeId = observation.maneuverId
            state = State.APPROACH
            completionEvidence = 0
            missedEvidence = 0
            lastEvidenceTimestampMs = Long.MIN_VALUE
        }

        val trusted = observation.matchConfidence >= 0.55
        // Do not count the same Android Location twice if guidance and ETA both
        // evaluate it during one UI cycle. Also require accepted map-match confidence
        // before advancing any spoken maneuver stage.
        if (!trusted || observation.fixElapsedRealtimeMs <= lastEvidenceTimestampMs) {
            return Update(observation.maneuverId, previous, state, previous != state)
        }
        lastEvidenceTimestampMs = observation.fixElapsedRealtimeMs
        val speed = observation.speedMetersPerSecond.coerceAtLeast(2.0)
        val etaSeconds = observation.distanceMeters / speed
        val prepareAt = if (observation.isRoundabout) roundaboutPrepareSeconds else normalPrepareSeconds
        val imminentAt = if (observation.isRoundabout) roundaboutImminentSeconds else normalImminentSeconds
        val executingDistance = if (observation.isRoundabout) 22.0 else 12.0

        val passedBy = observation.matchedAlongRouteMeters -
            observation.maneuverAlongRouteMeters
        val wasExecuting = state == State.EXECUTING
        // A maneuver is completed only after the rider entered its execution phase.
        // Passing its route position without ever reaching that phase is a missed turn,
        // even when the UI's forward distance has clamped to zero.
        if (passedBy >= 28.0 && observation.distanceMeters <= 65.0 &&
            wasExecuting && missedEvidence == 0
        ) {
            completionEvidence++
        } else {
            completionEvidence = 0
        }
        if (passedBy >= 85.0 && (!wasExecuting || missedEvidence > 0) && state != State.COMPLETED) {
            missedEvidence++
        } else if (passedBy < 85.0) {
            missedEvidence = 0
        }

        state = when (state) {
            State.APPROACH -> when {
                observation.distanceMeters <= executingDistance || etaSeconds <= 3.5 -> State.EXECUTING
                etaSeconds <= imminentAt -> State.IMMINENT
                etaSeconds <= prepareAt -> State.PREPARE
                else -> State.APPROACH
            }
            State.PREPARE -> when {
                observation.distanceMeters <= executingDistance || etaSeconds <= 3.5 -> State.EXECUTING
                etaSeconds <= imminentAt -> State.IMMINENT
                else -> State.PREPARE
            }
            State.IMMINENT -> if (observation.distanceMeters <= executingDistance || etaSeconds <= 3.5)
                State.EXECUTING else State.IMMINENT
            State.EXECUTING -> when {
                completionEvidence >= completionConfirmations -> State.COMPLETED
                missedEvidence >= missedConfirmations -> State.MISSED
                else -> State.EXECUTING
            }
            State.COMPLETED -> State.COMPLETED
            State.MISSED -> State.MISSED
        }

        return Update(observation.maneuverId, previous, state, previous != state)
    }
}
